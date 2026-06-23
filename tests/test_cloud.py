import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import haoleme.cloud as cloud_module
from haoleme.cloud import CloudClient, CloudConfig, CloudSyncer, DEFAULT_CLOUD_URL, generate_account_token, get_or_create_machine_id, normalize_cloud_url
from haoleme.crypto import generate_account_key
from haoleme.store import RunRecord


class TieredSyncIntervalTest(unittest.TestCase):
    def test_running_sync_interval_eases_with_age(self):
        syncer = CloudSyncer.__new__(CloudSyncer)
        syncer._started_at = 0.0
        for age, expected in [(0, 1.0), (120, 1.0), (600, 5.0), (1800, 10.0), (5000, 10.0)]:
            with patch.object(cloud_module.time, "monotonic", return_value=float(age)):
                self.assertAlmostEqual(syncer._running_sync_interval(), expected, places=2)
        prev = 0.0
        for age in range(0, 2000, 50):
            with patch.object(cloud_module.time, "monotonic", return_value=float(age)):
                cur = syncer._running_sync_interval()
            self.assertGreaterEqual(cur + 1e-9, prev)
            prev = cur


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
            "http://106.14.246.204",
            "https://106.14.246.204/",
            "http://api.haoleme.cloud",
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
                        "api_url": "http://106.14.246.204",
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


class CapturingCloudClient(CloudClient):
    def __init__(self, config):
        super().__init__(config)
        self.requests = []

    def request(self, method, path, payload=None):
        self.requests.append((method, path, payload))
        return {"ok": True}


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
        cloud_synced_at="",
    )


if __name__ == "__main__":
    unittest.main()
