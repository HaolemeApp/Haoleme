import json
import tempfile
import threading
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

import haoleme.cloud as cloud_module
from haoleme.cloud import CloudClient, CloudConfig, CloudSyncer, DEFAULT_CLOUD_URL, OUTPUT_CHUNK_BYTES, generate_account_token, get_or_create_machine_id, next_output_chunk, normalize_cloud_url
from haoleme.crypto import generate_account_key
from haoleme.store import RunRecord, RunStore


class TieredSyncIntervalTest(unittest.TestCase):
    def test_running_sync_interval_eases_with_output_idle(self):
        syncer = CloudSyncer.__new__(CloudSyncer)
        syncer._last_output_at = 0.0
        # Interval depends on time since last output, not run age.
        for idle, expected in [(0, 1.0), (120, 1.0), (600, 5.0), (1800, 10.0), (5000, 10.0)]:
            with patch.object(cloud_module.time, "monotonic", return_value=float(idle)):
                self.assertAlmostEqual(syncer._running_sync_interval(), expected, places=2)
        # Fresh output (idle resets to ~0) snaps back to the fast tier.
        syncer._last_output_at = 4000.0
        with patch.object(cloud_module.time, "monotonic", return_value=4001.0):
            self.assertAlmostEqual(syncer._running_sync_interval(), 1.0, places=2)
        prev = 0.0
        syncer._last_output_at = 0.0
        for idle in range(0, 2000, 50):
            with patch.object(cloud_module.time, "monotonic", return_value=float(idle)):
                cur = syncer._running_sync_interval()
            self.assertGreaterEqual(cur + 1e-9, prev)
            prev = cur


