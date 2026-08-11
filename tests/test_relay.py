import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from haoleme.relay import configure_relay_environment, default_relay_data_dir


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


if __name__ == "__main__":
    unittest.main()
