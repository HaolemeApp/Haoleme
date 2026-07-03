from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import select
import re
import secrets
import signal
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import uuid
from collections.abc import Callable, Sequence
from pathlib import Path
from socket import gethostname

from . import __version__
from ._compat import remove_prefix, shlex_join, unlink_missing
from .cloud import DEFAULT_CLOUD_URL, CloudClient, CloudConfig, CloudSyncer, InterruptWatcher, PairingClient, default_config_path, describe_cloud_error, generate_account_token, generate_device_id, get_or_create_machine_id
from .crypto import decrypt_account_key, generate_pair_keypair
from .server import serve
from .store import RunRecord, RunStore, default_db_path


RESERVED_COMMANDS = {
    "server",
    "status",
    "public",
    "ngrok",
    "login",
    "heartbeat",
    "cloud-login",
    "cloud-logout",
    "cloud-status",
    "project",
    "doctor",
    "sync",
    "update",
}
PUBLIC_URL_RE = re.compile(r"https://[a-zA-Z0-9.-]+\.trycloudflare\.com")
HEARTBEAT_INTERVAL_SECONDS = 45
HEARTBEAT_STAGGER_SECONDS = 20
HEARTBEAT_ACTIVE_POLL_SECONDS = 3
ACTIVE_RUN_RESYNC_SECONDS = 30
GITHUB_UPDATE_JSON_URL = "https://raw.githubusercontent.com/HaolemeApp/Haoleme/main/update.json"
ORPHANED_RUN_GRACE_SECONDS = 30
INTERRUPT_NOTE = "\n[好了么] Interrupted from mobile app.\n"


def main(argv: Sequence[str] | None = None) -> int:
    raw_args = list(sys.argv[1:] if argv is None else argv)
    if not raw_args:
        print_help()
        return 2

    # Extract any leading project flags so that
    # "hao --project foo status" still dispatches to status subcommand
    # (instead of treating "status" as a command to run).
    project_from_leading, args = _extract_leading_project(raw_args)

    if not args:
        print_help()
        return 2

    first = args[0]
    if first in {"-h", "--help"}:
        print_help()
        return 0
    if first in {"-v", "-V", "--version"}:
        return version_command(args[1:])
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
    if first == "update":
        return update_command(args[1:])
    if first == "devices":
        return devices_command(args[1:])
    if first == "clear":
        return clear_command(args[1:])
    if first == "cancel":
        return cancel_command(args[1:])

    # Implicit run: use leading project if present, else let parse_run_args handle
    # (parse_run_args supports the old style with flags before the command too)
    if project_from_leading is not None:
        command = args[:]
        project_override = project_from_leading
    else:
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
    public_url = f"https://{remove_prefix(remove_prefix(ns.domain, 'https://'), 'http://').rstrip('/')}"

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
        [ngrok, "http", str(ns.port), "--url", remove_prefix(remove_prefix(ns.domain, "https://"), "http://").rstrip("/")],
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


def _extract_leading_project(args: Sequence[str]) -> tuple[str | None, list[str]]:
    """Strip leading --project / --no-project flags (for use before subcommand dispatch).
    Returns (project_override or None, remaining_args)
    """
    args = list(args)
    project_override: str | None = None
    i = 0
    while i < len(args):
        item = args[i]
        if item in {"--project", "-p"}:
            if i + 1 >= len(args):
                print("hao: --project requires a name", file=sys.stderr)
                return None, args
            project_override = normalize_project_name(args[i + 1])
            del args[i:i+2]
            continue
        if item.startswith("--project="):
            project_override = normalize_project_name(item.split("=", 1)[1])
            del args[i]
            continue
        if item == "--no-project":
            project_override = ""
            del args[i]
            continue
        break
    return project_override, args


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
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--project", default="", help="Filter by project")
    parser.add_argument("--status", default="", help="Filter by status (running|created|succeeded|failed|cancelled)")
    parser.add_argument("--active", action="store_true", help="Only show non-terminal runs")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    ns = parser.parse_args(argv)

    store = RunStore()
    runs = store.list_runs(limit=max(ns.limit, 1))

    def matches(r: RunRecord) -> bool:
        if ns.project and r.project != ns.project:
            return False
        if ns.active and r.status not in ("created", "running"):
            return False
        if ns.status:
            s = ns.status.lower()
            if s == "running":
                if r.status not in ("created", "running"):
                    return False
            elif r.status != s:
                return False
        return True

    filtered = [r for r in runs if matches(r)]

    if ns.json:
        import json as _json
        data = [r.to_dict() for r in filtered]
        print(_json.dumps({"runs": data}, indent=2, ensure_ascii=False))
        return 0

    if not filtered:
        print("No matching runs.")
        return 0

    print(f"Showing {len(filtered)} run(s):")
    for run in filtered:
        ec = "" if run.exit_code is None else f" exit={run.exit_code}"
        proj = f" [{run.project}]" if run.project else ""
        # simple duration
        dur = ""
        try:
            from datetime import datetime, timezone
            start = datetime.fromisoformat(run.started_at.replace("Z", "+00:00"))
            if run.ended_at:
                end = datetime.fromisoformat(run.ended_at.replace("Z", "+00:00"))
            else:
                end = datetime.now(timezone.utc)
            secs = max(0, int((end - start).total_seconds()))
            dur = f" {secs}s"
        except Exception:
            pass
        last_out = ""
        tail = (run.output_tail or run.stderr_tail or run.stdout_tail or "").strip()
        if tail:
            last_out = " | " + (tail.splitlines()[-1][:70] if tail else "")
        sync_mark = "" if run.cloud_synced_at else " (pending sync)"
        print(f"{run.id[:8]} {run.status:9}{dur}{proj} {run.commandText}{ec}{last_out}{sync_mark}")
    return 0