class CloudSyncerReliabilityTest(unittest.TestCase):
    def test_output_delta_uses_total_length_after_tail_trimming(self):
        run = replace(
            sample_run_record(),
            output_tail="a" * 20 + "NEW!!",
            output_length=105,
        )
        syncer = CloudSyncer.__new__(CloudSyncer)
        syncer._synced_output_len = 100
        syncer._synced_stdout_len = 0
        syncer._synced_stderr_len = 0

        deltas = syncer._output_deltas(run)

        self.assertEqual(deltas["output_tail"], "NEW!!")

    def test_failed_sync_marks_run_pending_for_later_replay(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-1", ["sleep", "60"], "/tmp")
            store.mark_running("run-1", 123)
            store.mark_cloud_synced("run-1")

            syncer = CloudSyncer.__new__(CloudSyncer)
            syncer.store = store
            syncer.run_id = "run-1"
            syncer.client = FailingSyncClient()
            syncer._event = threading.Event()
            syncer._stop = threading.Event()
            syncer._thread = None
            syncer.last_error = None
            syncer._last_sync_at = 0.0
            syncer._started_at = 0.0
            syncer._last_output_at = 0.0
            syncer._initial_synced = True
            syncer._synced_output_len = 0
            syncer._synced_stdout_len = 0
            syncer._synced_stderr_len = 0
            syncer._failure_count = 0
            syncer._next_retry_at = 0.0

            syncer._sync_once(force=True)

            self.assertEqual(store.get_run("run-1").cloud_synced_at, "")
            self.assertIn("offline", syncer.last_error)
            self.assertGreater(syncer._next_retry_at, 0)

    def test_large_output_is_split_and_persistent_cursor_advances(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = RunStore(Path(tmp) / "runs.db")
            store.create_run("run-1", ["train"], "/tmp")
            store.mark_running("run-1", 123)
            store.append_output("run-1", "stdout_tail", "x" * (OUTPUT_CHUNK_BYTES + 1000))
            client = SuccessfulChunkClient()

            syncer = CloudSyncer.__new__(CloudSyncer)
            syncer.store = store
            syncer.run_id = "run-1"
            syncer.client = client
            syncer._event = threading.Event()
            syncer._stop = threading.Event()
            syncer._thread = None
            syncer.last_error = None
            syncer._last_sync_at = 0.0
            syncer._started_at = 0.0
            syncer._last_output_at = 0.0
            syncer._initial_synced = False
            syncer._synced_output_len = 0
            syncer._synced_stdout_len = 0
            syncer._synced_stderr_len = 0
            syncer._failure_count = 0
            syncer._next_retry_at = 0.0

            syncer._sync_once(force=True)

            run = store.get_run("run-1")
            self.assertEqual(len(client.chunks), 2)
            self.assertTrue(all(len(item["text"].encode("utf-8")) <= OUTPUT_CHUNK_BYTES for item in client.chunks))
            self.assertEqual(run.cloud_output_cursor, run.output_length)
            self.assertTrue(run.cloud_synced_at)

    def test_utf8_chunk_boundary_never_exceeds_wire_limit(self):
        run = replace(sample_run_record(), output_tail="你" * 100_000, output_length=100_000)

        chunk, start, end = next_output_chunk(run, 0)

        self.assertEqual(start, 0)
        self.assertEqual(end, len(chunk))
        self.assertLessEqual(len(chunk.encode("utf-8")), OUTPUT_CHUNK_BYTES)


class CloudConfigTest(unittest.TestCase):
    def test_cloud_config_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "config.json"
            config = CloudConfig(
                api_url="https://example.com/",
                account="alice",
                token="token-" + generate_account_token(),
                device_id="dev_123",
                device_name="我的 Mac",
                machine_id="machine_abc1234567890",
                encryption_key="enc_key_123",
            )

            config.save(path)
            loaded = CloudConfig.load(path)

            self.assertIsNotNone(loaded)
            self.assertEqual(loaded.api_url, "https://example.com")
            self.assertEqual(loaded.account, "alice")
            self.assertEqual(loaded.token, config.token)
            self.assertEqual(loaded.device_id, "dev_123")
            self.assertEqual(loaded.device_name, "我的 Mac")
            self.assertEqual(loaded.machine_id, "machine_abc1234567890")
            self.assertEqual(loaded.encryption_key, "enc_key_123")

    def test_machine_id_roundtrip_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "machine_id"

            first = get_or_create_machine_id(path)
            second = get_or_create_machine_id(path)

            self.assertTrue(first.startswith("machine_"))
            self.assertEqual(first, second)

    def test_default_cloud_url_is_normalized(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "config.json"
            path.write_text(
                json.dumps({
                    "cloud": {
                        "enabled": True,
                        "api_url": "https://api.haoleme.cloud/",
                        "account": "default",
                        "token": "x" * 32,
                    }
                }),
                encoding="utf-8",
            )

            loaded = CloudConfig.load(path)

            self.assertIsNotNone(loaded)
            self.assertEqual(loaded.api_url, "https://api.haoleme.cloud")

    def test_legacy_cloud_urls_migrate_to_https_domain(self):
        legacy_urls = [
            "http://api.haoleme.cloud",
            "http://8.8.8.8",
            "https://8.8.8.8/",
            "http://8.8.8.8:80/",
            "https://8.8.8.8:443",
        ]
        for legacy in legacy_urls:
            with self.subTest(legacy=legacy):
                self.assertEqual(normalize_cloud_url(legacy), DEFAULT_CLOUD_URL)

    def test_legacy_cloud_url_is_saved_after_load(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "config.json"
            path.write_text(
                json.dumps({
                    "cloud": {
                        "enabled": True,
                        "api_url": "http://8.8.8.8:80/",
                        "account": "default",
                        "token": "x" * 32,
                    }
                }),
                encoding="utf-8",
            )

            loaded = CloudConfig.load(path)

            self.assertIsNotNone(loaded)
            self.assertEqual(loaded.api_url, DEFAULT_CLOUD_URL)
            saved = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(saved["cloud"]["api_url"], DEFAULT_CLOUD_URL)

    def test_private_ip_cloud_url_is_preserved(self):
        self.assertEqual(normalize_cloud_url("http://127.0.0.1:8765"), "http://127.0.0.1:8765")
        self.assertEqual(normalize_cloud_url("http://192.168.1.10:8000"), "http://192.168.1.10:8000")

    def test_disabled_cloud_config_is_ignored(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "config.json"
            path.write_text(
                json.dumps({"cloud": {"enabled": False, "api_url": "https://example.com", "token": "x"}}),
                encoding="utf-8",
            )

            self.assertIsNone(CloudConfig.load(path))

    def test_cloud_client_refuses_plaintext_run_uploads_by_default(self):
        config = CloudConfig(api_url="https://example.com", account="default", token="x" * 32)
        client = CapturingCloudClient(config)

        with self.assertRaisesRegex(RuntimeError, "E2EE is not configured"):
            client.upsert_run(sample_run_record())

        self.assertEqual(client.requests, [])

    def test_cloud_client_encrypts_run_payloads_when_key_is_available(self):
        config = CloudConfig(
            api_url="https://example.com",
            account="default",
            token="x" * 32,
            encryption_key=generate_account_key(),
        )
        client = CapturingCloudClient(config)

        client.upsert_run(sample_run_record())

        payload = client.requests[0][2]["run"]
        self.assertIn("e2ee", payload)
        self.assertEqual(payload["commandText"], "Encrypted command")
        self.assertNotIn("secret", json.dumps(payload))

    def test_metadata_upsert_does_not_advance_remote_output_cursor(self):
        config = CloudConfig(
            api_url="https://example.com",
            account="default",
            token="x" * 32,
            encryption_key=generate_account_key(),
        )
        client = CapturingCloudClient(config)

        client.upsert_run(sample_run_record(), include_output=False)

        self.assertEqual(client.requests[0][2]["run"]["outputLength"], 0)


class CapturingCloudClient(CloudClient):
    def __init__(self, config):
        super().__init__(config)
        self.requests = []

    def request(self, method, path, payload=None):
        self.requests.append((method, path, payload))
        return {"ok": True}


class FailingSyncClient:
    def append_run_update(self, _run, _deltas, *_cursor):
        raise RuntimeError("offline")

    def upsert_run(self, _run, *, include_output=True):
        raise RuntimeError("offline")


class SuccessfulChunkClient:
    def __init__(self):
        self.chunks = []

    def upsert_run(self, _run, *, include_output=True):
        return None

    def append_run_update(self, _run, deltas, output_start=None, output_end=None):
        text = deltas.get("output_tail", "")
        if text:
            self.chunks.append({"text": text, "start": output_start, "end": output_end})
        return {"ok": True, "outputLength": output_end or 0}


def sample_run_record():
    return RunRecord(
        id="run-1",
        command=["python", "-c", "print('secret')"],
        cwd="/private/project",
        project="",
        status="succeeded",
        pid=123,
        exit_code=0,
        started_at="2026-06-20T00:00:00Z",
        ended_at="2026-06-20T00:00:01Z",
        updated_at="2026-06-20T00:00:01Z",
        stdout_tail="secret\n",
        stderr_tail="",
        output_tail="secret\n",
        output_length=len("secret\n"),
        cloud_synced_at="",
    )


if __name__ == "__main__":
    unittest.main()
