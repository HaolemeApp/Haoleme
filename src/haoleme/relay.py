from __future__ import annotations

import os
import ipaddress
import socket
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
    args = list(sys.argv[1:] if argv is None else argv)
    lan_mode = "--lan" in args
    if lan_mode:
        args.remove("--lan")
        if "--host" not in args:
            os.environ["HAOLEME_RELAY_HOST"] = "0.0.0.0"
    configure_relay_environment()
    if lan_mode:
        port = relay_port_from_args(args)
        print("Haoleme Relay LAN mode (trusted networks only)")
        for address in local_lan_addresses():
            print(f"Pair with: hao login http://{address}:{port}")
        print("Run contents remain E2EE, but HTTP does not protect credentials or metadata.")
    try:
        return cloud_server.main(args)
    except KeyboardInterrupt:
        return 130


def relay_port_from_args(args: list[str]) -> int:
    try:
        index = args.index("--port")
        return int(args[index + 1])
    except (ValueError, IndexError):
        return int(os.environ.get("HAOLEME_RELAY_PORT", "8000"))


def local_lan_addresses() -> list[str]:
    addresses: set[str] = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            address = info[4][0]
            parsed = ipaddress.ip_address(address)
            if parsed.is_private and not parsed.is_loopback:
                addresses.add(address)
    except OSError:
        pass
    return sorted(addresses) or ["YOUR_LAN_IP"]


if __name__ == "__main__":
    raise SystemExit(main())