def devices_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao devices")
    subparsers = parser.add_subparsers(dest="action")
    subparsers.add_parser("list", help="List paired devices and their status.")
    rename_p = subparsers.add_parser("rename", help="Rename a device.")
    rename_p.add_argument("device_id", help="Device ID (or use current if 'self' or omitted in some flows)")
    rename_p.add_argument("name", help="New display name")
    revoke_p = subparsers.add_parser("revoke", help="Revoke (disconnect) a device. This device will stop syncing.")
    revoke_p.add_argument("device_id", help="Device ID to revoke")
    ns = parser.parse_args(argv)

    config = CloudConfig.load()
    if config is None:
        print("hao devices: cloud is not configured. Run: hao login or hao cloud-login", file=sys.stderr)
        return 1

    client = CloudClient(config, timeout=10.0)

    action = ns.action or "list"
    if action == "list":
        try:
            devices = client.list_devices()
        except Exception as exc:
            print(f"hao devices list failed: {describe_cloud_error(exc)}", file=sys.stderr)
            return 1
        if not devices:
            print("No devices.")
            return 0
        print("Devices:")
        for d in devices:
            did = d.get("id", "")
            name = d.get("name", did)
            online = "online" if d.get("online") else "offline"
            last = d.get("lastSeenAt", "") or d.get("tokenLastUsedAt", "")
            gpus = d.get("gpus") or []
            gpu_info = f" GPUs={len(gpus)}" if gpus else ""
            print(f"  {did}  {name}  [{online}]{gpu_info}  last={last}")
        return 0

    if action == "rename":
        did = ns.device_id
        if did in ("self", ".", ""):
            did = config.device_id or ""
        if not did:
            print("hao devices rename: device_id required (or configure one)", file=sys.stderr)
            return 2
        name = ns.name.strip()
        if not name:
            print("hao devices rename: name required", file=sys.stderr)
            return 2
        try:
            dev = client.rename_device(did, name)
            print(f"Renamed device {did} -> {dev.get('name', name)}")
            return 0
        except Exception as exc:
            print(f"hao devices rename failed: {describe_cloud_error(exc)}", file=sys.stderr)
            return 1

    if action == "revoke":
        did = ns.device_id
        if not did:
            print("hao devices revoke: device_id required", file=sys.stderr)
            return 2
        if did == config.device_id:
            print("Warning: revoking the current device. You may need to re-login.", file=sys.stderr)
        try:
            ok = client.revoke_device(did)
            if ok:
                print(f"Revoked device {did}")
                return 0
            else:
                print(f"Revoke may have failed for {did}")
                return 1
        except Exception as exc:
            print(f"hao devices revoke failed: {describe_cloud_error(exc)}", file=sys.stderr)
            return 1

    print("Unknown devices action. Use: list | rename | revoke")
    return 2


def clear_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao clear")
    parser.add_argument("--all", action="store_true", help="Delete all local runs (including running).")
    parser.add_argument("--completed", action="store_true", help="Delete only terminal runs (succeeded/failed/cancelled).")
    parser.add_argument("--project", default="", help="Only runs in this project.")
    parser.add_argument("--older-than", type=int, default=0, help="Delete runs older than N days (based on started_at).")
    parser.add_argument("--yes", "-y", action="store_true", help="Do not prompt for confirmation.")
    parser.add_argument("--cloud", action="store_true", help="Also clear runs from the cloud server (irreversible).")
    ns = parser.parse_args(argv)

    store = RunStore()
    all_runs = store.list_runs(limit=10_000)

    def is_old(r: RunRecord) -> bool:
        if ns.older_than <= 0:
            return True
        try:
            from datetime import datetime, timezone, timedelta
            dt = datetime.fromisoformat(r.started_at.replace("Z", "+00:00"))
            cutoff = datetime.now(timezone.utc) - timedelta(days=ns.older_than)
            return dt < cutoff
        except Exception:
            return False

    to_delete = []
    for r in all_runs:
        if ns.project and r.project != ns.project:
            continue
        if not is_old(r):
            continue
        status = r.status
        if ns.all:
            to_delete.append(r)
        elif ns.completed:
            if status in ("succeeded", "failed", "cancelled"):
                to_delete.append(r)
        else:
            # default: clear completed
            if status in ("succeeded", "failed", "cancelled"):
                to_delete.append(r)

    if not to_delete:
        print("No matching local runs to clear.")
        # still allow cloud clear below
    else:
        print(f"Will delete {len(to_delete)} local run(s).")
        for r in to_delete[:5]:
            print(f"  {r.id[:8]} {r.status} {r.project or ''} {r.commandText[:60]}")
        if len(to_delete) > 5:
            print(f"  ... and {len(to_delete)-5} more")

    if not ns.yes and to_delete:
        ans = input("Proceed with local clear? [y/N] ").strip().lower()
        if ans not in ("y", "yes"):
            print("Aborted local clear.")
            # still consider cloud
            to_delete = []

    deleted_local = 0
    for r in to_delete:
        if store.delete_run(r.id):
            deleted_local += 1
    if deleted_local:
        print(f"Deleted {deleted_local} local run(s).")

    if ns.cloud:
        config = CloudConfig.load()
        if config is None:
            print("No cloud configured; skipping cloud clear. (Run hao login)", file=sys.stderr)
        else:
            if not ns.yes:
                ans = input("Also CLEAR ALL runs on the CLOUD? This is irreversible. [y/N] ").strip().lower()
                if ans not in ("y", "yes"):
                    print("Skipping cloud clear.")
                else:
                    client = CloudClient(config, timeout=15.0)
                    try:
                        cnt = client.clear_all_runs()
                        print(f"Cloud clear requested (deleted ~{cnt if cnt >= 0 else 'unknown'}).")
                    except Exception as exc:
                        print(f"Cloud clear failed: {describe_cloud_error(exc)}", file=sys.stderr)
                        return 1
    return 0


