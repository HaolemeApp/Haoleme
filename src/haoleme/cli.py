from __future__ import annotations

import argparse
import hashlib
import json
import os
import select
import re
import secrets
import shlex
import signal
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse
import uuid
from collections.abc import Callable, Sequence
from pathlib import Path
from socket import gethostname

from . import __version__
from .cloud import DEFAULT_CLOUD_URL, CloudClient, CloudConfig, CloudSyncer, PairingClient, default_config_path, describe_cloud_error, generate_account_token, generate_device_id, get_or_create_machine_id
from .crypto import decrypt_account_key, generate_pair_keypair
from .server import serve
from .store import RunStore, default_db_path


RESERVED_COMMANDS = {"run", "server", "status", "public", "ngrok", "login", "heartbeat", "cloud-login", "cloud-logout", "cloud-status", "project", "doctor", "sync"}
PUBLIC_URL_RE = re.compile(r"https://[a-zA-Z0-9.-]+\.trycloudflare\.com")
HEARTBEAT_INTERVAL_SECONDS = 60
ORPHANED_RUN_GRACE_SECONDS = 30


def main(argv: Sequence[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if not args:
        print_help()
        return 2

    first = args[0]
    if first in {"-h", "--help"}:
        print_help()
        return 0
    if first == "server":
        return server_command(args[1:])
    if first == "public":
        return public_command(args[1:])
    if first == "ngrok":
        return ngrok_command(args[1:])
    if first == "status":
        return status_command(args[1:])
    if first == "login":
        return pairing_login_command(args[1:])
    if first == "heartbeat":
        return heartbeat_command(args[1:])
    if first == "cloud-login":
        return cloud_login_command(args[1:])
    if first == "cloud-logout":
        return cloud_logout_command(args[1:])
    if first == "cloud-status":
        return cloud_status_command(args[1:])
    if first == "project":
        return project_command(args[1:])
    if first == "doctor":
        return doctor_command(args[1:])
    if first == "sync":
        return sync_command(args[1:])
    if first == "run":
        command, project_override = parse_run_args(args[1:])
        return run_command(command, project_override=project_override)

    command, project_override = parse_run_args(args)
    return run_command(command, project_override=project_override)


def server_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument(
        "--token",
        default=None,
        help="Bearer token required by /api/* endpoints. Can also use HAOLEME_TOKEN.",
    )
    ns = parser.parse_args(argv)
    serve(ns.host, ns.port, token=ns.token)
    return 0


def public_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao public")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--token", default=None)
    parser.add_argument("--cloudflared", default="cloudflared")
    ns = parser.parse_args(argv)

    cloudflared = shutil.which(ns.cloudflared) if ns.cloudflared == "cloudflared" else ns.cloudflared
    if not cloudflared:
        print(
            "hao: cloudflared is required for public tunnels.\n"
            "Install it with: brew install cloudflared\n"
            "Linux: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/",
            file=sys.stderr,
        )
        return 127

    token = ns.token or os.environ.get("HAOLEME_TOKEN") or secrets.token_urlsafe(24)
    local_url = f"http://{ns.host}:{ns.port}"

    print("Starting 好了么 local server...")
    print(f"Local:  {local_url}")
    print(f"Token:  {token}")
    print("Starting Cloudflare quick tunnel...")

    server_thread = threading.Thread(
        target=serve,
        args=(ns.host, ns.port),
        kwargs={"token": token},
        daemon=True,
    )
    server_thread.start()
    time.sleep(0.5)

    proc = subprocess.Popen(
        [cloudflared, "tunnel", "--url", local_url],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )

    public_url_seen = threading.Event()

    def stream_tunnel_output() -> None:
        if proc.stdout is None:
            return
        for line in proc.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            match = PUBLIC_URL_RE.search(line)
            if match and not public_url_seen.is_set():
                public_url_seen.set()
                print()
                print("Use this in the Android app:")
                print(f"Server: {match.group(0)}")
                print(f"Token:  {token}")
                print()

    output_thread = threading.Thread(target=stream_tunnel_output, daemon=True)
    output_thread.start()

    try:
        return proc.wait()
    except KeyboardInterrupt:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
        return 130


def ngrok_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao ngrok")
    parser.add_argument("--domain", required=True, help="Your fixed ngrok dev domain, like abc.ngrok-free.dev.")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--token", default=None)
    parser.add_argument("--ngrok", default="ngrok")
    ns = parser.parse_args(argv)

    ngrok = shutil.which(ns.ngrok) if ns.ngrok == "ngrok" else ns.ngrok
    if not ngrok:
        print(
            "hao: ngrok is required for fixed free dev domains.\n"
            "Install it with: brew install ngrok/ngrok/ngrok\n"
            "Then run: ngrok config add-authtoken <YOUR_NGROK_AUTHTOKEN>",
            file=sys.stderr,
        )
        return 127

    token = ns.token or os.environ.get("HAOLEME_TOKEN") or secrets.token_urlsafe(24)
    local_url = f"http://{ns.host}:{ns.port}"
    public_url = f"https://{ns.domain.removeprefix('https://').removeprefix('http://').rstrip('/')}"

    print("Starting 好了么 local server...")
    print(f"Local:  {local_url}")
    print(f"Server: {public_url}")
    print(f"Token:  {token}")
    print("Starting ngrok fixed dev domain tunnel...")

    server_thread = threading.Thread(
        target=serve,
        args=(ns.host, ns.port),
        kwargs={"token": token},
        daemon=True,
    )
    server_thread.start()
    time.sleep(0.5)

    proc = subprocess.Popen(
        [ngrok, "http", str(ns.port), "--url", ns.domain.removeprefix("https://").removeprefix("http://").rstrip("/")],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )

    print()
    print("Use this in the Android app:")
    print(f"Server: {public_url}")
    print(f"Token:  {token}")
    print()

    try:
        if proc.stdout is not None:
            for line in proc.stdout:
                sys.stdout.write(line)
                sys.stdout.flush()
        return proc.wait()
    except KeyboardInterrupt:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
        return 130


def project_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao project")
    subparsers = parser.add_subparsers(dest="action")

    use_parser = subparsers.add_parser("use", help="Set the default project for future hao runs.")
    use_parser.add_argument("name", help="Project name, for example: website or paper-tests")

    subparsers.add_parser("clear", help="Stop assigning future runs to a default project.")
    subparsers.add_parser("status", help="Show the current default project.")

    ns = parser.parse_args(argv)
    if ns.action == "use":
        name = normalize_project_name(ns.name)
        if not name:
            print("hao project: project name cannot be empty", file=sys.stderr)
            return 2
        save_default_project(name)
        print(f"Default project: {name}")
        print("Future hao runs will be grouped there. Use --no-project for one run outside projects.")
        return 0
    if ns.action == "clear":
        save_default_project("")
        print("Default project cleared. Future hao runs will not be grouped by project.")
        return 0

    configured_project = configured_default_project()
    project = default_project()
    if project:
        source = "configured" if configured_project else "git"
        print(f"Default project: {project} ({source})")
        print("One-off override: hao --project other <command>")
        print("One-off outside projects: hao --no-project <command>")
    else:
        print("Default project: none")
        print("Set one with: hao project use NAME")
    return 0


def parse_run_args(argv: Sequence[str]) -> tuple[list[str], str | None]:
    args = list(argv)
    project_override: str | None = None
    command: list[str] = []
    i = 0
    while i < len(args):
        item = args[i]
        if item == "--":
            command = args[i + 1 :]
            break
        if item in {"--project", "-p"}:
            if i + 1 >= len(args):
                print("hao: --project requires a name", file=sys.stderr)
                return [], None
            project_override = normalize_project_name(args[i + 1])
            i += 2
            continue
        if item.startswith("--project="):
            project_override = normalize_project_name(item.split("=", 1)[1])
            i += 1
            continue
        if item == "--no-project":
            project_override = ""
            i += 1
            continue
        command = args[i:]
        break
    return command, project_override


def normalize_project_name(value: str | None) -> str:
    return (value or "").strip()[:80]


def configured_default_project() -> str:
    config_path = default_config_path()
    try:
        data = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return ""
    if not isinstance(data, dict):
        return ""
    projects = data.get("projects")
    if not isinstance(projects, dict):
        return ""
    return normalize_project_name(str(projects.get("default") or ""))


def default_project(cwd: str | Path | None = None) -> str:
    configured = configured_default_project()
    if configured:
        return configured
    return auto_git_project(cwd)


def auto_git_project(cwd: str | Path | None = None) -> str:
    workdir = str(Path(cwd or os.getcwd()).expanduser())
    try:
        proc = subprocess.run(
            ["git", "-C", workdir, "rev-parse", "--show-toplevel"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=2,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return ""
    if proc.returncode != 0:
        return ""
    root = proc.stdout.strip()
    if not root:
        return ""
    return normalize_project_name(Path(root).name)


def save_default_project(project: str) -> None:
    config_path = default_config_path()
    config_path.parent.mkdir(parents=True, exist_ok=True)
    data: dict[str, object] = {}
    if config_path.exists():
        try:
            loaded = json.loads(config_path.read_text(encoding="utf-8"))
            if isinstance(loaded, dict):
                data = loaded
        except (OSError, json.JSONDecodeError):
            data = {}
    projects = data.get("projects")
    if not isinstance(projects, dict):
        projects = {}
    if project:
        projects["default"] = normalize_project_name(project)
    else:
        projects.pop("default", None)
    data["projects"] = projects
    config_path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    try:
        config_path.chmod(0o600)
    except OSError:
        pass


def status_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao status")
    parser.add_argument("--limit", type=int, default=10)
    ns = parser.parse_args(argv)
    runs = RunStore().list_runs(limit=ns.limit)
    if not runs:
        print("No runs yet.")
        return 0
    for run in runs:
        exit_code = "" if run.exit_code is None else f" exit={run.exit_code}"
        project = f" [{run.project}]" if run.project else ""
        print(f"{run.id[:8]} {run.status:9} {run.started_at}{project} {run.commandText}{exit_code}")
    return 0


def cloud_login_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao cloud-login")
    parser.add_argument("--api-url", default=os.environ.get("HAOLEME_CLOUD_URL", DEFAULT_CLOUD_URL))
    parser.add_argument("--account", default=os.environ.get("HAOLEME_ACCOUNT", "default"))
    parser.add_argument("--token", default=os.environ.get("HAOLEME_ACCOUNT_TOKEN", ""))
    parser.add_argument("--skip-check", action="store_true")
    ns = parser.parse_args(argv)

    api_url = ns.api_url.strip().rstrip("/")
    if not api_url:
        print(
            "hao: missing --api-url.\n"
            "Example: hao cloud-login --api-url http://39.96.50.42 --account alice",
            file=sys.stderr,
        )
        return 2

    token = ns.token.strip() or generate_account_token()
    device_name = gethostname() or "好了么 CLI"
    config = CloudConfig(
        api_url=api_url,
        account=ns.account.strip() or "default",
        token=token,
        device_id=generate_device_id(),
        device_name=device_name,
        machine_id=get_or_create_machine_id(),
    )

    if not ns.skip_check:
        try:
            CloudClient(config).health()
        except Exception as exc:
            print(f"hao: cloud health check failed: {exc}", file=sys.stderr)
            print("Use --skip-check if the cloud service is not deployed yet.", file=sys.stderr)
            return 1

    config.save()
    print("好了么 cloud login saved.")
    print(f"Config:  {default_config_path()}")
    print(f"Server:  {config.api_url}")
    print(f"Account: {config.account}")
    print(f"Device:  {config.device_name}")
    print(f"Token:   {config.token}")
    print()
    print("Use the same Server and Token in the Android app.")
    print("Future hao commands will sync to cloud automatically.")
    _started, message = start_heartbeat_daemon()
    print(f"Heartbeat: {message}")
    return 0


def pairing_login_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao login")
    parser.add_argument("--api-url", default=os.environ.get("HAOLEME_CLOUD_URL", DEFAULT_CLOUD_URL))
    parser.add_argument("--device", default=os.environ.get("HAOLEME_DEVICE_NAME", gethostname() or "好了么 CLI"))
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--new-device", action="store_true", help="Ignore the saved device id and pair this machine as a new device.")
    parser.add_argument("--reuse-saved-device", action="store_true", help="Trust and reuse the saved device id, then bind it to this machine.")
    parser.add_argument("--yes", "-y", action="store_true", help="Re-login without prompting when 好了么 is already logged in.")
    ns = parser.parse_args(argv)

    api_url = ns.api_url.strip().rstrip("/")
    client = PairingClient(api_url)
    machine_id = get_or_create_machine_id()
    existing_config = CloudConfig.load()
    if existing_config is not None and not ns.yes and not confirm_relogin(existing_config):
        return 0
    existing_device_id = reusable_login_device_id(existing_config, api_url, machine_id, ns.new_device, ns.reuse_saved_device)
    public_key, private_key = generate_pair_keypair()
    try:
        started = client.start(ns.device, existing_device_id, public_key)
    except Exception as exc:
        print(f"hao: could not start login: {exc}", file=sys.stderr)
        print("If your 好了么 Cloud URL is different, use: hao login --api-url https://your-server.example.com", file=sys.stderr)
        return 1

    code = str(started.get("code", ""))
    pair_token = str(started.get("pairToken", ""))
    if not code or not pair_token:
        print("hao: cloud did not return a pair code", file=sys.stderr)
        return 1

    print("好了么 login")
    print()
    pair_url = build_pair_url(api_url, code)

    print("Open the 好了么 Android app and enter this pair code:")
    print()
    print(f"  {code}")
    print()
    print("Or scan this QR code with the phone camera:")
    print()
    print_qr(pair_url)
    print()
    print(f"Device: {ns.device}")
    if existing_device_id:
        print("Reusing this computer's previous 好了么 device.")
    elif ns.new_device:
        print("Pairing as a new 好了么 device.")
    print("Waiting for pairing...")

    paired = False
    try:
        deadline = time.monotonic() + max(30, ns.timeout)
        while time.monotonic() < deadline:
            time.sleep(2)
            try:
                status = client.status(code, pair_token)
            except Exception:
                continue
            if status.get("status") != "confirmed":
                continue

            token = str(status.get("token", "")).strip()
            account = str(status.get("account", "default")).strip() or "default"
            device_id = str(status.get("deviceId", "")).strip()
            device_name = str(status.get("deviceName", ns.device)).strip() or ns.device
            encrypted_account_key = str(status.get("encryptedAccountKey", "")).strip()
            encryption_key = ""
            if encrypted_account_key:
                try:
                    encryption_key = decrypt_account_key(encrypted_account_key, private_key)
                except Exception as exc:
                    print(f"hao: could not decrypt 好了么 encryption key: {exc}", file=sys.stderr)
                    return 1
            if not token:
                print("hao: cloud returned an empty account token", file=sys.stderr)
                return 1

            config = CloudConfig(
                api_url=api_url,
                account=account,
                token=token,
                device_id=device_id,
                device_name=device_name,
                machine_id=machine_id,
                encryption_key=encryption_key,
            )
            config.save()
            paired = True
            print()
            print("Login success.")
            print(f"Config: {default_config_path()}")
            print(f"Device: {device_name}")
            print("Encryption: enabled" if encryption_key else "Encryption: not enabled for this pairing")
            print("Future hao commands will sync to 好了么 Cloud automatically.")
            _started, message = start_heartbeat_daemon()
            print(f"Heartbeat: {message}")
            return 0
    except KeyboardInterrupt:
        print("\nhao: login cancelled.", file=sys.stderr)
        return 130
    finally:
        if not paired:
            try:
                client.cancel(code, pair_token)
            except Exception:
                pass

    print("hao: login timed out. Pair code cancelled. Run hao login again to get a new code.", file=sys.stderr)
    return 1


def reusable_login_device_id(
    existing_config: CloudConfig | None,
    api_url: str,
    machine_id: str,
    force_new: bool = False,
    force_reuse: bool = False,
) -> str:
    if force_new or existing_config is None:
        return ""
    if existing_config.api_url.rstrip("/") != api_url.rstrip("/"):
        return ""
    if force_reuse:
        return existing_config.device_id
    if existing_config.machine_id and existing_config.machine_id == machine_id:
        return existing_config.device_id
    return ""


def confirm_relogin(existing_config: CloudConfig) -> bool:
    print("好了么 is already logged in.")
    print("Server: hidden")
    print(f"Account: {existing_config.account or 'default'}")
    if existing_config.device_name or existing_config.device_id:
        print(f"Device: {existing_config.device_name or existing_config.device_id}")
    try:
        answer = input("Press Enter to re-login, or type n to cancel: ")
    except EOFError:
        print("hao: login cancelled. Use hao login --yes to re-login without a prompt.", file=sys.stderr)
        return False
    return should_continue_relogin(answer)


def should_continue_relogin(answer: str) -> bool:
    value = answer.strip().lower()
    return value not in {"n", "no", "q", "quit", "cancel"}


def heartbeat_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao heartbeat")
    parser.add_argument("action", nargs="?", choices=["start", "stop", "status", "run"], default="status")
    ns = parser.parse_args(argv)

    if ns.action == "start":
        started, message = start_heartbeat_daemon()
        print(f"Heartbeat: {message}")
        return 0 if started else 1
    if ns.action == "stop":
        stopped, message = stop_heartbeat_daemon()
        print(f"Heartbeat: {message}")
        return 0 if stopped else 1
    if ns.action == "run":
        return heartbeat_run_foreground()

    pid = read_heartbeat_pid()
    if pid and is_process_running(pid):
        print(f"Heartbeat: running (pid {pid})")
        print(f"Interval:  {HEARTBEAT_INTERVAL_SECONDS}s")
        print(f"Log:       {heartbeat_log_path()}")
        state = read_heartbeat_state()
        if state:
            print(f"Last OK:   {state.get('lastOkAt') or 'never'}")
            if state.get("lastSyncAt"):
                print(f"Last sync: {state.get('lastSyncAt')} ({state.get('lastSyncedRuns', 0)} run(s))")
            if state.get("lastError"):
                print(f"Last err:  {state.get('lastError')}")
        return 0
    print("Heartbeat: stopped")
    state = read_heartbeat_state()
    if state:
        print(f"Last OK:   {state.get('lastOkAt') or 'never'}")
        if state.get("lastError"):
            print(f"Last err:  {state.get('lastError')}")
    print("Run: hao heartbeat start")
    return 1


def heartbeat_pid_path() -> Path:
    return default_config_path().with_name("heartbeat.pid")


def heartbeat_log_path() -> Path:
    return default_config_path().with_name("heartbeat.log")


def heartbeat_state_path() -> Path:
    return default_config_path().with_name("heartbeat.json")


def read_heartbeat_pid() -> int | None:
    try:
        raw = heartbeat_pid_path().read_text(encoding="utf-8").strip()
        return int(raw) if raw else None
    except (OSError, ValueError):
        return None


def is_process_running(pid: int) -> bool:
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def read_heartbeat_state() -> dict[str, object]:
    try:
        raw = heartbeat_state_path().read_text(encoding="utf-8")
        data = json.loads(raw)
        return data if isinstance(data, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def write_heartbeat_state(**fields: object) -> None:
    state = read_heartbeat_state()
    state.update(fields)
    state["updatedAt"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    path = heartbeat_state_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    if os.name == "posix":
        try:
            path.chmod(0o600)
        except OSError:
            pass


def start_heartbeat_daemon() -> tuple[bool, str]:
    config = CloudConfig.load()
    if config is None:
        return False, "not configured (run hao login)"

    pid_path = heartbeat_pid_path()
    pid = read_heartbeat_pid()
    if pid and is_process_running(pid):
        return True, f"already running (pid {pid})"

    pid_path.parent.mkdir(parents=True, exist_ok=True)
    pid_path.unlink(missing_ok=True)
    log_path = heartbeat_log_path()
    with log_path.open("a", encoding="utf-8") as log_file:
        proc = subprocess.Popen(
            [sys.executable, "-m", "haoleme", "heartbeat", "run"],
            stdin=subprocess.DEVNULL,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
    pid_path.write_text(f"{proc.pid}\n", encoding="utf-8")
    return True, f"started (pid {proc.pid})"


def stop_heartbeat_daemon() -> tuple[bool, str]:
    pid_path = heartbeat_pid_path()
    pid = read_heartbeat_pid()
    if not pid or not is_process_running(pid):
        pid_path.unlink(missing_ok=True)
        return False, "not running"
    try:
        os.kill(pid, signal.SIGTERM)
    except OSError as exc:
        return False, f"could not stop pid {pid}: {exc}"
    pid_path.unlink(missing_ok=True)
    return True, f"stopped (pid {pid})"


def heartbeat_initial_delay(config: CloudConfig) -> int:
    seed = config.device_id or config.machine_id or config.device_name or gethostname() or "haoleme"
    digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % HEARTBEAT_INTERVAL_SECONDS


def heartbeat_run_foreground() -> int:
    config = CloudConfig.load()
    if config is None:
        print("hao: cloud is not configured. Run hao login.", file=sys.stderr)
        return 1

    delay = heartbeat_initial_delay(config)
    print(f"好了么 heartbeat started. First heartbeat in {delay}s, then every {HEARTBEAT_INTERVAL_SECONDS}s.", flush=True)
    try:
        time.sleep(delay)
        while True:
            config = CloudConfig.load()
            if config is None:
                print("好了么 heartbeat paused: cloud config removed.", flush=True)
                time.sleep(HEARTBEAT_INTERVAL_SECONDS)
                continue
            try:
                client = CloudClient(config, timeout=8.0)
                store = RunStore()
                recovered = reconcile_orphaned_running_runs(store, client)
                if recovered:
                    print(f"Recovered {recovered} orphaned run(s).", flush=True)
                synced = sync_pending_runs(store, client, limit=100)
                if synced:
                    print(f"Synced {synced} pending run(s).", flush=True)
                    write_heartbeat_state(lastSyncAt=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), lastSyncedRuns=synced)
                client.heartbeat()
                now_text = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
                write_heartbeat_state(lastOkAt=now_text, lastError="", pendingRuns=store.count_unsynced_runs())
                print(f"Heartbeat ok: {time.strftime('%Y-%m-%d %H:%M:%S')}", flush=True)
            except Exception as exc:
                error = describe_cloud_error(exc)
                write_heartbeat_state(lastError=error)
                print(f"Heartbeat failed: {error}", flush=True)
            time.sleep(HEARTBEAT_INTERVAL_SECONDS)
    except KeyboardInterrupt:
        print("好了么 heartbeat stopped.", flush=True)
        return 130


def reconcile_orphaned_running_runs(
    store: RunStore,
    client: CloudClient | None,
    process_running: Callable[[int], bool] = is_process_running,
    now_timestamp: float | None = None,
) -> int:
    now_value = time.time() if now_timestamp is None else now_timestamp
    recovered = 0
    note = "\n[好了么] Command process is no longer running. Marked as cancelled by heartbeat.\n"
    for run in store.list_active_runs(limit=100):
        if run_age_seconds(run.updated_at, now_value) < ORPHANED_RUN_GRACE_SECONDS:
            continue
        if run.pid is not None and process_running(run.pid):
            continue
        store.cancel_run(run.id, note)
        recovered += 1
        updated = store.get_run(run.id)
        if client is not None and updated is not None:
            client.upsert_run(updated)
    return recovered


def sync_pending_runs(store: RunStore, client: CloudClient, limit: int = 100) -> int:
    synced = 0
    for run in store.list_unsynced_runs(limit=limit):
        client.upsert_run(run)
        store.mark_cloud_synced(run.id)
        synced += 1
    return synced


def run_age_seconds(updated_at: str, now_timestamp: float | None = None) -> float:
    try:
        updated_timestamp = datetime_from_iso(updated_at).timestamp()
    except ValueError:
        return ORPHANED_RUN_GRACE_SECONDS
    return (time.time() if now_timestamp is None else now_timestamp) - updated_timestamp


def datetime_from_iso(value: str):
    from datetime import datetime

    return datetime.fromisoformat(str(value).replace("Z", "+00:00"))


def build_pair_url(api_url: str, code: str) -> str:
    query = urllib.parse.urlencode({"server": api_url.rstrip("/"), "code": code})
    return f"haoleme://pair?{query}"


def print_qr(text: str) -> None:
    try:
        import qrcode
    except Exception:
        print("(QR unavailable. Use the 6-digit pair code in the Android app.)")
        return
    qr = qrcode.QRCode(border=2)
    qr.add_data(text)
    qr.make(fit=True)
    matrix = qr.get_matrix()
    white = "\033[47m  \033[0m"
    black = "\033[40m  \033[0m"
    for row in matrix:
        print("".join(black if cell else white for cell in row))


def cloud_logout_command(_argv: Sequence[str]) -> int:
    config_path = default_config_path()
    if not config_path.exists():
        print("好了么 cloud is not configured.")
        return 0
    try:
        import json

        data = json.loads(config_path.read_text(encoding="utf-8"))
        if isinstance(data, dict) and "cloud" in data:
            data.pop("cloud", None)
            config_path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    except Exception:
        config_path.unlink(missing_ok=True)
    stop_heartbeat_daemon()
    print("好了么 cloud login removed.")
    return 0


def cloud_status_command(_argv: Sequence[str]) -> int:
    config = CloudConfig.load()
    if config is None:
        print("Cloud: not configured")
        print("Run: hao login")
        return 1
    print("Cloud: configured")
    print("Server:  hidden")
    print(f"Account: {config.account}")
    try:
        CloudClient(config).health()
        print("Health:  ok")
        return 0
    except Exception as exc:
        print(f"Health:  failed ({describe_cloud_error(exc)})")
        return 1


def sync_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao sync")
    parser.add_argument("--limit", type=int, default=500, help="Maximum pending runs to upload.")
    ns = parser.parse_args(argv)

    config = CloudConfig.load()
    if config is None:
        print("hao sync: cloud is not configured. Run: hao login", file=sys.stderr)
        return 1

    store = RunStore()
    pending = store.count_unsynced_runs()
    if pending == 0:
        print("No pending runs to sync.")
        return 0

    client = CloudClient(config, timeout=10.0)
    try:
        client.health()
        synced = sync_pending_runs(store, client, limit=max(ns.limit, 1))
    except Exception as exc:
        remaining = store.count_unsynced_runs()
        uploaded = max(pending - remaining, 0)
        if uploaded:
            print(f"Synced {uploaded} run(s); {remaining} still pending.")
        print(f"hao sync failed: {describe_cloud_error(exc)}", file=sys.stderr)
        return 1

    remaining = store.count_unsynced_runs()
    print(f"Synced {synced} run(s).")
    if remaining:
        print(f"{remaining} run(s) still pending. Run `hao sync --limit {max(ns.limit, 1)}` again to continue.")
    return 0


def doctor_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao doctor")
    parser.add_argument("--no-network", action="store_true", help="Skip cloud health and pending sync checks.")
    ns = parser.parse_args(argv)

    failures = 0
    warnings = 0

    def report(label: str, status: str, detail: str = "") -> None:
        nonlocal failures, warnings
        if status == "FAIL":
            failures += 1
        elif status == "WARN":
            warnings += 1
        suffix = f" - {detail}" if detail else ""
        print(f"{status:4} {label}{suffix}")

    print("好了么 doctor")
    print(f"Version: {__version__}")
    print(f"Python:  {sys.version.split()[0]}")
    print(f"Config:  {default_config_path()}")
    print(f"DB:      {default_db_path()}")
    print()

    config_path = default_config_path()
    if config_path.exists():
        report("config file", "OK")
        mode = config_path.stat().st_mode & 0o777
        if os.name == "posix" and mode & 0o077:
            report("config permissions", "WARN", f"{oct(mode)}; run: chmod 600 {config_path}")
        else:
            report("config permissions", "OK")
    else:
        report("config file", "WARN", "not found; run: hao login")

    config = CloudConfig.load()
    if config is None:
        report("cloud login", "FAIL", "not configured; run: hao login")
    else:
        device = config.device_name or config.device_id or "unknown"
        report("cloud login", "OK", f"account={config.account or 'default'} device={device}")
        report("encryption", "OK" if config.encryption_key else "WARN", "enabled" if config.encryption_key else "not enabled; re-pair from the app to enable E2EE")

    pid = read_heartbeat_pid()
    if pid and is_process_running(pid):
        report("heartbeat", "OK", f"pid {pid}")
    else:
        report("heartbeat", "WARN", "stopped; run: hao heartbeat start")
    heartbeat_state = read_heartbeat_state()
    if heartbeat_state.get("lastOkAt"):
        detail = str(heartbeat_state.get("lastOkAt"))
        pending_state = heartbeat_state.get("pendingRuns")
        if pending_state is not None:
            detail += f"; pending={pending_state}"
        report("heartbeat last ok", "OK", detail)
    elif heartbeat_state.get("lastError"):
        report("heartbeat last ok", "WARN", f"never; last error={heartbeat_state.get('lastError')}")

    store = RunStore()
    pending = store.count_unsynced_runs()
    if pending:
        report("pending cloud sync", "WARN", f"{pending} run(s) waiting")
    else:
        report("pending cloud sync", "OK")

    configured_project = configured_default_project()
    git_project = auto_git_project()
    if configured_project:
        report("project", "OK", f"{configured_project} (configured)")
    elif git_project:
        report("project", "OK", f"{git_project} (git auto)")
    else:
        report("project", "WARN", "none; run inside a git repo or use: hao project use NAME")

    if ns.no_network:
        report("cloud health", "WARN", "skipped")
    elif config is not None:
        try:
            client = CloudClient(config, timeout=8.0)
            health = client.health()
            report("cloud health", "OK", config.api_url)
            storage = health.get("storage") if isinstance(health, dict) else None
            disk = health.get("disk") if isinstance(health, dict) else None
            if isinstance(storage, dict):
                report("cloud database", "OK" if storage.get("ok") else "FAIL", str(storage.get("error") or "sqlite ok"))
            if isinstance(disk, dict):
                free = int(disk.get("freeBytes") or 0)
                detail = f"{free // (1024 * 1024)} MB free" if free else str(disk.get("error") or "")
                report("cloud disk", "OK" if disk.get("ok") else "WARN", detail)
            synced = sync_pending_runs(store, client, limit=100)
            if synced:
                report("pending sync retry", "OK", f"uploaded {synced} run(s)")
            elif pending:
                report("pending sync retry", "WARN", "nothing uploaded")
        except Exception as exc:
            report("cloud health", "FAIL", describe_cloud_error(exc))

    print()
    if failures:
        print(f"Doctor found {failures} failure(s) and {warnings} warning(s).")
        return 1
    if warnings:
        print(f"Doctor found {warnings} warning(s).")
        return 0
    print("Everything looks good.")
    return 0


def run_command(command: Sequence[str], project_override: str | None = None) -> int:
    if not command:
        print("hao: missing command to run", file=sys.stderr)
        return 2

    start_heartbeat_daemon()
    run_id = str(uuid.uuid4())
    store = RunStore()
    project = default_project() if project_override is None else normalize_project_name(project_override)
    store.create_run(run_id=run_id, command=list(command), cwd=os.getcwd(), project=project)
    syncer = CloudSyncer(store, run_id, configured_cloud_client())
    syncer.request_sync()

    print(f"好了么 run: {run_id}", flush=True)
    print(f"Command: {shlex.join(command)}", flush=True)
    if project:
        print(f"Project: {project}", flush=True)

    executable_command = resolve_local_executable(command)
    if executable_command != list(command):
        print(f"Resolved: {shlex.join(executable_command)}", flush=True)

    try:
        if should_use_pty():
            exit_code = run_command_with_pty(executable_command, store, run_id, syncer)
        else:
            exit_code = run_command_with_pipes(executable_command, store, run_id, syncer)
    except FileNotFoundError:
        store.append_output(run_id, "stderr_tail", f"command not found: {command[0]}\n")
        store.finish_run(run_id, 127)
        syncer.close()
        print(f"hao: command not found: {command[0]}", file=sys.stderr)
        return 127

    store.finish_run(run_id, exit_code)
    syncer.close()
    print(f"好了么 finished: {run_id} exit={exit_code}")
    if syncer.last_error:
        print(f"好了么 cloud sync warning: {syncer.last_error}", file=sys.stderr)
    return exit_code


def resolve_local_executable(command: Sequence[str]) -> list[str]:
    resolved = list(command)
    if not resolved:
        return resolved
    program = resolved[0]
    if os.sep in program or (os.altsep and os.altsep in program):
        return resolved
    if shutil.which(program):
        return resolved
    candidate = Path.cwd() / program
    if candidate.is_file() and os.access(candidate, os.X_OK):
        resolved[0] = str(candidate)
    return resolved


def should_use_pty() -> bool:
    return os.name == "posix" and hasattr(os, "openpty")


def run_command_with_pty(command: Sequence[str], store: RunStore, run_id: str, syncer: CloudSyncer) -> int:
    previous_sighup = ignore_sighup()
    master_fd, slave_fd = os.openpty()
    proc: subprocess.Popen[bytes] | None = None
    try:
        try:
            proc = subprocess.Popen(
                list(command),
                stdin=slave_fd,
                stdout=slave_fd,
                stderr=slave_fd,
                close_fds=True,
            )
        except Exception:
            restore_sighup(previous_sighup)
            raise
    finally:
        os.close(slave_fd)

    store.mark_running(run_id, proc.pid)
    syncer.request_sync()

    def forward_signal(signum: int, _frame: object) -> None:
        if proc.poll() is None:
            proc.send_signal(signum)

    previous_sigint = signal.signal(signal.SIGINT, forward_signal)
    previous_sigterm = signal.signal(signal.SIGTERM, forward_signal)
    stdin_fd = sys.stdin.fileno() if sys.stdin is not None and sys.stdin.isatty() else None
    output = bytearray()

    try:
        while proc.poll() is None:
            read_fds = [master_fd]
            if stdin_fd is not None:
                read_fds.append(stdin_fd)
            ready, _, _ = select.select(read_fds, [], [], 0.2)
            if master_fd in ready:
                chunk = read_pty_chunk(master_fd)
                if chunk:
                    output.extend(chunk)
                    flush_pty_output(output, store, run_id, syncer)
                    write_bytes(sys.stdout, chunk)
            if stdin_fd is not None and stdin_fd in ready:
                try:
                    user_input = os.read(stdin_fd, 4096)
                except OSError:
                    stdin_fd = None
                    user_input = b""
                if user_input:
                    try:
                        os.write(master_fd, user_input)
                    except OSError:
                        stdin_fd = None
                else:
                    stdin_fd = None

        while True:
            chunk = read_pty_chunk(master_fd)
            if not chunk:
                break
            output.extend(chunk)
            flush_pty_output(output, store, run_id, syncer)
            write_bytes(sys.stdout, chunk)
    finally:
        signal.signal(signal.SIGINT, previous_sigint)
        signal.signal(signal.SIGTERM, previous_sigterm)
        restore_sighup(previous_sighup)
        flush_pty_output(output, store, run_id, syncer, force=True)
        os.close(master_fd)

    return proc.wait()


def read_pty_chunk(master_fd: int) -> bytes:
    try:
        return os.read(master_fd, 4096)
    except OSError:
        return b""


def write_bytes(target, data: bytes) -> None:
    try:
        buffer = getattr(target, "buffer", None)
        if buffer is not None:
            buffer.write(data)
            buffer.flush()
            return
        target.write(data.decode(errors="replace"))
        target.flush()
    except (BrokenPipeError, OSError):
        return


def write_text(target, text: str) -> None:
    try:
        target.write(text)
        target.flush()
    except (BrokenPipeError, OSError):
        return


def flush_pty_output(
    output: bytearray,
    store: RunStore,
    run_id: str,
    syncer: CloudSyncer,
    force: bool = False,
) -> None:
    if not output:
        return
    if not force and b"\n" not in output and b"\r" not in output and len(output) < 1024:
        return
    text = output.decode(errors="replace")
    output.clear()
    store.append_output(run_id, "stdout_tail", text)
    syncer.request_sync()


def run_command_with_pipes(command: Sequence[str], store: RunStore, run_id: str, syncer: CloudSyncer) -> int:
    previous_sighup = ignore_sighup()
    try:
        proc = subprocess.Popen(
            list(command),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
    except Exception:
        restore_sighup(previous_sighup)
        raise

    store.mark_running(run_id, proc.pid)
    syncer.request_sync()

    stop_forwarding = threading.Event()

    def forward_signal(signum: int, _frame: object) -> None:
        if proc.poll() is None:
            proc.send_signal(signum)

    previous_sigint = signal.signal(signal.SIGINT, forward_signal)
    previous_sigterm = signal.signal(signal.SIGTERM, forward_signal)

    try:
        threads = [
            threading.Thread(
                target=stream_output,
                args=(proc.stdout, sys.stdout, store, run_id, "stdout_tail", stop_forwarding, syncer.request_sync),
                daemon=True,
            ),
            threading.Thread(
                target=stream_output,
                args=(proc.stderr, sys.stderr, store, run_id, "stderr_tail", stop_forwarding, syncer.request_sync),
                daemon=True,
            ),
        ]
        for thread in threads:
            thread.start()

        exit_code = proc.wait()
        stop_forwarding.set()
        for thread in threads:
            thread.join(timeout=2)
    finally:
        signal.signal(signal.SIGINT, previous_sigint)
        signal.signal(signal.SIGTERM, previous_sigterm)
        restore_sighup(previous_sighup)

    return exit_code


def configured_cloud_client() -> CloudClient | None:
    config = CloudConfig.load()
    if config is None:
        return None
    return CloudClient(config)


def stream_output(
    pipe,
    target,
    store: RunStore,
    run_id: str,
    stream_name: str,
    stop: threading.Event,
    on_update: Callable[[], None] | None = None,
) -> None:
    if pipe is None:
        return
    while not stop.is_set():
        chunk = pipe.readline()
        if chunk == "":
            break
        store.append_output(run_id, stream_name, chunk)
        if on_update is not None:
            on_update()
        write_text(target, chunk)


def ignore_sighup():
    if not hasattr(signal, "SIGHUP"):
        return None
    return signal.signal(signal.SIGHUP, signal.SIG_IGN)


def restore_sighup(previous_handler) -> None:
    if previous_handler is None or not hasattr(signal, "SIGHUP"):
        return
    signal.signal(signal.SIGHUP, previous_handler)


def print_help() -> None:
    print(
        """好了么 command runner

Usage:
  hao server [--host 0.0.0.0] [--port 8765] [--token TOKEN]
  hao public [--port 8765] [--token TOKEN]
  hao ngrok --domain YOUR_DOMAIN.ngrok-free.dev [--token TOKEN]
  hao login [--api-url URL]
  hao login --new-device
  hao login --reuse-saved-device
  hao heartbeat [start|stop|status]
  hao project use NAME
  hao project clear
  hao project status
  hao cloud-login --api-url URL [--account NAME] [--token TOKEN]
  hao cloud-status
  hao cloud-logout
  hao doctor [--no-network]
  hao sync [--limit 500]
  hao status [--limit 10]
  hao <command> [args...]
  hao [--project NAME|--no-project] <command> [args...]
  hao run [--project NAME|--no-project] -- <command> [args...]

After login or cloud-login, normal hao commands sync status to cloud automatically.
Set a default project once with `hao project use NAME`; otherwise git repo names are used automatically.
Override one run with --project or --no-project.
"""
    )
