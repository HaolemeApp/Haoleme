import unittest
import tempfile
import io
import json
import os
import signal
import subprocess
import sys
import threading
import time
from contextlib import redirect_stdout
from unittest.mock import patch
from datetime import datetime
from pathlib import Path

from haoleme.cli import (
    HEARTBEAT_INTERVAL_SECONDS,
    ORPHANED_RUN_GRACE_SECONDS,
    RemoteActionWatcher,
    acquire_process_file_lock,
    apply_cloud_controls,
    apply_remote_actions,
    apply_scheduled_shutdowns,
    build_pair_url,
    command_needs_shell,
    compare_versions,
    collect_cpu_stats,
    collect_heartbeat_metrics,
    collect_gpu_stats,
    collect_memory_stats,
    _parse_darwin_vm_stat,
    _parse_linux_meminfo,
    _parse_nvidia_compute_apps,
    _parse_posix_ps,
    check_cli_update,
    doctor_command,
    heartbeat_initial_delay,
    heartbeat_state_path,
    is_heartbeat_process_running,
    is_process_running,
    main,
    mark_stale_active_runs_pending,
    latest_python_release,
    launch_remote_rerun,
    login_server_label,
    normalize_relay_login_url,
    pairing_login_command,
    prompt_login_relay_url,
    print_update_notice_after_command,
    python_wheel_candidates,
    update_command,
    version_command,
    qr_matrix_to_terminal_lines,
    read_heartbeat_state,
    restart_heartbeat_daemon,
    reconcile_orphaned_running_runs,
    remote_terminal_shell_command,
    reusable_login_device_id,
    run_command,
    run_command_with_pipes,
    subprocess_session_kwargs,
    terminate_process_on_interrupt,
    split_leading_env_assignments,
    start_local_relay_for_login,
    stream_output,
    sync_pending_runs,
    terminate_windows_process,
    uses_private_relay,
    write_update_check_cache,
    write_heartbeat_state,
    _parse_windows_gpu_payload,
    _windows_memory_stats,
)
from haoleme.cloud import CloudConfig, DEFAULT_CLOUD_URL, InterruptWatcher
from haoleme.crypto import encrypt_action_payload, generate_account_key
from haoleme.store import RunStore


class DummyCloudClient:
    def __init__(self):
        self.synced = []

    def upsert_run(self, run, *, include_output=True):
        self.synced.append(run)


class DummySyncer:
    client = None

    def request_sync(self):
        pass


class BrokenTarget:
    def write(self, _value):
        raise BrokenPipeError("closed")

    def flush(self):
        raise BrokenPipeError("closed")


