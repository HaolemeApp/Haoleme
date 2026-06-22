import json
import tempfile
import unittest
from pathlib import Path

from haoleme.cloud import CloudClient, CloudConfig, generate_account_token, get_or_create_machine_id
from haoleme.crypto import generate_account_key
from haoleme.store import RunRecord


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
