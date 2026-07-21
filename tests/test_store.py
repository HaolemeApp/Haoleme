import multiprocessing
import sqlite3
import tempfile
import threading
import time
import unittest
from pathlib import Path

from haoleme.store import RunStore


def append_output_worker(db_path, run_id, marker, count, start_event, result_queue):
    try:
        store = RunStore(db_path)
        if not start_event.wait(10):
            raise RuntimeError("concurrent append start timed out")
        for _ in range(count):
            store.append_output(run_id, "stdout_tail", marker)
        result_queue.put("")
    except BaseException as exc:
        result_queue.put(f"{type(exc).__name__}: {exc}")


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

    def test_transient_write_lock_is_retried(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "runs.db"
            store = RunStore(
                db_path,
                sqlite_timeout_seconds=0.02,
                sqlite_write_retries=10,
            )
            store.create_run("run-locked", ["echo", "hello"], "/tmp")
            lock = sqlite3.connect(db_path)
            lock.execute("BEGIN IMMEDIATE")
            lock.execute("UPDATE runs SET updated_at = updated_at WHERE id = 'run-locked'")
            errors = []

            def append_while_locked():
                try:
                    store.append_output("run-locked", "stdout_tail", "after-lock\n")
                except BaseException as exc:
                    errors.append(exc)

            thread = threading.Thread(target=append_while_locked)
            thread.start()
            time.sleep(0.2)
            lock.commit()
            lock.close()
            thread.join(5)

            self.assertFalse(thread.is_alive())
            self.assertEqual(errors, [])
            self.assertEqual(store.get_run("run-locked").output_tail, "after-lock\n")

    def test_multiple_processes_append_without_database_lock_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "runs.db"
            store = RunStore(db_path)
            worker_count = 4
            append_count = 60
            marker = "output-line\n"
            for index in range(worker_count):
                store.create_run(f"run-{index}", ["train", str(index)], "/tmp")

            context = multiprocessing.get_context("spawn")
            start_event = context.Event()
            result_queue = context.Queue()
            workers = [
                context.Process(
                    target=append_output_worker,
                    args=(db_path, f"run-{index}", marker, append_count, start_event, result_queue),
                )
                for index in range(worker_count)
            ]
            for worker in workers:
                worker.start()
            start_event.set()
            for worker in workers:
                worker.join(20)

            errors = [result_queue.get(timeout=2) for _ in workers]
            self.assertTrue(all(not worker.is_alive() for worker in workers))
            self.assertEqual(errors, [""] * worker_count)
            for index in range(worker_count):
                run = store.get_run(f"run-{index}")
                self.assertEqual(run.output_length, append_count * len(marker))
                self.assertEqual(run.output_tail, marker * append_count)

    def test_database_uses_wal_journal_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            with store.connect() as conn:
                mode = conn.execute("PRAGMA journal_mode").fetchone()[0]
            self.assertEqual(mode.lower(), "wal")


if __name__ == "__main__":
    unittest.main()
