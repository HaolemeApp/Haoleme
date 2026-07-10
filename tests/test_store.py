import tempfile
import unittest
from pathlib import Path

from haoleme.store import RunStore


class RunStoreTest(unittest.TestCase):
    def test_run_lifecycle(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")

            store.create_run("run-1", ["echo", "hello"], "/tmp")
            store.mark_running("run-1", 123)
            store.append_output("run-1", "stdout_tail", "hello\n")
            store.finish_run("run-1", 0)

            run = store.get_run("run-1")

            self.assertIsNotNone(run)
            self.assertEqual(run.status, "succeeded")
            self.assertEqual(run.exit_code, 0)
            self.assertEqual(run.stdout_tail, "hello\n")
            self.assertEqual(run.output_tail, "hello\n")
            self.assertEqual(run.output_length, len("hello\n"))
            self.assertEqual(run.commandText, "echo hello")

    def test_failed_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")

            store.create_run("run-2", ["false"], "/tmp")
            store.finish_run("run-2", 1)

            run = store.get_run("run-2")

            self.assertIsNotNone(run)
            self.assertEqual(run.status, "failed")
            self.assertEqual(run.exit_code, 1)

    def test_cancel_run_marks_terminal_and_keeps_note(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")

            store.create_run("run-cancel", ["sleep", "10"], "/tmp")
            store.mark_running("run-cancel", 123)
            store.cancel_run("run-cancel", "\ninterrupted\n")

            run = store.get_run("run-cancel")

            self.assertIsNotNone(run)
            self.assertEqual(run.status, "cancelled")
            self.assertEqual(run.exit_code, -1)
            self.assertIn("interrupted", run.output_tail)

    def test_interrupt_run_marks_failed_and_keeps_note(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")

            store.create_run("run-interrupt", ["sleep", "10"], "/tmp")
            store.mark_running("run-interrupt", 123)
            store.interrupt_run("run-interrupt", "\ninterrupted\n")

            run = store.get_run("run-interrupt")

            self.assertIsNotNone(run)
            self.assertEqual(run.status, "failed")
            self.assertEqual(run.exit_code, 130)
            self.assertIn("interrupted", run.output_tail)

    def test_delete_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")

            store.create_run("run-3", ["sleep", "1"], "/tmp")

            self.assertTrue(store.delete_run("run-3"))
            self.assertIsNone(store.get_run("run-3"))
            self.assertFalse(store.delete_run("run-3"))

    def test_output_tail_size_is_configurable(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db", output_tail_chars=30000)
            store.create_run("run-tail", ["python", "big.py"], "/tmp")

            store.append_output("run-tail", "stdout_tail", "a" * 40000)

            run = store.get_run("run-tail")
            self.assertEqual(len(run.output_tail), 30000)
            self.assertEqual(run.output_tail, "a" * 30000)
            self.assertEqual(run.output_length, 40000)

    def test_output_length_keeps_growing_after_tail_is_trimmed(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db", output_tail_chars=30000)
            store.create_run("run-total", ["python", "train.py"], "/tmp")

            store.append_output("run-total", "stdout_tail", "a" * 25000)
            store.append_output("run-total", "stdout_tail", "b" * 25000)

            run = store.get_run("run-total")
            self.assertEqual(len(run.output_tail), 30000)
            self.assertEqual(run.output_length, 50000)


if __name__ == "__main__":
    unittest.main()
