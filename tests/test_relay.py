import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from haoleme.relay import configure_relay_environment, default_relay_data_dir, main, run_lan_pairing


class RelayTest(unittest.TestCase):
    def test_private_relay_defaults_require_e2ee(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(
            os.environ,
            {"HAOLEME_RELAY_DATA_DIR": tmp},
            clear=True,
        ):
            configure_relay_environment()

            self.assertEqual(os.environ["HAOLEME_REQUIRE_E2EE"], "1")
            self.assertEqual(os.environ["HAOLEME_ALLOW_LEGACY_ADMIN_TOKENS"], "0")
            self.assertEqual(os.environ["HAOLEME_CLOUD_HOST"], "127.0.0.1")
            self.assertEqual(os.environ["HAOLEME_CLOUD_DB"], str(Path(tmp) / "relay.db"))

    def test_relay_data_dir_expands_operator_path(self):
        with patch.dict(os.environ, {"HAOLEME_RELAY_DATA_DIR": "~/private-relay"}, clear=True):
            self.assertEqual(default_relay_data_dir(), Path.home() / "private-relay")

    def test_lan_mode_binds_all_interfaces_without_changing_relay_security(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(
            os.environ,
            {"HAOLEME_RELAY_DATA_DIR": tmp},
            clear=True,
        ), patch("haoleme.relay.local_lan_addresses", return_value=["192.168.1.20"]), patch(
            "haoleme.relay.cloud_server.main", return_value=0
        ) as cloud_main, patch("haoleme.relay.start_lan_pairing_thread") as start_pairing:
            result = main(["--lan", "--port", "8123"])

            self.assertEqual(result, 0)
            self.assertEqual(os.environ["HAOLEME_CLOUD_HOST"], "0.0.0.0")
            self.assertEqual(os.environ["HAOLEME_REQUIRE_E2EE"], "1")
            start_pairing.assert_called_once_with("192.168.1.20", 8123)
            cloud_main.assert_called_once_with(["--port", "8123"])

    def test_lan_no_pair_only_starts_relay(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(
            os.environ,
            {"HAOLEME_RELAY_DATA_DIR": tmp},
            clear=True,
        ), patch("haoleme.relay.local_lan_addresses", return_value=["192.168.1.20"]), patch(
            "haoleme.relay.cloud_server.main", return_value=0
        ) as cloud_main, patch("haoleme.relay.start_lan_pairing_thread") as start_pairing:
            result = main(["--lan", "--no-pair", "--port", "8123"])

            self.assertEqual(result, 0)
            start_pairing.assert_not_called()
            cloud_main.assert_called_once_with(["--port", "8123"])

    def test_lan_pairing_waits_for_health_then_uses_lan_address(self):
        with patch("haoleme.relay.wait_for_relay_health", return_value=True), patch(
            "haoleme.cli.pairing_login_command", return_value=0
        ) as login:
            result = run_lan_pairing("192.168.1.20", 8123)

            self.assertEqual(result, 0)
            login.assert_called_once_with(["http://192.168.1.20:8123", "--yes"])


if __name__ == "__main__":
    unittest.main()
