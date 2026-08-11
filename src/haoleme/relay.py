from __future__ import annotations

import os
import ipaddress
import socket
import subprocess
import sys
import time
import urllib.request
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
    no_pair = "--no-pair" in args
    if no_pair:
        args.remove("--no-pair")
    if lan_mode:
        args.remove("--lan")
        if "--host" not in args:
            os.environ["HAOLEME_RELAY_HOST"] = "0.0.0.0"
    configure_relay_environment()
    if lan_mode:
        port = relay_port_from_args(args)
        addresses = local_lan_addresses()
        print("Haoleme Relay LAN mode (trusted networks only)")
        for address in addresses:
            print(f"Pair with: hao login http://{address}:{port}")
        print("Run contents remain E2EE, but HTTP does not protect credentials or metadata.")
        if not no_pair:
            if not addresses or addresses[0] == "YOUR_LAN_IP":
                print("Could not determine a private LAN address.", file=sys.stderr)
                return 1
            if wait_for_relay_health(port, timeout=0.3):
                print("Relay is already running on this port.")
            else:
                process, log_path = start_detached_relay(args)
                if not wait_for_relay_health(port):
                    process.terminate()
                    print(f"Relay failed to start. Check: {log_path}", file=sys.stderr)
                    return 1
                print(f"Relay started in background (pid {process.pid}).")
                print(f"Relay log: {log_path}")
            return run_lan_pairing(addresses[0], port)
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


def start_detached_relay(args: list[str]) -> tuple[subprocess.Popen[bytes], Path]:
    data_dir = default_relay_data_dir()
    log_path = data_dir / "relay-stdio.log"
    command = [sys.executable, "-m", "haoleme.relay", "--lan", "--no-pair", *args]
    popen_kwargs: dict[str, object] = {
        "stdin": subprocess.DEVNULL,
        "stderr": subprocess.STDOUT,
        "close_fds": True,
        "env": os.environ.copy(),
    }
    if os.name == "nt":
        popen_kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
    else:
        popen_kwargs["start_new_session"] = True
    with log_path.open("ab") as log_file:
        process = subprocess.Popen(command, stdout=log_file, **popen_kwargs)
    (data_dir / "relay.pid").write_text(f"{process.pid}\n", encoding="utf-8")
    return process, log_path


def run_lan_pairing(address: str, port: int) -> int:
    if not wait_for_relay_health(port):
        print("\nCould not start pairing: the local Relay did not become ready.", file=sys.stderr)
        return 1
    from .cli import pairing_login_command

    print("\nRelay is ready. Pairing this computer with the mobile app...\n")
    result = pairing_login_command([f"http://{address}:{port}", "--yes"])
    if result == 0:
        print("\nRelay continues running in the background.")
    return result


def wait_for_relay_health(port: int, timeout: float = 10.0) -> bool:
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with opener.open(f"http://127.0.0.1:{port}/health", timeout=0.5) as response:
                if response.status == 200:
                    return True
        except Exception:
            time.sleep(0.1)
    return False


if __name__ == "__main__":
    raise SystemExit(main())