def cancel_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao cancel")
    parser.add_argument("run_id", help="Run ID (full or prefix)")
    parser.add_argument("--force", action="store_true", help="Kill even if not found locally (try cloud interrupt).")
    ns = parser.parse_args(argv)

    rid = ns.run_id.strip()
    store = RunStore()
    run = None
    for r in store.list_runs(limit=500):
        if r.id == rid or r.id.startswith(rid):
            run = r
            break

    client = None
    config = CloudConfig.load()
    if config:
        client = CloudClient(config, timeout=10.0)

    if run is None:
        if ns.force and client:
            print(f"Run {rid} not in local DB; requesting interrupt on cloud...")
            try:
                client.request_interrupt(rid)
                print("Interrupt requested via cloud.")
                return 0
            except Exception as exc:
                print(f"Interrupt request failed: {describe_cloud_error(exc)}", file=sys.stderr)
                return 1
        print(f"Run not found locally: {rid}. Use --force to attempt cloud interrupt.", file=sys.stderr)
        return 2

    print(f"Cancelling: {run.id[:8]} {run.status} {run.commandText[:50]}")

    interrupted = False
    if run.pid and is_process_running(run.pid):
        print(f"Killing PID {run.pid} ...")
        kill_process_tree(run.pid)
        interrupted = True

    note = "\n[hao] Cancelled from CLI.\n"
    store.cancel_run(run.id, note)

    if client:
        try:
            # Prefer the interrupt endpoint (will be picked by heartbeat if active)
            resp = client.request_interrupt(run.id)
            print(f"Cloud interrupt requested: {resp}")
        except Exception as exc:
            # Fallback: push the cancelled state directly
            print(f"Direct interrupt failed ({exc}); pushing cancelled state...")
            try:
                updated = store.get_run(run.id)
                if updated:
                    client.upsert_run(updated)
            except Exception as e2:
                print(f"Cloud sync warning: {describe_cloud_error(e2)}", file=sys.stderr)

    print(f"Marked cancelled locally: {run.id[:8]}")
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
            "Example: hao cloud-login --api-url https://api.haoleme.cloud --account alice",
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
        started = client.start(ns.device, existing_device_id, public_key, machine_id)
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
    if os.name == "nt":
        return is_windows_process_running(pid)
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    except OSError:
        return False
    return True


