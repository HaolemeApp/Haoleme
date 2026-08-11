from __future__ import annotations

import os
import sys
from pathlib import Path

from . import cloud_server


def default_relay_data_dir() -> Path:
    configured = os.environ.get("HAOLEME_RELAY_DATA_DIR", "").strip()
    if configured:
        return Path(configured).expanduser()
    return Path.home() / ".local" / "share" / "haoleme-relay"


def configure_relay_environment() -> None:
    """Apply private-relay defaults without overriding operator choices."""
    data_dir = default_relay_data_dir()
    data_dir.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("HAOLEME_CLOUD_HOST", os.environ.get("HAOLEME_RELAY_HOST", "127.0.0.1"))
    os.environ.setdefault("HAOLEME_CLOUD_PORT", os.environ.get("HAOLEME_RELAY_PORT", "8000"))
    os.environ.setdefault("HAOLEME_CLOUD_DB", str(data_dir / "relay.db"))
    os.environ.setdefault("HAOLEME_CLOUD_LOG", str(data_dir / "relay.log"))
    os.environ.setdefault("HAOLEME_CLOUD_BACKUP_DIR", str(data_dir / "backups"))

    # A private relay must never accept plaintext run payloads or legacy broad
    # bearer tokens. These settings keep the existing cloud protocol while
    # making the private deployment secure by default.
    os.environ["HAOLEME_REQUIRE_E2EE"] = "1"
    os.environ["HAOLEME_ALLOW_LEGACY_ADMIN_TOKENS"] = "0"


def main(argv: list[str] | None = None) -> int:
    configure_relay_environment()
    args = list(sys.argv[1:] if argv is None else argv)
    try:
        return cloud_server.main(args)
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
