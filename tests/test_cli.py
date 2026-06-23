import unittest
import tempfile
import io
import os
import signal
import sys
from unittest.mock import patch
from datetime import datetime
from pathlib import Path

from haoleme.cli import (
    HEARTBEAT_INTERVAL_SECONDS,
    ORPHANED_RUN_GRACE_SECONDS,
    command_needs_shell,
    heartbeat_initial_delay,
    heartbeat_state_path,
    main,
    pairing_login_command,
    qr_matrix_to_terminal_lines,
    read_heartbeat_state,
    reconcile_orphaned_running_runs,
    reusable_login_device_id,
    run_command_with_pipes,
    should_continue_relogin,
    stream_output,
    write_heartbeat_state,
)
from haoleme.cloud import CloudConfig, DEFAULT_CLOUD_URL
from haoleme.store import RunStore


class DummyCloudClient:
    def __init__(self):
        self.synced = []

    def upsert_run(self, run):
        self.synced.append(run)


class DummySyncer:
    def request_sync(self):
        pass


class BrokenTarget:
    def write(self, _value):
        raise BrokenPipeError("closed")

    def flush(self):
        raise BrokenPipeError("closed")


class CliPairingTest(unittest.TestCase):
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

    @unittest.skipUnless(os.name == "posix" and hasattr(signal, "SIGHUP"), "SIGHUP is POSIX-only")
    def test_child_command_ignores_sighup(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-hup", [sys.executable, "-c", "signal"], "/tmp")

            exit_code = run_command_with_pipes(
                [sys.executable, "-c", "import signal; print(signal.getsignal(signal.SIGHUP) == signal.SIG_IGN)"],
                store,
                "run-hup",
                DummySyncer(),
            )

            run = store.get_run("run-hup")
            self.assertEqual(exit_code, 0)
            self.assertIn("True", run.output_tail)


class DummyStop:
    def is_set(self):
        return False


if __name__ == "__main__":
    unittest.main()
