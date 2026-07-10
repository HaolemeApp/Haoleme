import unittest
import tempfile
import io
import os
import signal
import subprocess
import sys
import threading
import time
from unittest.mock import patch
from datetime import datetime
from pathlib import Path

from haoleme.cli import (
    HEARTBEAT_INTERVAL_SECONDS,
    ORPHANED_RUN_GRACE_SECONDS,
    command_needs_shell,
    compare_versions,
    collect_cpu_stats,
    heartbeat_initial_delay,
    heartbeat_state_path,
    is_process_running,
    main,
    mark_stale_active_runs_pending,
    latest_python_release,
    pairing_login_command,
    python_wheel_candidates,
    update_command,
    version_command,
    qr_matrix_to_terminal_lines,
    read_heartbeat_state,
    reconcile_orphaned_running_runs,
    reusable_login_device_id,
    run_command,
    run_command_with_pipes,
    subprocess_session_kwargs,
    terminate_process_on_interrupt,
    should_continue_relogin,
    split_leading_env_assignments,
    stream_output,
    terminate_windows_process,
    write_heartbeat_state,
)
from haoleme.cloud import CloudConfig, DEFAULT_CLOUD_URL, InterruptWatcher
from haoleme.store import RunStore


class DummyCloudClient:
    def __init__(self):
        self.synced = []

    def upsert_run(self, run):
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
    def test_compare_versions_orders_semver_like_values(self):
        self.assertEqual(compare_versions("0.3.9", "0.3.10"), -1)
        self.assertEqual(compare_versions("0.3.19", "0.3.19"), 0)
        self.assertEqual(compare_versions("1.0.0", "0.9.9"), 1)

    def test_version_command_prints_current_version(self):
        buffer = io.StringIO()
        with patch("sys.stdout", buffer), patch("haoleme.cli.fetch_update_manifest") as fetch:
            fetch.return_value = ({"python": {"version": "9.9.9"}}, "http://example.test/downloads/update.json")
            exit_code = version_command([])
        output = buffer.getvalue()
        self.assertIn("haoleme", output)
        self.assertEqual(exit_code, 0)

    def test_version_check_exits_when_update_available(self):
        with patch("haoleme.cli.fetch_update_manifest") as fetch, patch("haoleme.cli.__version__", "0.0.1"):
            fetch.return_value = ({"python": {"version": "9.9.9"}}, "http://example.test/downloads/update.json")
            self.assertEqual(version_command(["--check"]), 1)

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

    def test_relogin_prompt_enter_confirms_and_n_cancels(self):
        self.assertTrue(should_continue_relogin(""))
        self.assertTrue(should_continue_relogin("yes"))
        self.assertFalse(should_continue_relogin("n"))
        self.assertFalse(should_continue_relogin(" cancel "))

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
                    patch("haoleme.cli.configured_cloud_client", return_value=None), \
                    patch("haoleme.cli.default_project", return_value=""), \
                    patch("haoleme.cli.should_use_pty", return_value=False), \
                    patch("haoleme.cli.InterruptWatcher", FakeWatcher), \
                    patch("haoleme.cli.RunStore", return_value=store), \
                    patch("haoleme.cli.uuid.uuid4", return_value="run-interrupt"):
                exit_code = run_command([sys.executable, "-c", "import time; time.sleep(30)"])

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