class CliPairingTest(unittest.TestCase):
    def test_remote_action_watcher_starts_for_official_cloud(self):
        config = CloudConfig(DEFAULT_CLOUD_URL, "default", "token", device_id="dev-cloud")
        watcher = RemoteActionWatcher()
        thread = unittest.mock.MagicMock()
        with patch("haoleme.cli.CloudConfig.load", return_value=config), \
                patch("haoleme.cli.threading.Thread", return_value=thread) as thread_factory:
            watcher.start()

        thread_factory.assert_called_once_with(target=watcher._loop, name="haoleme-actions", daemon=True)
        thread.start.assert_called_once_with()

    def test_instant_action_channel_is_limited_to_private_relays(self):
        private = CloudConfig("http://192.168.1.20:8000", "default", "x" * 32)
        official = CloudConfig(DEFAULT_CLOUD_URL, "default", "x" * 32)

        self.assertTrue(uses_private_relay(private))
        self.assertFalse(uses_private_relay(official))

    def test_stop_is_cancel_command_alias(self):
        with patch("haoleme.cli.cancel_command", return_value=0) as cancel:
            self.assertEqual(main(["stop", "run-123"]), 0)
        cancel.assert_called_once_with(["run-123"])

    def test_remote_rerun_launch_is_idempotent_and_keeps_context(self):
        class ActionClient:
            def __init__(self):
                self.completed = []

            def complete_remote_action(self, action_id, status, detail="", launcher_pid=None):
                self.completed.append((action_id, status, detail, launcher_pid))
                return {"ok": True}

        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run(
                "run-original",
                ["MODE=1", sys.executable, "-c", "print('ok')"],
                tmp,
                project="Experiment",
            )
            store.finish_run("run-original", 0)
            client = ActionClient()
            action = {"id": "action-1", "runId": "run-original", "action": "rerun"}

            with patch("haoleme.cli.launch_remote_rerun", return_value=4321) as launcher:
                first = apply_remote_actions(store, client, [action])
                second = apply_remote_actions(store, client, [action])

            launcher.assert_called_once()
            self.assertIn("Remote rerun started", first[0])
            self.assertEqual(second, [])
            self.assertEqual(client.completed[-1][1], "started")
            self.assertEqual(client.completed[-1][3], 4321)

    def test_active_control_poll_applies_stop_and_rerun_together(self):
        class ControlClient:
            def __init__(self):
                self.completed = []
                self.synced = []

            def list_pending_controls(self):
                return {
                    "actions": [{"id": "action-rerun", "runId": "run-source", "action": "rerun"}],
                    "interrupts": [{"id": "run-active", "interruptRequestedAt": "2026-08-12T00:00:00Z"}],
                }

            def complete_remote_action(self, action_id, status, detail="", launcher_pid=None):
                self.completed.append((action_id, status, launcher_pid))
                return {"ok": True}

            def upsert_run(self, run, *, include_output=True):
                self.synced.append(run.id)
                return {"ok": True}

        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-source", ["echo", "again"], tmp)
            store.finish_run("run-source", 0)
            store.create_run("run-active", ["sleep", "30"], tmp)
            store.mark_running("run-active", None)
            client = ControlClient()

            with patch("haoleme.cli.launch_remote_rerun", return_value=5432) as launcher:
                messages, interrupted = apply_cloud_controls(store, client)

            launcher.assert_called_once()
            self.assertEqual(interrupted, 1)
            self.assertIn("Remote rerun started", messages[0])
            self.assertEqual(store.get_run("run-active").status, "failed")
            self.assertEqual(client.synced, ["run-active"])
            self.assertEqual(client.completed, [("action-rerun", "started", 5432)])

    def test_active_control_poll_keeps_stop_working_with_old_server(self):
        class LegacyClient:
            def list_pending_controls(self):
                raise RuntimeError("HTTP 403: permission denied; re-pair this device")

            def list_pending_interrupts(self):
                return [{"id": "run-active", "interruptRequestedAt": "2026-08-12T00:00:00Z"}]

            def upsert_run(self, run, *, include_output=True):
                return {"ok": True}

        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-active", ["sleep", "30"], tmp)
            store.mark_running("run-active", None)

            messages, interrupted = apply_cloud_controls(store, LegacyClient())

            self.assertEqual(messages, [])
            self.assertEqual(interrupted, 1)
            self.assertEqual(store.get_run("run-active").status, "failed")

    def test_launch_remote_rerun_uses_saved_command_directory_and_project(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-launch", ["FOO=1", "python", "app.py"], tmp, project="Project A")
            store.finish_run("run-launch", 0)
            process = type("Process", (), {"pid": 9876})()

            target_run_id = "fd55b90a-9e47-4295-b09e-4f5409b07d6f"
            with patch("haoleme.cli.subprocess.Popen", return_value=process) as popen:
                pid = launch_remote_rerun(store.get_run("run-launch"), target_run_id)

            self.assertEqual(pid, 9876)
            command = popen.call_args.args[0]
            self.assertEqual(command[:5], [sys.executable, "-m", "haoleme", "--project", "Project A"])
            self.assertEqual(command[5:], ["FOO=1", "python", "app.py"])
            self.assertEqual(popen.call_args.kwargs["cwd"], tmp)
            self.assertEqual(popen.call_args.kwargs["env"]["HAOLEME_REMOTE_RUN_ID"], target_run_id)

    def test_terminal_action_is_decrypted_and_launched_as_new_run(self):
        class ActionClient:
            def __init__(self, key):
                self.config = CloudConfig("https://relay.test", "default", "token", encryption_key=key)
                self.completed = []

            def complete_remote_action(self, action_id, status, detail="", launcher_pid=None):
                self.completed.append((action_id, status, launcher_pid))
                return {"ok": True}

        with tempfile.TemporaryDirectory() as tmp:
            key = generate_account_key()
            store = RunStore(Path(tmp) / "runs.db")
            client = ActionClient(key)
            action_id = "action-terminal"
            action = {
                "id": action_id,
                "runId": "",
                "targetRunId": "7bd4a3b6-5e70-4679-8887-b8e20d6d10c0",
                "action": "terminal",
                "payload": encrypt_action_payload(
                    action_id,
                    {"command": "echo terminal-ok", "cwd": tmp, "project": "Remote"},
                    key,
                ),
            }

            with patch.dict(os.environ, {"SHELL": "/bin/sh"}, clear=False):
                with patch("haoleme.cli.launch_remote_command", return_value=7654) as launcher:
                    messages = apply_remote_actions(store, client, [action])

            launcher.assert_called_once_with(
                ["/bin/sh", "-lc", "echo terminal-ok"],
                tmp,
                "Remote",
                action["targetRunId"],
            )
            self.assertIn("Remote terminal run started", messages[0])
            self.assertEqual(client.completed[0][1], "started")

    def test_remote_terminal_shell_uses_login_shell(self):
        with patch.dict(os.environ, {"SHELL": "/bin/sh"}, clear=False):
            self.assertEqual(
                remote_terminal_shell_command("pwd && echo ok"),
                ["/bin/sh", "-lc", "pwd && echo ok"],
            )

    def test_scheduled_shutdown_waits_for_run_and_executes_once(self):
        class ActionClient:
            def __init__(self):
                self.completed = []

            def complete_remote_action(self, action_id, status, detail="", launcher_pid=None):
                self.completed.append((action_id, status))
                return {"ok": True}

        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-active", ["sleep", "1"], tmp)
            store.reserve_remote_action("action-shutdown", "run-active", "shutdown_after_run")
            store.finish_remote_action("action-shutdown", "scheduled")
            client = ActionClient()

            with patch("haoleme.cli.request_system_shutdown") as shutdown:
                self.assertEqual(apply_scheduled_shutdowns(store, client), [])
                shutdown.assert_not_called()
                store.finish_run("run-active", 0)
                self.assertEqual(apply_scheduled_shutdowns(store, client), [])
                shutdown.assert_called_once()
                apply_scheduled_shutdowns(store, client)
                shutdown.assert_called_once()

            self.assertEqual(client.completed, [("action-shutdown", "completed")])

    def test_remote_run_id_creates_a_distinct_run_record(self):
        target_run_id = "ef626c63-e95b-4e6b-89d9-2152d5c40bf2"
        with tempfile.TemporaryDirectory() as tmp, \
                patch.dict(os.environ, {"HAOLEME_HOME": tmp, "HAOLEME_REMOTE_RUN_ID": target_run_id}, clear=False), \
                patch("haoleme.cli.start_background_update_check", return_value={}), \
                patch("haoleme.cli.start_heartbeat_daemon", return_value=(True, "started")), \
                patch("haoleme.cli.configured_cloud_client", return_value=None), \
                patch("haoleme.cli.should_use_pty", return_value=False), \
                patch("haoleme.cli.run_command_with_pipes", return_value=(0, False)), \
                patch("haoleme.cli.print_update_notice_after_command"):
            self.assertEqual(run_command(["echo", "new-run"]), 0)

            run = RunStore().get_run(target_run_id)
            self.assertIsNotNone(run)
            self.assertEqual(run.command, ["echo", "new-run"])
            self.assertEqual(run.status, "succeeded")
            self.assertNotIn("HAOLEME_REMOTE_RUN_ID", os.environ)

    def test_compare_versions_orders_semver_like_values(self):
        self.assertEqual(compare_versions("0.3.9", "0.3.10"), -1)
        self.assertEqual(compare_versions("0.3.19", "0.3.19"), 0)
        self.assertEqual(compare_versions("1.0.0", "0.9.9"), 1)

    def test_version_command_prints_current_version(self):
        buffer = io.StringIO()
        with patch("sys.stdout", buffer), patch(
            "haoleme.cli.check_cli_update",
            return_value={"latestVersion": "9.9.9", "source": "http://example.test/downloads/update.json"},
        ):
            exit_code = version_command([])
        output = buffer.getvalue()
        self.assertIn("haoleme", output)
        self.assertEqual(exit_code, 0)

    def test_version_check_exits_when_update_available(self):
        with patch(
            "haoleme.cli.check_cli_update",
            return_value={"latestVersion": "9.9.9", "source": "test"},
        ), patch("haoleme.cli.__version__", "0.0.1"):
            self.assertEqual(version_command(["--check"]), 1)

    def test_cached_update_check_does_not_use_network(self):
        with tempfile.TemporaryDirectory() as tmp:
            cache_path = Path(tmp) / "update-check.json"
            with patch("haoleme.cli.update_check_cache_path", return_value=cache_path):
                write_update_check_cache({"checkedAt": 1000.0, "latestVersion": "9.9.9"})
                with patch("haoleme.cli.fetch_update_manifest") as fetch:
                    status = check_cli_update(now=1001.0)

            self.assertEqual(status["latestVersion"], "9.9.9")
            fetch.assert_not_called()

    def test_command_update_notice_is_rate_limited(self):
        with tempfile.TemporaryDirectory() as tmp:
            cache_path = Path(tmp) / "update-check.json"
            status = {"checkedAt": time.time(), "latestVersion": "9.9.9"}
            output = io.StringIO()
            with patch("haoleme.cli.update_check_cache_path", return_value=cache_path), \
                    patch("haoleme.cli.__version__", "0.0.1"), \
                    patch("sys.stdout", output):
                self.assertTrue(print_update_notice_after_command({"status": status}))
                cached = json.loads(cache_path.read_text(encoding="utf-8"))
                self.assertFalse(print_update_notice_after_command({"status": cached}))

            self.assertEqual(output.getvalue().count("Run: hao update"), 1)

    def test_short_command_waits_for_background_update_result(self):
        with tempfile.TemporaryDirectory() as tmp:
            cache_path = Path(tmp) / "update-check.json"
            state = {"status": {}}

            def finish_check():
                time.sleep(0.05)
                state["status"] = {"checkedAt": time.time(), "latestVersion": "9.9.9"}

            thread = threading.Thread(target=finish_check, daemon=True)
            state["thread"] = thread
            thread.start()
            output = io.StringIO()
            with patch("haoleme.cli.update_check_cache_path", return_value=cache_path), \
                    patch("haoleme.cli.__version__", "0.0.1"), \
                    patch("sys.stdout", output):
                shown = print_update_notice_after_command(state)

            self.assertTrue(shown)
            self.assertIn("Run: hao update", output.getvalue())

    def test_doctor_reports_available_cli_update(self):
        output = io.StringIO()
        with tempfile.TemporaryDirectory() as tmp, \
                patch.dict(os.environ, {"HAOLEME_HOME": tmp}), \
                patch("haoleme.cli.check_cli_update", return_value={"latestVersion": "9.9.9"}), \
                patch("haoleme.cli.CloudConfig.load", return_value=None), \
                patch("sys.stdout", output):
            doctor_command(["--no-network"])

        self.assertIn("cli update", output.getvalue())
        self.assertIn("run: hao update", output.getvalue())

    def test_doctor_does_not_upload_pending_runs(self):
        class HealthyClient:
            def __init__(self, *_args, **_kwargs):
                pass

            def health(self):
                return {"storage": {"ok": True}, "disk": {"ok": True, "freeBytes": 1024 * 1024}}

            def list_devices(self):
                return []

        output = io.StringIO()
        config = CloudConfig(api_url="https://example.test", account="default", token="x" * 32)
        with tempfile.TemporaryDirectory() as tmp, \
                patch.dict(os.environ, {"HAOLEME_HOME": tmp}), \
                patch("haoleme.cli.CloudConfig.load", return_value=config), \
                patch("haoleme.cli.CloudClient", HealthyClient), \
                patch("haoleme.cli.check_cli_update", return_value={"latestVersion": "0.4.24"}), \
                patch("haoleme.cli.sync_pending_runs", side_effect=AssertionError("doctor must not upload")), \
                patch("sys.stdout", output):
            exit_code = doctor_command([])

        self.assertEqual(exit_code, 0)
        self.assertEqual(output.getvalue().count("cloud health"), 1)

    def test_update_check_reports_available_release(self):
        buffer = io.StringIO()
        with patch("sys.stdout", buffer), patch("haoleme.cli.fetch_update_manifest") as fetch, patch("haoleme.cli.__version__", "0.0.1"):
            fetch.return_value = (
                {"python": {"version": "9.9.9", "packageUrl": "https://pypi.org/project/haoleme/"}},
                "http://example.test/downloads/update.json",
            )
            exit_code = update_command(["--check"])
        self.assertEqual(exit_code, 0)
        self.assertIn("Update available", buffer.getvalue())

    def test_newer_pypi_release_drops_stale_manifest_wheel(self):
        manifest = {
            "python": {
                "version": "0.4.22",
                "wheelUrl": "https://api.example/downloads/haoleme-0.4.22-py3-none-any.whl",
            }
        }
        with patch("haoleme.cli._fetch_pypi_latest_version", return_value="0.4.24"):
            release = latest_python_release(manifest)

        self.assertEqual(release["version"], "0.4.24")
        self.assertEqual(release["wheelUrl"], "")
        self.assertEqual(release["packageUrl"], "haoleme")
        self.assertEqual(
            python_wheel_candidates(release, "https://api.example/downloads/update.json")[0],
            "haoleme",
        )

    def test_main_routes_version_flag(self):
        with patch("haoleme.cli.version_command", return_value=0) as mocked:
            exit_code = main(["--version", "--check"])
        self.assertEqual(exit_code, 0)
        mocked.assert_called_once_with(["--check"])

    def test_main_treats_unknown_first_arg_as_command(self):
        with patch("haoleme.cli.run_command", return_value=0) as mocked:
            exit_code = main(["python", "train.py"])

        self.assertEqual(exit_code, 0)
        mocked.assert_called_once_with(["python", "train.py"], project_override=None)

    def test_main_supports_project_option_without_run_subcommand(self):
        with patch("haoleme.cli.run_command", return_value=0) as mocked:
            exit_code = main(["--project", "demo", "python", "train.py"])

        self.assertEqual(exit_code, 0)
        mocked.assert_called_once_with(["python", "train.py"], project_override="demo")

    def test_pairing_login_uses_default_cloud_url(self):
        with patch("haoleme.cli.PairingClient") as client_cls, \
                patch("haoleme.cli.CloudConfig.load", return_value=None), \
                patch("haoleme.cli.get_or_create_machine_id", return_value="machine_test"), \
                patch("haoleme.cli.generate_pair_keypair", return_value=("public", "private")):
            client_cls.return_value.start.side_effect = RuntimeError("stop")

            exit_code = pairing_login_command([])

        self.assertEqual(exit_code, 1)
        client_cls.assert_called_once_with(DEFAULT_CLOUD_URL)

    def test_existing_login_goes_directly_to_server_selection(self):
        existing = CloudConfig(
            api_url=DEFAULT_CLOUD_URL,
            account="default",
            token="x" * 32,
            device_id="dev_mac",
            device_name="Mac",
            machine_id="machine_mac",
        )
        relay = "https://relay.example.com"
        with patch("haoleme.cli.PairingClient") as client_cls, patch(
            "haoleme.cli.CloudConfig.load", return_value=existing
        ), patch("haoleme.cli.stdin_is_interactive", return_value=True), patch(
            "haoleme.cli.prompt_login_relay_url", return_value=relay
        ) as choose_server, patch(
            "haoleme.cli.get_or_create_machine_id", return_value="machine_mac"
        ), patch(
            "haoleme.cli.generate_pair_keypair", return_value=("public", "private")
        ):
            client_cls.return_value.start.side_effect = RuntimeError("stop")

            exit_code = pairing_login_command([])

        self.assertEqual(exit_code, 1)
        choose_server.assert_called_once_with()
        client_cls.assert_called_once_with(relay)

    def test_login_can_be_cancelled_during_server_selection(self):
        with patch("haoleme.cli.CloudConfig.load", return_value=None), patch(
            "haoleme.cli.stdin_is_interactive", return_value=True
        ), patch("haoleme.cli.prompt_login_relay_url", side_effect=KeyboardInterrupt), patch(
            "haoleme.cli.PairingClient"
        ) as client_cls:
            exit_code = pairing_login_command([])

        self.assertEqual(exit_code, 130)
        client_cls.assert_not_called()

    def test_interactive_login_can_choose_official_cloud(self):
        with patch("builtins.input", return_value=""):
            self.assertEqual(prompt_login_relay_url(), DEFAULT_CLOUD_URL)

    def test_interactive_login_can_choose_https_private_relay(self):
        with patch("builtins.input", side_effect=["2", "2", "https://relay.example.com"]):
            self.assertEqual(prompt_login_relay_url(), "https://relay.example.com")

    def test_interactive_login_can_choose_lan_private_relay(self):
        with patch("builtins.input", side_effect=["2", "2", "192.168.1.20:8000"]):
            self.assertEqual(prompt_login_relay_url(), "http://192.168.1.20:8000")

    def test_interactive_login_can_start_local_private_relay(self):
        with patch("builtins.input", side_effect=["2", ""]), patch(
            "haoleme.cli.start_local_relay_for_login",
            return_value="http://192.168.1.20:8000",
        ) as start_local:
            self.assertEqual(prompt_login_relay_url(), "http://192.168.1.20:8000")

        start_local.assert_called_once_with()

    def test_start_local_relay_for_login_reports_background_service(self):
        with patch(
            "haoleme.relay.ensure_background_lan_relay",
            return_value=("http://192.168.1.20:8000", True, 4321, Path("/tmp/relay.log")),
        ), redirect_stdout(io.StringIO()) as output:
            url = start_local_relay_for_login()

        self.assertEqual(url, "http://192.168.1.20:8000")
        self.assertIn("pid 4321", output.getvalue())

    def test_pairing_login_accepts_private_relay_as_positional_url(self):
        relay = "https://hao.example.com"
        with patch("haoleme.cli.PairingClient") as client_cls, \
                patch("haoleme.cli.CloudConfig.load", return_value=None), \
                patch("haoleme.cli.get_or_create_machine_id", return_value="machine_test"), \
                patch("haoleme.cli.generate_pair_keypair", return_value=("public", "private")):
            client_cls.return_value.start.side_effect = RuntimeError("stop")

            exit_code = pairing_login_command([relay])

        self.assertEqual(exit_code, 1)
        client_cls.assert_called_once_with(relay)

    def test_pairing_login_accepts_bare_private_lan_address(self):
        with patch("haoleme.cli.PairingClient") as client_cls, \
                patch("haoleme.cli.CloudConfig.load", return_value=None), \
                patch("haoleme.cli.get_or_create_machine_id", return_value="machine_test"), \
                patch("haoleme.cli.generate_pair_keypair", return_value=("public", "private")):
            client_cls.return_value.start.side_effect = RuntimeError("stop")

            exit_code = pairing_login_command(["192.168.1.20:8000"])

        self.assertEqual(exit_code, 1)
        client_cls.assert_called_once_with("http://192.168.1.20:8000")

    def test_relay_url_policy_keeps_https_and_limits_http_to_lan(self):
        self.assertEqual(normalize_relay_login_url("relay.example.com"), "https://relay.example.com")
        self.assertEqual(normalize_relay_login_url("localhost:8000"), "http://localhost:8000")
        self.assertEqual(normalize_relay_login_url("http://10.1.2.3:8000"), "http://10.1.2.3:8000")
        with self.assertRaises(ValueError):
            normalize_relay_login_url("http://8.8.8.8:8000")
        with self.assertRaises(ValueError):
            normalize_relay_login_url("http://relay.example.com:8000")
        with self.assertRaises(ValueError):
            normalize_relay_login_url("https://relay.example.com/unexpected")
        with self.assertRaisesRegex(ValueError, "LAN.*not an address"):
            normalize_relay_login_url("LAN")

    def test_login_server_label_hides_official_endpoint(self):
        self.assertEqual(login_server_label(DEFAULT_CLOUD_URL), "Haoleme Cloud")
        self.assertEqual(login_server_label("https://relay.example.com"), "Private Relay")

    def test_private_pair_qr_identifies_relay_protocol(self):
        pair_url = build_pair_url("https://hao.example.com/", "123456")

        self.assertIn("server=https%3A%2F%2Fhao.example.com", pair_url)
        self.assertIn("code=123456", pair_url)
        self.assertIn("mode=relay", pair_url)
        self.assertIn("v=1", pair_url)

    def test_command_needs_shell_detects_shell_syntax(self):
        # Single tokens with shell metacharacters / whitespace run via the shell.
        for token in ["echo a && echo b", "ls | wc -l", "cat > out.txt", "echo $HOME", "ls *.py"]:
            self.assertTrue(command_needs_shell(token), token)
        # Plain program names / paths execute directly.
        for token in ["ls", "npm", "./build.sh", "/usr/bin/python3", "my-tool"]:
            self.assertFalse(command_needs_shell(token), token)

    def test_leading_env_assignments_are_split_from_command(self):
        env, command = split_leading_env_assignments(["MODE=1", "EMPTY=", "bash", "run.sh"])

        self.assertEqual(env, {"MODE": "1", "EMPTY": ""})
        self.assertEqual(command, ["bash", "run.sh"])

    def test_leading_env_assignment_reaches_child_process(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-env", ["MODE=1", sys.executable, "-c", "import os; print(os.getenv('MODE'))"], "/tmp")

            exit_code, interrupted = run_command_with_pipes(
                [sys.executable, "-c", "import os; print(os.getenv('MODE'))"],
                store,
                "run-env",
                DummySyncer(),
                env={"MODE": "1"},
            )

            run = store.get_run("run-env")
            self.assertFalse(interrupted)
            self.assertEqual(exit_code, 0)
            self.assertIn("1\n", run.output_tail)

    def test_qr_terminal_rendering_uses_gap_free_blocks(self):
        lines = qr_matrix_to_terminal_lines([
            [True, False],
            [False, True],
        ])

        # One terminal line per matrix row, rendered with background-coloured
        # blocks (black dark / white light, no half-block glyphs) so scanning
        # stays reliable.
        self.assertEqual(len(lines), 2)
        self.assertNotIn("▀", "".join(lines))
        for line in lines:
            self.assertIn("\033[40m", line)  # black dark module
            self.assertIn("\033[47m", line)  # white light module

    def test_old_config_without_machine_id_is_not_reused(self):
        config = CloudConfig(
            api_url="http://cloud.example",
            account="default",
            token="x" * 32,
            device_id="dev_remote",
            device_name="5090",
        )

        self.assertEqual(reusable_login_device_id(config, "http://cloud.example", "machine_mac"), "")

    def test_config_is_reused_only_for_same_machine(self):
        config = CloudConfig(
            api_url="http://cloud.example",
            account="default",
            token="x" * 32,
            device_id="dev_mac",
            device_name="Mac",
            machine_id="machine_mac",
        )

        self.assertEqual(reusable_login_device_id(config, "http://cloud.example", "machine_mac"), "dev_mac")
        self.assertEqual(reusable_login_device_id(config, "http://cloud.example", "machine_ssh"), "")

    def test_new_device_overrides_saved_identity(self):
        config = CloudConfig(
            api_url="http://cloud.example",
            account="default",
            token="x" * 32,
            device_id="dev_mac",
            device_name="Mac",
            machine_id="machine_mac",
        )

        self.assertEqual(reusable_login_device_id(config, "http://cloud.example", "machine_mac", force_new=True), "")

    def test_heartbeat_initial_delay_is_staggered_within_interval(self):
        first = CloudConfig(
            api_url="http://cloud.example",
            account="default",
            token="x" * 32,
            device_id="dev_first",
            machine_id="machine_first",
        )
        second = CloudConfig(
            api_url="http://cloud.example",
            account="default",
            token="x" * 32,
            device_id="dev_second",
            machine_id="machine_second",
        )

        first_delay = heartbeat_initial_delay(first)
        second_delay = heartbeat_initial_delay(second)

        self.assertGreaterEqual(first_delay, 0)
        self.assertLess(first_delay, HEARTBEAT_INTERVAL_SECONDS)
        self.assertGreaterEqual(second_delay, 0)
        self.assertLess(second_delay, HEARTBEAT_INTERVAL_SECONDS)
        self.assertNotEqual(first_delay, second_delay)

    def test_process_running_handles_unexpected_oserror(self):
        with patch("haoleme.cli.os.name", "posix"), patch("haoleme.cli.os.kill", side_effect=OSError(11, "bad executable")):
            self.assertFalse(is_process_running(12345))

    def test_heartbeat_pid_must_belong_to_heartbeat_command(self):
        with patch("haoleme.cli.os.name", "posix"), \
                patch("haoleme.cli.is_process_running", return_value=True), \
                patch("haoleme.cli.Path.read_bytes", return_value=b"python\0-m\0haoleme\0heartbeat\0run\0"):
            self.assertTrue(is_heartbeat_process_running(4321))

        with patch("haoleme.cli.os.name", "posix"), \
                patch("haoleme.cli.is_process_running", return_value=True), \
                patch("haoleme.cli.Path.read_bytes", return_value=b"python\0train.py\0"):
            self.assertFalse(is_heartbeat_process_running(4321))

    @unittest.skipUnless(os.name == "posix", "file-lock semantics are platform specific")
    def test_heartbeat_file_lock_allows_only_one_owner(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "heartbeat.lock"
            first = acquire_process_file_lock(path, blocking=False)
            self.assertIsNotNone(first)
            try:
                self.assertIsNone(acquire_process_file_lock(path, blocking=False))
            finally:
                first.close()
            third = acquire_process_file_lock(path, blocking=False)
            self.assertIsNotNone(third)
            third.close()

    def test_pending_sync_honors_expired_maintenance_deadline(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-pending", ["echo", "hello"], "/tmp")

            class UnexpectedClient:
                def upsert_run(self, *_args, **_kwargs):
                    raise AssertionError("sync started after its maintenance deadline")

            synced = sync_pending_runs(store, UnexpectedClient(), deadline=time.monotonic() - 1)

            self.assertEqual(synced, 0)
            self.assertEqual(store.count_unsynced_runs(), 1)

    def test_background_sync_skips_active_run_owned_by_live_syncer(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-active", ["sleep", "10"], "/tmp")
            store.mark_running("run-active", 123)

            class UnexpectedClient:
                def upsert_run(self, *_args, **_kwargs):
                    raise AssertionError("heartbeat duplicated a live run upload")

            synced = sync_pending_runs(store, UnexpectedClient(), include_active=False)

            self.assertEqual(synced, 0)
            self.assertEqual(store.count_unsynced_runs(), 1)

    def test_windows_process_running_parses_tasklist_csv(self):
        result = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout='"python.exe","4321","Console","1","10,000 K"\n',
        )
        with patch("haoleme.cli.os.name", "nt"), patch("haoleme.cli.subprocess.run", return_value=result):
            self.assertTrue(is_process_running(4321))

    def test_windows_process_running_returns_false_when_missing(self):
        result = subprocess.CompletedProcess(args=[], returncode=0, stdout="INFO: No tasks are running which match the specified criteria.\n")
        with patch("haoleme.cli.os.name", "nt"), patch("haoleme.cli.subprocess.run", return_value=result):
            self.assertFalse(is_process_running(4321))

    def test_windows_terminate_process_uses_taskkill(self):
        result = subprocess.CompletedProcess(args=[], returncode=0, stdout="")
        with patch("haoleme.cli.subprocess.run", return_value=result) as run:
            self.assertTrue(terminate_windows_process(4321))
        run.assert_called_once()

    def test_collect_cpu_stats_returns_bounded_snapshot(self):
        stats = collect_cpu_stats()

        self.assertGreaterEqual(stats.get("cores", 0), 1)
        if "utilization" in stats:
            self.assertGreaterEqual(stats["utilization"], 0)
            self.assertLessEqual(stats["utilization"], 100)
        if "memoryUtilization" in stats:
            self.assertGreaterEqual(stats["memoryUtilization"], 0)
            self.assertLessEqual(stats["memoryUtilization"], 100)

    def test_parse_linux_meminfo_uses_available(self):
        text = "MemTotal:        16384000 kB\nMemFree:          1000000 kB\nMemAvailable:     8192000 kB\n"
        stats = _parse_linux_meminfo(text)
        self.assertEqual(stats["memoryTotal"], 16000)
        self.assertEqual(stats["memoryUsed"], 8000)
        self.assertEqual(stats["memoryUtilization"], 50)

    def test_parse_darwin_vm_stat_computes_utilization(self):
        text = (
            "Mach Virtual Memory Statistics: (page size of 16384 bytes)\n"
            "Pages free:                               1000.\n"
            "Pages active:                             2000.\n"
            "Pages inactive:                           500.\n"
            "Pages speculative:                        100.\n"
            "Pages wired down:                         400.\n"
            "Pages purgeable:                          200.\n"
        )
        # 1800 free-ish pages * 16384 = 29491200; total 64MB
        stats = _parse_darwin_vm_stat(text, 64 * 1024 * 1024)
        self.assertEqual(stats["memoryTotal"], 64)
        self.assertGreaterEqual(stats["memoryUtilization"], 0)
        self.assertLessEqual(stats["memoryUtilization"], 100)

    def test_collect_memory_stats_returns_bounded_snapshot(self):
        stats = collect_memory_stats()
        if not stats:
            return
        self.assertGreaterEqual(stats["memoryUtilization"], 0)
        self.assertLessEqual(stats["memoryUtilization"], 100)
        self.assertGreater(stats["memoryTotal"], 0)

    def test_parse_nvidia_compute_apps_groups_by_uuid(self):
        text = (
            "GPU-aaa, 1234, 1024, /usr/bin/python\n"
            "GPU-aaa, 99, 256, torchrun\n"
            "GPU-bbb, 7, 512, llama\n"
        )
        grouped = _parse_nvidia_compute_apps(text)
        self.assertEqual(len(grouped["GPU-aaa"]), 2)
        self.assertEqual(grouped["GPU-aaa"][0]["name"], "python")
        self.assertEqual(grouped["GPU-bbb"][0]["pid"], 7)

    def test_parse_posix_ps_reads_top_fields(self):
        text = "  42  12.5  3.0  204800 python\n  7   0.1  1.2   10240 sshd\n"
        rows = _parse_posix_ps(text)
        self.assertEqual(rows[0]["pid"], 42)
        self.assertEqual(rows[0]["cpu"], 12.5)
        self.assertEqual(rows[0]["memoryUsed"], 200)

    def test_collect_cpu_stats_uses_windows_cim_utilization(self):
        with patch("haoleme.cli.os.name", "nt"), patch(
            "haoleme.cli._linux_cpu_totals", return_value=None
        ), patch("haoleme.cli._windows_cpu_utilization", return_value=47):
            stats = collect_cpu_stats()

        self.assertEqual(stats["utilization"], 47)

    def test_windows_memory_stats_accepts_numeric_powershell_json(self):
        with patch(
            "haoleme.cli._windows_json_command",
            return_value={"total": 16 * 1024 * 1024, "free": 6 * 1024 * 1024},
        ):
            stats = _windows_memory_stats()

        self.assertEqual(stats["memoryTotal"], 16 * 1024)
        self.assertEqual(stats["memoryUsed"], 10 * 1024)
        self.assertEqual(stats["memoryUtilization"], 62)

    def test_metric_failure_does_not_suppress_heartbeat_payload(self):
        with patch("haoleme.cli.collect_gpu_stats", side_effect=RuntimeError("counter unavailable")), patch(
            "haoleme.cli.collect_cpu_stats", side_effect=AttributeError("bad CIM value")
        ), patch("haoleme.cli.os.cpu_count", return_value=12):
            gpus, cpu, error = collect_heartbeat_metrics()

        self.assertEqual(gpus, [])
        self.assertEqual(cpu, {"cores": 12})
        self.assertIn("GPU: RuntimeError", error)
        self.assertIn("CPU/memory: AttributeError", error)

    def test_parse_windows_gpu_payload_supports_amd_adapter(self):
        payload = {
            "adapters": [
                {"name": "AMD Radeon RX 7900 XTX", "adapterRam": 8589934592},
                {"name": "Microsoft Basic Display Adapter", "adapterRam": None},
            ],
            "engines": [
                {"instance": "pid_12_luid_0x0_0x1_phys_0_eng_0_engtype_3d", "value": 72.4},
                {"instance": "pid_12_luid_0x0_0x1_phys_0_eng_1_engtype_copy", "value": 18.0},
            ],
        }

        gpus = _parse_windows_gpu_payload(payload)

        self.assertEqual(len(gpus), 1)
        self.assertEqual(gpus[0]["name"], "AMD Radeon RX 7900 XTX")
        self.assertEqual(gpus[0]["utilization"], 72)
        self.assertEqual(gpus[0]["memoryTotal"], 8192)

    def test_collect_gpu_stats_merges_windows_amd_with_nvidia(self):
        nvidia = [{"index": 0, "name": "NVIDIA GeForce RTX 4090", "utilization": 10}]
        windows = [
            {"index": 0, "name": "NVIDIA GeForce RTX 4090", "utilization": 11},
            {"index": 1, "name": "AMD Radeon Graphics", "utilization": 33},
        ]
        with patch("haoleme.cli.os.name", "nt"), patch(
            "haoleme.cli._collect_nvidia_gpu_stats", return_value=nvidia
        ), patch("haoleme.cli._collect_windows_gpu_stats", return_value=windows):
            gpus = collect_gpu_stats()

        self.assertEqual([gpu["name"] for gpu in gpus], ["NVIDIA GeForce RTX 4090", "AMD Radeon Graphics"])

    def test_heartbeat_recovers_orphaned_running_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            client = DummyCloudClient()
            store.create_run("run-orphan", ["sleep", "10"], "/tmp")
            store.mark_running("run-orphan", 999999)
            run = store.get_run("run-orphan")
            now_timestamp = datetime.fromisoformat(run.updated_at.replace("Z", "+00:00")).timestamp()
            now_timestamp += ORPHANED_RUN_GRACE_SECONDS + 1

            recovered = reconcile_orphaned_running_runs(
                store,
                client,
                process_running=lambda _pid: False,
                now_timestamp=now_timestamp,
            )

            updated = store.get_run("run-orphan")
            self.assertEqual(recovered, 1)
            self.assertEqual(updated.status, "cancelled")
            self.assertEqual(len(client.synced), 1)
            self.assertEqual(client.synced[0].status, "cancelled")

    def test_heartbeat_keeps_live_running_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            client = DummyCloudClient()
            store.create_run("run-live", ["sleep", "10"], "/tmp")
            store.mark_running("run-live", 123)
            run = store.get_run("run-live")
            now_timestamp = datetime.fromisoformat(run.updated_at.replace("Z", "+00:00")).timestamp()
            now_timestamp += ORPHANED_RUN_GRACE_SECONDS + 1

            recovered = reconcile_orphaned_running_runs(
                store,
                client,
                process_running=lambda _pid: True,
                now_timestamp=now_timestamp,
            )

            updated = store.get_run("run-live")
            self.assertEqual(recovered, 0)
            self.assertEqual(updated.status, "running")
            self.assertEqual(client.synced, [])

    def test_heartbeat_marks_stale_active_run_pending(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-live", ["sleep", "10"], "/tmp")
            store.mark_running("run-live", 123)
            store.mark_cloud_synced("run-live")
            run = store.get_run("run-live")
            now_timestamp = datetime.fromisoformat(run.cloud_synced_at.replace("Z", "+00:00")).timestamp()
            now_timestamp += 31

            marked = mark_stale_active_runs_pending(store, max_age_seconds=30, now_timestamp=now_timestamp)

            self.assertEqual(marked, 1)
            self.assertEqual(store.get_run("run-live").cloud_synced_at, "")

    def test_heartbeat_state_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            config_path = Path(tmp) / "config.json"
            with patch("haoleme.cli.default_config_path", return_value=config_path):
                write_heartbeat_state(lastOkAt="2026-06-20T00:00:00Z", pendingRuns=3, lastError="")

                state = read_heartbeat_state()

                self.assertEqual(heartbeat_state_path(), Path(tmp) / "heartbeat.json")
                self.assertEqual(state["lastOkAt"], "2026-06-20T00:00:00Z")
                self.assertEqual(state["pendingRuns"], 3)

    def test_restart_heartbeat_stops_then_starts(self):
        with patch("haoleme.cli.stop_heartbeat_daemon", return_value=(True, "stopped")) as stop, patch(
            "haoleme.cli.start_heartbeat_daemon", return_value=(True, "started (pid 9)")
        ) as start:
            started, message = restart_heartbeat_daemon()
        self.assertTrue(started)
        self.assertIn("started", message)
        stop.assert_called_once()
        start.assert_called_once()

    def test_stream_output_records_even_when_terminal_is_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-output", ["echo", "hello"], "/tmp")

            stream_output(io.StringIO("hello\n"), BrokenTarget(), store, "run-output", "stdout_tail", stop=DummyStop())

            run = store.get_run("run-output")
            self.assertEqual(run.output_tail, "hello\n")

    def test_interrupt_watcher_triggers_callback(self):
        class Client:
            def list_pending_interrupts(self):
                return [{"id": "run-1", "interruptRequestedAt": "2026-06-18T01:00:00Z"}]

        triggered = threading.Event()
        watcher = InterruptWatcher(Client(), "run-1", triggered.set)
        watcher.start()
        self.assertTrue(triggered.wait(timeout=3))
        watcher.stop()
        self.assertTrue(watcher.triggered())

    @unittest.skipUnless(os.name == "posix", "process groups are POSIX-only")
    def test_terminate_process_on_interrupt_stops_bash_loop(self):
        proc = subprocess.Popen(
            ["bash", "-c", "for i in 1 2 3 4 5; do sleep 1; done"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            **subprocess_session_kwargs(),
        )
        try:
            time.sleep(1.5)
            event = threading.Event()
            event.set()
            self.assertTrue(terminate_process_on_interrupt(proc, event))
            proc.wait(timeout=5)
            self.assertIsNotNone(proc.returncode)
            self.assertNotEqual(proc.returncode, 0)
        finally:
            if proc.poll() is None:
                proc.kill()

    def test_run_command_with_pipes_stops_on_interrupt_event(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-stop", [sys.executable, "-c", "import time; time.sleep(30)"], "/tmp")
            interrupt_event = threading.Event()

            def trigger():
                time.sleep(0.5)
                interrupt_event.set()

            threading.Thread(target=trigger, daemon=True).start()
            exit_code, interrupted = run_command_with_pipes(
                [sys.executable, "-c", "import time; time.sleep(30)"],
                store,
                "run-stop",
                DummySyncer(),
                interrupt_event,
            )

            self.assertTrue(interrupted)
            self.assertNotEqual(exit_code, 0)

    def test_run_command_marks_mobile_interrupt_as_failed(self):
        class FakeWatcher:
            last_error = ""

            def __init__(self, _client, _run_id, on_interrupt):
                self.on_interrupt = on_interrupt
                self._triggered = threading.Event()

            def start(self):
                def trigger():
                    time.sleep(0.3)
                    self._triggered.set()
                    self.on_interrupt()

                threading.Thread(target=trigger, daemon=True).start()

            def stop(self):
                pass

            def triggered(self):
                return self._triggered.is_set()

        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            with patch("haoleme.cli.start_heartbeat_daemon", return_value=(False, "disabled")), \
                    patch("haoleme.cli.start_background_update_check", return_value={}) as update_start, \
                    patch("haoleme.cli.print_update_notice_after_command") as update_notice, \
                    patch("haoleme.cli.configured_cloud_client", return_value=None), \
                    patch("haoleme.cli.default_project", return_value=""), \
                    patch("haoleme.cli.should_use_pty", return_value=False), \
                    patch("haoleme.cli.InterruptWatcher", FakeWatcher), \
                    patch("haoleme.cli.RunStore", return_value=store), \
                    patch("haoleme.cli.uuid.uuid4", return_value="run-interrupt"):
                exit_code = run_command([sys.executable, "-c", "import time; time.sleep(30)"])

            update_start.assert_called_once_with()
            update_notice.assert_called_once_with({})

            run = store.get_run("run-interrupt")
            self.assertEqual(exit_code, 130)
            self.assertEqual(run.status, "failed")
            self.assertEqual(run.exit_code, 130)
            self.assertIn("Interrupted from mobile app", run.output_tail)

    @unittest.skipUnless(os.name == "posix" and hasattr(signal, "SIGHUP"), "SIGHUP is POSIX-only")
    def test_child_command_ignores_sighup(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-hup", [sys.executable, "-c", "signal"], "/tmp")

            exit_code, interrupted = run_command_with_pipes(
                [sys.executable, "-c", "import signal; print(signal.getsignal(signal.SIGHUP) == signal.SIG_IGN)"],
                store,
                "run-hup",
                DummySyncer(),
            )

            run = store.get_run("run-hup")
            self.assertFalse(interrupted)
            self.assertEqual(exit_code, 0)
            self.assertIn("True", run.output_tail)


class DummyStop:
    def is_set(self):
        return False


if __name__ == "__main__":
    unittest.main()