def is_windows_process_running(pid: int) -> bool:
    try:
        result = subprocess.run(
            ["tasklist", "/FI", f"PID eq {pid}", "/FO", "CSV", "/NH"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=3,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False

    for row in csv.reader(result.stdout.splitlines()):
        if len(row) >= 2 and row[1].strip() == str(pid):
            return True
    return False


def terminate_windows_process(pid: int) -> bool:
    try:
        result = subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    return result.returncode == 0 or not is_windows_process_running(pid)


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
        state = read_heartbeat_state()
        if state.get("haolemeVersion") != __version__:
            stop_heartbeat_daemon()
        else:
            return True, f"already running (pid {pid})"

    pid_path.parent.mkdir(parents=True, exist_ok=True)
    unlink_missing(pid_path)
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
        unlink_missing(pid_path)
        return False, "not running"
    if os.name == "nt":
        if not terminate_windows_process(pid):
            return False, f"could not stop pid {pid}"
        unlink_missing(pid_path)
        return True, f"stopped (pid {pid})"
    try:
        os.kill(pid, signal.SIGTERM)
    except OSError as exc:
        return False, f"could not stop pid {pid}: {exc}"
    unlink_missing(pid_path)
    return True, f"stopped (pid {pid})"


def heartbeat_initial_delay(config: CloudConfig) -> int:
    seed = config.device_id or config.machine_id or config.device_name or gethostname() or "haoleme"
    digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % HEARTBEAT_STAGGER_SECONDS


def _parse_int(value: str):
    value = value.strip()
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def collect_gpu_stats() -> list:
    """Snapshot NVIDIA GPU utilization via nvidia-smi. Empty list if unavailable."""
    nvidia_smi = shutil.which("nvidia-smi")
    if not nvidia_smi:
        return []
    try:
        proc = subprocess.run(
            [
                nvidia_smi,
                "--query-gpu=index,name,utilization.gpu,memory.used,memory.total,temperature.gpu",
                "--format=csv,noheader,nounits",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=5,
        )
    except Exception:
        return []
    if proc.returncode != 0:
        return []
    gpus = []
    for line in proc.stdout.decode("utf-8", errors="replace").strip().splitlines():
        parts = [p.strip() for p in line.split(",")]
        if len(parts) < 6:
            continue
        gpus.append({
            "index": _parse_int(parts[0]),
            "name": parts[1][:80],
            "utilization": _parse_int(parts[2]),
            "memoryUsed": _parse_int(parts[3]),
            "memoryTotal": _parse_int(parts[4]),
            "temperature": _parse_int(parts[5]),
        })
    return gpus


def _linux_cpu_totals() -> tuple[int, int] | None:
    try:
        with open("/proc/stat", "r", encoding="utf-8") as fh:
            first = fh.readline().strip().split()
    except Exception:
        return None
    if not first or first[0] != "cpu":
        return None
    values = []
    for part in first[1:]:
        parsed = _parse_int(part)
        if parsed is not None:
            values.append(parsed)
    if len(values) < 4:
        return None
    idle = values[3] + (values[4] if len(values) > 4 else 0)
    total = sum(values)
    return total, idle


def collect_cpu_stats() -> dict:
    """Snapshot host CPU utilization. Best effort and dependency-free."""
    cores = os.cpu_count() or 1
    utilization = None
    first = _linux_cpu_totals()
    if first is not None:
        time.sleep(0.08)
        second = _linux_cpu_totals()
        if second is not None:
            total_delta = second[0] - first[0]
            idle_delta = second[1] - first[1]
            if total_delta > 0:
                utilization = round(max(0.0, min(100.0, (1.0 - idle_delta / total_delta) * 100.0)))
    load1 = None
    try:
        load1 = os.getloadavg()[0]
    except (AttributeError, OSError):
        load1 = None
    if utilization is None and load1 is not None:
        utilization = round(max(0.0, min(100.0, load1 * 100.0 / max(1, cores))))
    cpu = {"cores": cores}
    if utilization is not None:
        cpu["utilization"] = int(utilization)
    if load1 is not None:
        cpu["load1"] = round(float(load1), 2)
    return cpu


def heartbeat_run_foreground() -> int:
    config = CloudConfig.load()
    if config is None:
        print("hao: cloud is not configured. Run hao login.", file=sys.stderr)
        return 1

    write_heartbeat_state(haolemeVersion=__version__)
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
                interrupted = apply_cloud_interrupts(store, client)
                if interrupted:
                    print(f"Applied {interrupted} cloud interrupt(s).", flush=True)
                recovered = reconcile_orphaned_running_runs(store, client)
                if recovered:
                    print(f"Recovered {recovered} orphaned run(s).", flush=True)
                refreshed = mark_stale_active_runs_pending(store)
                if refreshed:
                    print(f"Queued {refreshed} active run(s) for cloud refresh.", flush=True)
                synced = sync_pending_runs(store, client, limit=100)
                if synced:
                    print(f"Synced {synced} pending run(s).", flush=True)
                    write_heartbeat_state(lastSyncAt=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), lastSyncedRuns=synced)
                client.heartbeat(gpus=collect_gpu_stats(), cpu=collect_cpu_stats())
                now_text = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
                write_heartbeat_state(
                    haolemeVersion=__version__,
                    lastOkAt=now_text,
                    lastError="",
                    pendingRuns=store.count_unsynced_runs(),
                )
                print(f"Heartbeat ok: {time.strftime('%Y-%m-%d %H:%M:%S')}", flush=True)
            except Exception as exc:
                error = describe_cloud_error(exc)
                write_heartbeat_state(lastError=error)
                print(f"Heartbeat failed: {error}", flush=True)
            store = RunStore()
            sleep_seconds = HEARTBEAT_ACTIVE_POLL_SECONDS if store.list_active_runs(limit=1) else HEARTBEAT_INTERVAL_SECONDS
            time.sleep(sleep_seconds)
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


def mark_stale_active_runs_pending(
    store: RunStore,
    *,
    max_age_seconds: int = ACTIVE_RUN_RESYNC_SECONDS,
    now_timestamp: float | None = None,
) -> int:
    marked = 0
    for run in store.list_active_runs(limit=100):
        if not run.cloud_synced_at:
            continue
        if run_age_seconds(run.cloud_synced_at, now_timestamp) < max_age_seconds:
            continue
        store.mark_cloud_pending(run.id)
        marked += 1
    return marked


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
    for line in qr_matrix_to_terminal_lines(qr.get_matrix()):
        print(line)


def qr_matrix_to_terminal_lines(matrix: Sequence[Sequence[bool]]) -> list[str]:
    # Each module is a background-coloured cell. Background colours fill the whole
    # character box, including the inter-line spacing terminals add, so modules tile
    # seamlessly with no scan-breaking gaps (unlike half-block glyphs such as ▀,
    # which only paint the foreground). Terminal cells are about half as wide as
    # they are tall, so two columns per module keeps the rendered code square —
    # essential for reliable scanning.
    dark = "\033[40m  \033[0m"   # black block: QR "dark" module
    light = "\033[47m  \033[0m"  # white block: QR "light" module / quiet zone
    return ["".join(dark if cell else light for cell in row) for row in matrix]


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
        unlink_missing(config_path)
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


def version_tuple(value: str) -> tuple[int, ...]:
    parts: list[int] = []
    for piece in re.split(r"[.+-]", str(value or "").strip()):
        if piece.isdigit():
            parts.append(int(piece))
        elif parts:
            break
    return tuple(parts) if parts else (0,)


def compare_versions(left: str, right: str) -> int:
    left_parts = version_tuple(left)
    right_parts = version_tuple(right)
    width = max(len(left_parts), len(right_parts))
    for index in range(width):
        left_value = left_parts[index] if index < len(left_parts) else 0
        right_value = right_parts[index] if index < len(right_parts) else 0
        if left_value < right_value:
            return -1
        if left_value > right_value:
            return 1
    return 0


def update_json_urls() -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()

    def add(raw: str) -> None:
        candidate = (raw or "").strip()
        if not candidate or candidate in seen:
            return
        seen.add(candidate)
        urls.append(candidate)

    config = CloudConfig.load()
    if config is not None:
        add(config.api_url.rstrip("/") + "/downloads/update.json")
    add(DEFAULT_CLOUD_URL.rstrip("/") + "/downloads/update.json")
    add(os.environ.get("HAOLEME_UPDATE_URL", ""))
    add(GITHUB_UPDATE_JSON_URL)
    return urls


def fetch_update_manifest(timeout: float = 8.0) -> tuple[dict[str, object], str]:
    errors: list[str] = []
    for url in update_json_urls():
        try:
            request = urllib.request.Request(
                url,
                headers={"Accept": "application/json", "User-Agent": f"haoleme/{__version__}"},
                method="GET",
            )
            with urllib.request.urlopen(request, timeout=timeout) as response:
                raw = response.read().decode("utf-8")
            parsed = json.loads(raw)
            if not isinstance(parsed, dict):
                raise ValueError("update manifest must be a JSON object")
            return parsed, url
        except Exception as exc:
            errors.append(f"{url}: {describe_cloud_error(exc)}")
    detail = errors[0] if errors else "no update URL configured"
    raise RuntimeError(detail)


def latest_python_release(manifest: dict[str, object]) -> dict[str, str]:
    python = manifest.get("python")
    version = ""
    package_url = ""
    wheel_url = ""
    if isinstance(python, dict):
        version = str(python.get("version") or "").strip()
        package_url = str(python.get("packageUrl") or "").strip()
        wheel_url = str(python.get("wheelUrl") or "").strip()

    # Fallback / override with actual latest from PyPI to keep "hao --version" accurate
    # even if the project's update.json on server is slightly behind.
    pypi_version = _fetch_pypi_latest_version()
    if pypi_version and (not version or compare_versions(pypi_version, version) > 0):
        version = pypi_version
        if not package_url:
            package_url = "https://pypi.org/project/haoleme/"

    return {
        "version": version,
        "packageUrl": package_url or "https://pypi.org/project/haoleme/",
        "wheelUrl": wheel_url,
    }


def _fetch_pypi_latest_version() -> str:
    try:
        req = urllib.request.Request(
            "https://pypi.org/pypi/haoleme/json",
            headers={"Accept": "application/json", "User-Agent": f"haoleme/{__version__}"},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=6) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        info = data.get("info") or {}
        return str(info.get("version") or "").strip()
    except Exception:
        return ""


def python_wheel_candidates(release: dict[str, str], manifest_source: str) -> list[str]:
    candidates: list[str] = []
    seen: set[str] = set()

    def add(raw: str) -> None:
        candidate = (raw or "").strip()
        if not candidate or candidate in seen:
            return
        seen.add(candidate)
        candidates.append(candidate)

    version = release.get("version", "")
    add(release.get("wheelUrl", ""))
    if version:
        wheel_name = f"haoleme-{version}-py3-none-any.whl"
        parsed = urllib.parse.urlparse(manifest_source)
        if parsed.scheme and parsed.netloc:
            base = f"{parsed.scheme}://{parsed.netloc}"
            add(f"{base}/downloads/{wheel_name}")
        config = CloudConfig.load()
        if config is not None:
            add(config.api_url.rstrip("/") + f"/downloads/{wheel_name}")
        add(DEFAULT_CLOUD_URL.rstrip("/") + f"/downloads/{wheel_name}")
    add(release.get("packageUrl", ""))
    add("haoleme")
    return candidates


def version_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao --version")
    parser.add_argument("--check", action="store_true", help="Exit 1 when a newer release is available.")
    ns = parser.parse_args(argv)

    print(f"haoleme {__version__}")
    print(f"Python  {sys.version.split()[0]}")
    print(f"hao     {shutil.which('hao') or sys.argv[0]}")

    try:
        manifest, source = fetch_update_manifest()
    except Exception as exc:
        print(f"Update check: unavailable ({exc})")
        return 1 if ns.check else 0

    release = latest_python_release(manifest)
    latest = release.get("version", "")
    if not latest:
        print("Update check: manifest has no python.version")
        return 1 if ns.check else 0

    print(f"Latest  {latest} ({source})")
    if compare_versions(__version__, latest) < 0:
        print("Status  update available (run: hao update)")
        return 1 if ns.check else 0
    print("Status  up to date")
    return 0


def update_command(argv: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="hao update")
    parser.add_argument("--check", action="store_true", help="Only report whether an update is available.")
    parser.add_argument("--yes", "-y", action="store_true", help="Install without confirmation.")
    ns = parser.parse_args(argv)

    try:
        manifest, source = fetch_update_manifest()
    except Exception as exc:
        print(f"hao update failed: {exc}", file=sys.stderr)
        return 1

    release = latest_python_release(manifest)
    latest = release.get("version", "")
    if not latest:
        print("hao update failed: manifest has no python.version", file=sys.stderr)
        return 1

    if compare_versions(__version__, latest) >= 0:
        print(f"hao is already up to date ({__version__}).")
        return 0

    print(f"Current: {__version__}")
    print(f"Latest:  {latest}")
    print(f"Source:  {source}")
    if ns.check:
        print("Update available. Run: hao update")
        return 0

    if not ns.yes:
        answer = input(f"Install haoleme {latest}? [Y/n] ").strip().lower()
        if answer in {"n", "no"}:
            print("hao update cancelled.")
            return 1

    candidates = python_wheel_candidates(release, source)
    last_error = ""
    for target in candidates:
        print(f"Trying: {target}")
        try:
            subprocess.run(
                [sys.executable, "-m", "pip", "install", "-U", target],
                check=True,
            )
            break
        except subprocess.CalledProcessError as exc:
            last_error = f"pip exited {exc.returncode}"
    else:
        print(f"hao update failed: {last_error or 'no install target worked'}", file=sys.stderr)
        return 1

    try:
        installed = subprocess.check_output(
            [sys.executable, "-c", "import haoleme; print(haoleme.__version__)"],
            text=True,
        ).strip()
    except Exception:
        installed = latest
    print(f"Installed haoleme {installed}.")
    stop_heartbeat_daemon()
    started, message = start_heartbeat_daemon()
    print(f"Heartbeat: {message}" if started else f"Heartbeat not restarted: {message}")
    if compare_versions(installed, latest) < 0:
        print("hao update warning: installed version still looks older than latest.", file=sys.stderr)
        return 1
    return 0


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

    all_local = store.list_runs(limit=1000)
    active_local = [r for r in all_local if r.status in ("created", "running")]
    report("local runs", "OK", f"{len(all_local)} total, {len(active_local)} active")

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

            # Device summary (bonus for doctor)
            try:
                devs = client.list_devices()
                active_devs = sum(1 for d in devs if d.get("online"))
                report("cloud devices", "OK", f"{len(devs)} total ({active_devs} online)")
            except Exception:
                report("cloud devices", "WARN", "could not list")
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

    shell_command = len(command) == 1 and command_needs_shell(command[0])
    env_overrides: dict[str, str] = {}
    runnable_command = list(command)
    if not shell_command:
        env_overrides, runnable_command = split_leading_env_assignments(command)
        if env_overrides and not runnable_command:
            print("hao: missing command after environment assignment", file=sys.stderr)
            return 2

    start_heartbeat_daemon()
    run_id = str(uuid.uuid4())
    store = RunStore()
    project = default_project() if project_override is None else normalize_project_name(project_override)
    store.create_run(run_id=run_id, command=list(command), cwd=os.getcwd(), project=project)
    syncer = CloudSyncer(store, run_id, configured_cloud_client())
    syncer.request_sync()

    if syncer.client is None:
        print("hao: cloud not configured; mobile interrupt will not work. Run: hao login", file=sys.stderr)

    print(f"好了么 run: {run_id}", flush=True)
    print(f"Command: {shlex_join(command)}", flush=True)
    if project:
        print(f"Project: {project}", flush=True)

    if shell_command:
        executable_command = ["/bin/sh", "-c", command[0]]
    else:
        executable_command = resolve_local_executable(runnable_command)
    if executable_command != list(runnable_command):
        print(f"Resolved: {shlex_join(display_command_with_env(executable_command, env_overrides))}", flush=True)

    env = child_environment(env_overrides)

    interrupt_event = threading.Event()
    watcher = InterruptWatcher(syncer.client, run_id, interrupt_event.set)
    watcher.start()
    try:
        try:
            if should_use_pty():
                exit_code, interrupted = run_command_with_pty(executable_command, store, run_id, syncer, interrupt_event, env)
            else:
                exit_code, interrupted = run_command_with_pipes(executable_command, store, run_id, syncer, interrupt_event, env)
        except FileNotFoundError:
            missing_command = runnable_command[0] if runnable_command else command[0]
            store.append_output(run_id, "stderr_tail", f"command not found: {missing_command}\n")
            store.finish_run(run_id, 127)
            syncer.close()
            print(f"hao: command not found: {missing_command}", file=sys.stderr)
            return 127
    finally:
        watcher.stop()

    if interrupted or watcher.triggered():
        store.interrupt_run(run_id, INTERRUPT_NOTE)
        syncer.request_sync()
        syncer.close()
        print(f"好了么 interrupted: {run_id}")
        if watcher.last_error:
            print(f"好了么 interrupt poll warning: {watcher.last_error}", file=sys.stderr)
        if syncer.last_error:
            print(f"好了么 cloud sync warning: {syncer.last_error}", file=sys.stderr)
        return 130

    store.finish_run(run_id, exit_code)
    syncer.close()
    print(f"好了么 finished: {run_id} exit={exit_code}")
    if syncer.last_error:
        print(f"好了么 cloud sync warning: {syncer.last_error}", file=sys.stderr)
    return exit_code


# Characters that imply the user wants shell interpretation (pipes, redirects,
# logical operators, globs, variable/command substitution, whitespace between
# words). A bare program path never contains these, so a single command token
# that does is treated as a shell command line and run via the shell.
_SHELL_METACHARACTERS = frozenset("|&;<>()$`\\\"'*?[]{}~# \t\n")
_ENV_ASSIGNMENT_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=.*$")


def command_needs_shell(token: str) -> bool:
    return any(ch in _SHELL_METACHARACTERS for ch in token)


def is_env_assignment(token: str) -> bool:
    return bool(_ENV_ASSIGNMENT_RE.match(token))


def split_leading_env_assignments(command: Sequence[str]) -> tuple[dict[str, str], list[str]]:
    env: dict[str, str] = {}
    index = 0
    for token in command:
        if not is_env_assignment(token):
            break
        key, value = token.split("=", 1)
        env[key] = value
        index += 1
    return env, list(command[index:])


def child_environment(overrides: dict[str, str]) -> dict[str, str] | None:
    if not overrides:
        return None
    env = os.environ.copy()
    env.update(overrides)
    return env


def display_command_with_env(command: Sequence[str], env: dict[str, str]) -> list[str]:
    if not env:
        return list(command)
    return [f"{key}={value}" for key, value in env.items()] + list(command)


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


def subprocess_session_kwargs() -> dict[str, object]:
    if os.name == "posix":
        return {"start_new_session": True}
    return {}


def send_signal_to_pid(pid: int, signum: int) -> None:
    if pid <= 0:
        return
    if os.name == "nt":
        terminate_windows_process(pid)
        return
    if os.name == "posix":
        try:
            os.killpg(os.getpgid(pid), signum)
            return
        except (ProcessLookupError, PermissionError, OSError):
            pass
    try:
        os.kill(pid, signum)
    except (ProcessLookupError, PermissionError, OSError):
        return


def send_signal_to_process_tree(proc: subprocess.Popen, signum: int) -> None:
    if proc.poll() is not None:
        return
    send_signal_to_pid(proc.pid, signum)


def kill_process_tree(pid: int) -> None:
    if pid <= 0 or not is_process_running(pid):
        return
    send_signal_to_pid(pid, signal.SIGTERM)
    for _ in range(15):
        if not is_process_running(pid):
            return
        time.sleep(0.2)
    send_signal_to_pid(pid, signal.SIGKILL)


def refresh_interrupt_event(
    run_id: str,
    client: CloudClient | None,
    interrupt_event: threading.Event | None,
) -> None:
    if interrupt_event is None or client is None or interrupt_event.is_set():
        return
    try:
        for item in client.list_pending_interrupts():
            if item.get("id") == run_id and item.get("interruptRequestedAt"):
                interrupt_event.set()
                return
    except Exception:
        return


def apply_cloud_interrupts(store: RunStore, client: CloudClient) -> int:
    applied = 0
    pending_ids = {str(item.get("id") or "") for item in client.list_pending_interrupts() if item.get("id")}
    if not pending_ids:
        return 0
    for run in store.list_active_runs(limit=100):
        if run.id not in pending_ids:
            continue
        if run.pid is not None:
            kill_process_tree(run.pid)
        store.interrupt_run(run.id, INTERRUPT_NOTE)
        updated = store.get_run(run.id)
        if updated is not None:
            client.upsert_run(updated)
        applied += 1
    return applied


def terminate_process_on_interrupt(proc: subprocess.Popen, interrupt_event: threading.Event) -> bool:
    if proc.poll() is not None:
        return interrupt_event.is_set()
    if not interrupt_event.is_set():
        return False
    # Mobile interrupt must stop shell loops (bash -c 'while ... sleep 1').
    # SIGINT only cancels the current sleep and the shell keeps running.
    send_signal_to_process_tree(proc, signal.SIGTERM)
    for _ in range(15):
        if proc.poll() is not None:
            return True
        time.sleep(0.2)
    send_signal_to_process_tree(proc, signal.SIGKILL)
    try:
        proc.wait(timeout=3)
    except subprocess.TimeoutExpired:
        proc.kill()
    return True


def run_command_with_pty(
    command: Sequence[str],
    store: RunStore,
    run_id: str,
    syncer: CloudSyncer,
    interrupt_event: threading.Event | None = None,
    env: dict[str, str] | None = None,
) -> tuple[int, bool]:
    previous_sighup = ignore_sighup()
    master_fd, slave_fd = os.openpty()
    set_pty_winsize(master_fd)
    proc: subprocess.Popen[bytes] | None = None
    try:
        try:
            proc = subprocess.Popen(
                list(command),
                stdin=slave_fd,
                stdout=slave_fd,
                stderr=slave_fd,
                close_fds=True,
                env=env,
                **subprocess_session_kwargs(),
            )
        except Exception:
            restore_sighup(previous_sighup)
            raise
    finally:
        os.close(slave_fd)

    store.mark_running(run_id, proc.pid)
    syncer.request_sync()

    def forward_signal(signum: int, _frame: object) -> None:
        send_signal_to_process_tree(proc, signum)

    previous_sigint = signal.signal(signal.SIGINT, forward_signal)
    previous_sigterm = signal.signal(signal.SIGTERM, forward_signal)

    def forward_winsize(_signum: int, _frame: object) -> None:
        set_pty_winsize(master_fd)

    previous_sigwinch = None
    if hasattr(signal, "SIGWINCH"):
        try:
            previous_sigwinch = signal.signal(signal.SIGWINCH, forward_winsize)
        except (ValueError, OSError):
            previous_sigwinch = None
    stdin_fd = sys.stdin.fileno() if sys.stdin is not None and sys.stdin.isatty() else None
    output = bytearray()

    interrupted = False
    try:
        while proc.poll() is None:
            refresh_interrupt_event(run_id, syncer.client, interrupt_event)
            if interrupt_event is not None and terminate_process_on_interrupt(proc, interrupt_event):
                interrupted = True
                break
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
        if previous_sigwinch is not None and hasattr(signal, "SIGWINCH"):
            signal.signal(signal.SIGWINCH, previous_sigwinch)
        restore_sighup(previous_sighup)
        flush_pty_output(output, store, run_id, syncer, force=True)
        os.close(master_fd)

    exit_code = proc.wait()
    return exit_code, interrupted


def set_pty_winsize(master_fd: int) -> None:
    """Size the PTY to the real terminal so child progress bars (tqdm, etc.)
    render at the correct width. Without this the PTY defaults to 0 columns and
    progress bars either don't draw or wrap into endless new lines."""
    try:
        import fcntl
        import struct
        import termios
    except Exception:
        return
    size = None
    for stream in (sys.stdout, sys.stderr, sys.stdin):
        try:
            if stream is not None and stream.isatty():
                size = fcntl.ioctl(stream.fileno(), termios.TIOCGWINSZ, b"\x00" * 8)
                break
        except Exception:
            continue
    if size is None:
        try:
            cols, rows = os.get_terminal_size()
        except Exception:
            cols, rows = 100, 30
        size = struct.pack("HHHH", rows, cols, 0, 0)
    try:
        fcntl.ioctl(master_fd, termios.TIOCSWINSZ, size)
    except Exception:
        pass


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


def run_command_with_pipes(
    command: Sequence[str],
    store: RunStore,
    run_id: str,
    syncer: CloudSyncer,
    interrupt_event: threading.Event | None = None,
    env: dict[str, str] | None = None,
) -> tuple[int, bool]:
    previous_sighup = ignore_sighup()
    try:
        proc = subprocess.Popen(
            list(command),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
            env=env,
            **subprocess_session_kwargs(),
        )
    except Exception:
        restore_sighup(previous_sighup)
        raise

    store.mark_running(run_id, proc.pid)
    syncer.request_sync()

    stop_forwarding = threading.Event()

    def forward_signal(signum: int, _frame: object) -> None:
        send_signal_to_process_tree(proc, signum)

    previous_sigint = signal.signal(signal.SIGINT, forward_signal)
    previous_sigterm = signal.signal(signal.SIGTERM, forward_signal)
    interrupted = False

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

        while proc.poll() is None:
            refresh_interrupt_event(run_id, syncer.client, interrupt_event)
            if interrupt_event is not None and terminate_process_on_interrupt(proc, interrupt_event):
                interrupted = True
                break
            time.sleep(0.2)
        exit_code = proc.returncode if proc.returncode is not None else proc.wait()
        stop_forwarding.set()
        for thread in threads:
            thread.join(timeout=2)
    finally:
        signal.signal(signal.SIGINT, previous_sigint)
        signal.signal(signal.SIGTERM, previous_sigterm)
        restore_sighup(previous_sighup)

    return exit_code, interrupted


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
  hao [options] <command> [args...]

Options:
  -h, --help            Show this help message and exit.
  -V, --version         Show version and exit.

Commands:
  login                 Pair this device with the Android app
  status                Show recent command runs (local)
  doctor                Diagnose configuration, connectivity and heartbeat
  sync                  Manually sync pending runs to cloud
  update                Update hao CLI to latest version

  project use NAME      Set default project for future runs
  project clear         Clear default project
  project status        Show current default project

  cloud-login           Configure cloud token directly (for CI/scripts)
  cloud-status          Show current cloud configuration
  cloud-logout          Remove cloud configuration

  devices list          List paired devices
  devices rename <id> <name>
                        Rename a device
  devices revoke <id>   Revoke a device

  status                (with filters) List/filter runs
  clear                 Clear local (and optionally cloud) run history
  cancel <run-id>       Cancel a running command

  server                Run local cloud server
  public                Run local server + Cloudflare tunnel
  ngrok                 Run local server + ngrok tunnel
  heartbeat             Manage local heartbeat daemon

Run commands (primary usage):
  hao <command> [args...]
  hao --project NAME <command> [args...]
  hao --no-project <command> [args...]
  hao 'shell | pipeline | with | metachars'

After login or cloud-login, normal hao commands sync status to cloud automatically.
Set a default project once with `hao project use NAME`; otherwise git repo names are used automatically.
Override one run with --project or --no-project.

Notes:
- Subcommand names take precedence over running commands with the same name.
  Use `hao -- <command>` or full path to bypass.
- Shell features (|, &&, redirects, etc.) must be quoted as a single argument
  to hao, e.g. `hao 'echo a | grep b'`. hao will wrap with sh -c when needed.

New: hao devices, hao clear, hao cancel, improved hao status.
"""
    )
