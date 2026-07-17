from __future__ import annotations

import ipaddress
import json
import os
import platform
import secrets
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from collections.abc import Callable
from typing import Any

from .store import RunRecord, RunStore, default_data_dir
from . import __version__
from .crypto import encrypt_output_chunk, encrypt_run_payload, is_valid_account_key


DEFAULT_CLOUD_URL = os.environ.get("HAOLEME_CLOUD_URL", "https://api.haoleme.cloud").rstrip("/")
USER_AGENT = f"haoleme/{__version__}"


_CLIENT_RUN_METADATA: dict[str, str] | None = None


def client_run_metadata() -> dict[str, str]:
    """Constant per-process run metadata (CLI version, OS, hostname), cached."""
    global _CLIENT_RUN_METADATA
    if _CLIENT_RUN_METADATA is None:
        try:
            os_label = f"{platform.system()} {platform.release()}".strip()
        except Exception:
            os_label = ""
        try:
            host = socket.gethostname()
        except Exception:
            host = ""
        _CLIENT_RUN_METADATA = {
            "cliVersion": __version__,
            "os": os_label,
            "hostname": host,
        }
    return _CLIENT_RUN_METADATA
# Console sync cadence is tiered by run age (see CloudSyncer._running_sync_interval):
# fast (MIN) for fresh runs, easing to MAX for long-lived ones.
RUNNING_SYNC_MIN_INTERVAL_SECONDS = 1.0
RUNNING_SYNC_MAX_INTERVAL_SECONDS = 10.0
SYNC_COALESCE_SECONDS = 0.35
SYNC_RETRY_MAX_SECONDS = 30.0
INTERRUPT_POLL_SECONDS = 1.0
OUTPUT_CHUNK_BYTES = 256 * 1024
LIVE_SYNC_MAX_CHUNKS = 4
LEGACY_CLOUD_URLS = {
    "http://api.haoleme.cloud",
}


def env_flag(name: str, default: bool = False) -> bool:
    value = os.environ.get(name, "")
    if not value:
        return default
    return value.strip().lower() not in {"0", "false", "no", "off"}


def _json_error_detail(detail: str) -> str:
    text = detail.strip()
    if not text:
        return ""
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        return text[:240]
    if isinstance(parsed, dict):
        for key in ("error", "message", "detail"):
            value = parsed.get(key)
            if value:
                return str(value)[:240]
    return text[:240]


def describe_cloud_error(exc: BaseException) -> str:
    if isinstance(exc, urllib.error.HTTPError):
        detail = _json_error_detail(exc.read().decode("utf-8", errors="replace"))
        hints = {
            400: "bad request; your hao package may be too old",
            401: "authentication failed; run: hao login",
            403: "permission denied; re-pair this device",
            404: "endpoint not found; check cloud server URL",
            409: "request conflict; retry or run: hao login",
            429: "rate limited; wait a moment and retry",
        }
        if exc.code >= 500:
            hint = "cloud server error; retry later"
        else:
            hint = hints.get(exc.code, "cloud rejected the request")
        suffix = f" ({detail})" if detail else ""
        return f"HTTP {exc.code}: {hint}{suffix}"

    if isinstance(exc, urllib.error.URLError):
        reason = exc.reason
        if isinstance(reason, (TimeoutError, socket.timeout)):
            return "network timeout; check your connection or cloud server"
        if isinstance(reason, ConnectionRefusedError):
            return "connection refused; cloud server is not listening"
        if isinstance(reason, socket.gaierror):
            return "DNS lookup failed; check the cloud server address"
        text = str(reason)
        lowered = text.lower()
        if "ssl" in lowered or "certificate" in lowered:
            return f"TLS/SSL connection failed; check server HTTPS settings ({text})"
        if "timed out" in lowered:
            return "network timeout; check your connection or cloud server"
        if "name or service not known" in lowered or "nodename nor servname" in lowered:
            return "DNS lookup failed; check the cloud server address"
        if "connection refused" in lowered:
            return "connection refused; cloud server is not listening"
        if "network is unreachable" in lowered:
            return "network unreachable; check your internet connection"
        return f"network error: {text}"

    if isinstance(exc, (TimeoutError, socket.timeout)):
        return "network timeout; check your connection or cloud server"
    if isinstance(exc, json.JSONDecodeError):
        return "cloud returned invalid JSON; check server version"
    return str(exc)


def default_config_path() -> Path:
    return default_data_dir() / "config.json"


@dataclass(frozen=True)
class CloudConfig:
    api_url: str
    account: str
    token: str
    device_id: str = ""
    device_name: str = ""
    machine_id: str = ""
    encryption_key: str = ""
    enabled: bool = True

    @classmethod
    def load(cls, path: Path | None = None) -> "CloudConfig | None":
        config_path = path or default_config_path()
        if not config_path.exists():
            return None
        try:
            data = json.loads(config_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None

        cloud = data.get("cloud") if isinstance(data, dict) else None
        if not isinstance(cloud, dict) or not cloud.get("enabled", True):
            return None

        raw_api_url = str(cloud.get("api_url", "")).strip()
        api_url = normalize_cloud_url(raw_api_url)
        account = str(cloud.get("account", "")).strip()
        token = str(cloud.get("token", "")).strip()
        device_id = str(cloud.get("device_id", "")).strip()
        device_name = str(cloud.get("device_name", "")).strip()
        machine_id = str(cloud.get("machine_id", "")).strip()
        encryption_key = str(cloud.get("encryption_key", "")).strip()
        if not api_url or not token:
            return None
        if api_url != raw_api_url.rstrip("/"):
            try:
                cloud["api_url"] = api_url
                config_path.write_text(json.dumps(data, indent=2), encoding="utf-8")
                config_path.chmod(0o600)
            except OSError:
                pass
        return cls(
            api_url=api_url,
            account=account,
            token=token,
            device_id=device_id,
            device_name=device_name,
            machine_id=machine_id,
            encryption_key=encryption_key,
        )

    def save(self, path: Path | None = None) -> None:
        config_path = path or default_config_path()
        config_path.parent.mkdir(parents=True, exist_ok=True)
        data: dict[str, Any] = {}
        if config_path.exists():
            try:
                loaded = json.loads(config_path.read_text(encoding="utf-8"))
                if isinstance(loaded, dict):
                    data = loaded
            except (OSError, json.JSONDecodeError):
                data = {}
        data["cloud"] = {
            "api_url": self.api_url.rstrip("/"),
            "account": self.account,
            "token": self.token,
            "device_id": self.device_id,
            "device_name": self.device_name,
            "machine_id": self.machine_id,
            "encryption_key": self.encryption_key,
            "enabled": self.enabled,
        }
        config_path.write_text(json.dumps(data, indent=2), encoding="utf-8")
        try:
            config_path.chmod(0o600)
        except OSError:
            pass


def normalize_cloud_url(raw: str) -> str:
    api_url = (raw or "").strip().rstrip("/")
    if api_url in LEGACY_CLOUD_URLS:
        return DEFAULT_CLOUD_URL
    try:
        parsed = urllib.parse.urlsplit(api_url)
        port = parsed.port
    except ValueError:
        return api_url
    try:
        host_is_public_ip = ipaddress.ip_address(parsed.hostname or "").is_global
    except ValueError:
        host_is_public_ip = False
    if (parsed.scheme.lower() in {"http", "https"}
            and host_is_public_ip
            and port in {None, 80, 443}
            and not parsed.path
            and not parsed.query
            and not parsed.fragment):
        return DEFAULT_CLOUD_URL
    return api_url


def generate_account_token() -> str:
    return secrets.token_urlsafe(32)


def generate_device_id() -> str:
    return "dev_" + secrets.token_urlsafe(12).replace("-", "_")


def default_machine_id_path() -> Path:
    return default_data_dir() / "machine_id"


def get_or_create_machine_id(path: Path | None = None) -> str:
    machine_path = path or default_machine_id_path()
    try:
        existing = machine_path.read_text(encoding="utf-8").strip()
    except OSError:
        existing = ""
    if existing.startswith("machine_") and 16 <= len(existing) <= 96:
        return existing
    machine_id = "machine_" + secrets.token_urlsafe(24).replace("-", "_")
    machine_path.parent.mkdir(parents=True, exist_ok=True)
    machine_path.write_text(machine_id + "\n", encoding="utf-8")
    return machine_id


class CloudClient:
    def __init__(self, config: CloudConfig, timeout: float = 5.0) -> None:
        self.config = config
        self.timeout = timeout

    def health(self) -> dict[str, Any]:
        return self.request("GET", "/health")

    def upsert_run(self, run: RunRecord, *, include_output: bool = True) -> None:
        payload = run.to_dict()
        if not include_output:
            # The authoritative cloud cursor advances only after a chunk is
            # committed. Advertising the local total here would make a retry
            # look fully uploaded before any output reached the server.
            payload["outputLength"] = 0
        meta = client_run_metadata()
        payload["cliVersion"] = meta["cliVersion"]
        payload["os"] = meta["os"]
        payload["hostname"] = meta["hostname"]
        if self.config.device_id:
            payload["deviceId"] = self.config.device_id
        if self.config.device_name:
            payload["deviceName"] = self.config.device_name
        if is_valid_account_key(self.config.encryption_key):
            payload = encrypt_run_payload(payload, self.config.encryption_key, include_output=include_output)
        elif not env_flag("HAOLEME_ALLOW_PLAINTEXT_CLOUD_RUNS", False):
            raise RuntimeError("E2EE is not configured; run `hao login` from the app again before cloud sync")
        self.request("POST", "/api/runs", {"run": payload})

    def append_run_update(
        self,
        run: RunRecord,
        deltas: dict[str, str],
        output_start: int | None = None,
        output_end: int | None = None,
    ) -> dict[str, Any]:
        patch: dict[str, Any] = {
            "id": run.id,
            "status": run.status,
            "pid": run.pid,
            "exitCode": run.exit_code,
            "endedAt": run.ended_at,
            "updatedAt": run.updated_at,
            "project": run.project,
            "outputLength": run.output_length if output_end is None else output_end,
        }
        if output_start is not None:
            patch["outputStart"] = max(0, output_start)
        if self.config.device_id:
            patch["deviceId"] = self.config.device_id
        if self.config.device_name:
            patch["deviceName"] = self.config.device_name
        output_delta = deltas.get("output_tail") or ""
        stdout_delta = deltas.get("stdout_tail") or ""
        stderr_delta = deltas.get("stderr_tail") or ""
        if is_valid_account_key(self.config.encryption_key):
            chunk_fields = {
                "outputTail": output_delta,
                "stdoutTail": stdout_delta,
                "stderrTail": stderr_delta,
            }
            chunk_fields = {key: value for key, value in chunk_fields.items() if value}
            if chunk_fields:
                patch["e2eeOutputChunk"] = encrypt_output_chunk(run.id, self.config.encryption_key, chunk_fields)
        else:
            if output_delta:
                patch["outputDelta"] = output_delta
            if stdout_delta:
                patch["stdoutDelta"] = stdout_delta
            if stderr_delta:
                patch["stderrDelta"] = stderr_delta
        return self.request("POST", "/api/runs", {"append": True, "run": patch})

    def heartbeat(self, gpus: list[dict[str, Any]] | None = None, cpu: dict[str, Any] | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.config.device_id:
            payload["deviceId"] = self.config.device_id
        if self.config.device_name:
            payload["deviceName"] = self.config.device_name
        if gpus is not None:
            payload["gpus"] = gpus
        if cpu is not None:
            payload["cpu"] = cpu
        return self.request("POST", "/api/devices/heartbeat", payload)

    def get_run(self, run_id: str) -> dict[str, Any]:
        payload = self.request("GET", f"/api/runs/{run_id}")
        run = payload.get("run")
        if not isinstance(run, dict):
            raise RuntimeError("cloud response missing run")
        return run

    def list_pending_interrupts(self) -> list[dict[str, Any]]:
        payload = self.request("GET", "/api/devices/pending-interrupts")
        interrupts = payload.get("interrupts")
        if not isinstance(interrupts, list):
            return []
        return [item for item in interrupts if isinstance(item, dict)]

    def list_devices(self) -> list[dict[str, Any]]:
        payload = self.request("GET", "/api/devices")
        devices = payload.get("devices", [])
        return devices if isinstance(devices, list) else []

    def rename_device(self, device_id: str, name: str) -> dict[str, Any]:
        payload = self.request("POST", f"/api/devices/{device_id}/rename", {"name": name})
        if isinstance(payload, dict):
            return payload.get("device") or payload
        return {}

    def revoke_device(self, device_id: str) -> bool:
        try:
            self.request("DELETE", f"/api/devices/{device_id}")
            return True
        except Exception:
            return False

    def request_interrupt(self, run_id: str) -> dict[str, Any]:
        try:
            return self.request("POST", f"/api/runs/{run_id}/interrupt")
        except Exception as exc:
            raise RuntimeError(f"interrupt request failed: {exc}") from exc

    def clear_all_runs(self) -> int:
        """Delete all runs on the cloud for this account. Returns deleted count if available."""
        try:
            payload = self.request("DELETE", "/api/runs")
            if isinstance(payload, dict):
                return int(payload.get("deleted", 0) or 0)
            return 0
        except Exception:
            # Some deployments may not return count; assume success if no exception on 2xx
            return -1

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        body = None
        headers = {
            "Authorization": f"Bearer {self.config.token}",
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": USER_AGENT,
            "X-Haoleme-Account": self.config.account,
        }
        if payload is not None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")

        url = self.config.api_url.rstrip("/") + path
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
        except urllib.error.HTTPError as exc:
            raise RuntimeError(describe_cloud_error(exc)) from exc
        except (urllib.error.URLError, TimeoutError, socket.timeout) as exc:
            raise RuntimeError(describe_cloud_error(exc)) from exc

        if not raw:
            return {}
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise RuntimeError(describe_cloud_error(exc)) from exc
        if not isinstance(parsed, dict):
            raise RuntimeError("cloud returned non-object JSON")
        return parsed


class PairingClient:
    def __init__(self, api_url: str, timeout: float = 8.0) -> None:
        self.api_url = api_url.rstrip("/")
        self.timeout = timeout

    def start(self, device_name: str, device_id: str = "", public_key: str = "", machine_id: str = "") -> dict[str, Any]:
        payload = {"deviceName": device_name}
        if device_id:
            payload["deviceId"] = device_id
        if public_key:
            payload["publicKey"] = public_key
        if machine_id:
            payload["machineId"] = machine_id
        return self.request("POST", "/api/pair/start", payload)

    def status(self, code: str, pair_token: str) -> dict[str, Any]:
        return self.request("POST", "/api/pair/status", {"code": code, "pairToken": pair_token})

    def cancel(self, code: str, pair_token: str) -> dict[str, Any]:
        return self.request("POST", "/api/pair/cancel", {"code": code, "pairToken": pair_token})

    def request(self, method: str, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            self.api_url + path,
            data=body,
            headers={"Content-Type": "application/json; charset=utf-8", "User-Agent": USER_AGENT},
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
        except urllib.error.HTTPError as exc:
            raise RuntimeError(describe_cloud_error(exc)) from exc
        except (urllib.error.URLError, TimeoutError, socket.timeout) as exc:
            raise RuntimeError(describe_cloud_error(exc)) from exc
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise RuntimeError(describe_cloud_error(exc)) from exc
        if not isinstance(parsed, dict):
            raise RuntimeError("cloud returned non-object JSON")
        return parsed


class InterruptWatcher:
    def __init__(self, client: CloudClient | None, run_id: str, on_interrupt: Callable[[], None]) -> None:
        self.client = client
        self.run_id = run_id
        self.on_interrupt = on_interrupt
        self._stop = threading.Event()
        self._triggered = threading.Event()
        self._thread: threading.Thread | None = None
        self.last_error: str | None = None

    def start(self) -> None:
        if self.client is None:
            return
        self._thread = threading.Thread(target=self._loop, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2)

    def triggered(self) -> bool:
        return self._triggered.is_set()

    def _loop(self) -> None:
        while not self._stop.is_set():
            try:
                for item in self.client.list_pending_interrupts():
                    if item.get("id") == self.run_id and item.get("interruptRequestedAt"):
                        if not self._triggered.is_set():
                            self._triggered.set()
                            self.on_interrupt()
                        return
            except Exception as exc:
                self.last_error = str(exc)
            self._stop.wait(timeout=INTERRUPT_POLL_SECONDS)


class CloudSyncer:
    def __init__(self, store: RunStore, run_id: str, client: CloudClient | None) -> None:
        self.store = store
        self.run_id = run_id
        self.client = client
        self._event = threading.Event()
        self._stop = threading.Event()
        self._sync_lock = threading.Lock()
        self._thread: threading.Thread | None = None
        self.last_error: str | None = None
        self._last_sync_at = 0.0
        self._started_at = time.monotonic()
        self._last_output_at = time.monotonic()
        self._initial_synced = False
        run = self.store.get_run(self.run_id)
        self._synced_output_len = run.cloud_output_cursor if run is not None else 0
        self._synced_stdout_len = 0
        self._synced_stderr_len = 0
        self._failure_count = 0
        self._next_retry_at = 0.0
        if self.client is not None:
            self._thread = threading.Thread(target=self._loop, daemon=True)
            self._thread.start()

    def request_sync(self) -> None:
        if self.client is not None:
            self._event.set()

    def close(self) -> None:
        if self.client is None:
            return
        self._sync_once(force=True)
        self._stop.set()
        self._event.set()
        if self._thread is not None:
            self._thread.join(timeout=2)

    def _loop(self) -> None:
        while not self._stop.is_set():
            self._event.wait(timeout=1)
            if self._stop.is_set():
                break
            retry_due = self._next_retry_at > 0 and time.monotonic() >= self._next_retry_at
            event_set = self._event.is_set()
            if not event_set and not retry_due:
                continue
            if event_set:
                self._event.clear()
                time.sleep(SYNC_COALESCE_SECONDS)
            self._sync_once(force=retry_due)

    def _running_sync_interval(self) -> float:
        # Tiered cadence by how long output has been idle, NOT by run age: a job
        # that keeps printing (e.g. training logs) stays near real-time forever so
        # the console never appears to freeze, while a genuinely quiet run eases
        # off to avoid hammering the cloud. ~1s while output is flowing (idle < 2
        # min), ramping to ~5s by 10 min idle and ~10s by 30 min idle.
        idle = time.monotonic() - self._last_output_at
        if idle <= 120:
            return 1.0
        if idle >= 1800:
            return RUNNING_SYNC_MAX_INTERVAL_SECONDS
        if idle <= 600:
            return 1.0 + (idle - 120) / (600 - 120) * (5.0 - 1.0)
        return 5.0 + (idle - 600) / (1800 - 600) * (RUNNING_SYNC_MAX_INTERVAL_SECONDS - 5.0)

    def _output_deltas(self, run: RunRecord) -> dict[str, str]:
        output_delta, _start, _end = next_output_chunk(run, self._synced_output_len)
        return {
            "output_tail": output_delta,
            "stdout_tail": "",
            "stderr_tail": "",
        }

    def _mark_output_synced(self, run: RunRecord, cursor: int | None = None) -> None:
        self._synced_output_len = run.output_length if cursor is None else max(0, cursor)
        self._synced_stdout_len = len(run.stdout_tail)
        self._synced_stderr_len = len(run.stderr_tail)

    def _sync_once(self, force: bool = False) -> None:
        sync_lock = getattr(self, "_sync_lock", None)
        if sync_lock is None:
            sync_lock = threading.Lock()
            self._sync_lock = sync_lock
        with sync_lock:
            self._sync_once_locked(force)

    def _sync_once_locked(self, force: bool = False) -> None:
        if self.client is None:
            return
        run = self.store.get_run(self.run_id)
        if run is None:
            return
        if not force and run.status in {"created", "running"}:
            now = time.monotonic()
            if self._last_sync_at and now - self._last_sync_at < self._running_sync_interval():
                return
        try:
            running = run.status in {"created", "running"}

            if not self._initial_synced:
                self.client.upsert_run(run, include_output=False)
                self._initial_synced = True

            sent_output = False
            for _ in range(LIVE_SYNC_MAX_CHUNKS):
                output_delta, output_start, output_end = next_output_chunk(run, self._synced_output_len)
                if not output_delta:
                    break
                deltas = {"output_tail": output_delta, "stdout_tail": "", "stderr_tail": ""}
                response = self.client.append_run_update(run, deltas, output_start, output_end)
                remote_cursor = int(response.get("outputLength", output_end) or output_end)
                acknowledged = min(remote_cursor, run.output_length)
                self.store.mark_cloud_output_cursor(run.id, acknowledged)
                self._mark_output_synced(run, acknowledged)
                self._last_output_at = time.monotonic()
                sent_output = True

            if running and not sent_output:
                self.client.append_run_update(run, {})
            elif not running:
                self.client.upsert_run(run, include_output=False)

            if self._synced_output_len >= run.output_length:
                self.store.mark_cloud_synced(run.id)
            else:
                self.store.mark_cloud_pending(run.id)
                self._event.set()
            self._last_sync_at = time.monotonic()
            self._failure_count = 0
            self._next_retry_at = 0.0
            self.last_error = None
        except Exception as exc:  # best-effort telemetry should not break commands
            self.last_error = str(exc)
            try:
                self.store.mark_cloud_pending(run.id)
            except Exception:
                pass
            self._failure_count = min(self._failure_count + 1, 8)
            delay = min(SYNC_RETRY_MAX_SECONDS, 2.0 ** min(self._failure_count - 1, 5))
            self._next_retry_at = time.monotonic() + delay


def utf8_prefix(text: str, max_bytes: int = OUTPUT_CHUNK_BYTES) -> str:
    if not text:
        return ""
    if len(text.encode("utf-8")) <= max_bytes:
        return text
    low = 1
    high = len(text)
    while low < high:
        middle = (low + high + 1) // 2
        if len(text[:middle].encode("utf-8")) <= max_bytes:
            low = middle
        else:
            high = middle - 1
    return text[:low]


def next_output_chunk(run: RunRecord, cursor: int) -> tuple[str, int, int]:
    available_start = max(0, run.output_length - len(run.output_tail))
    start = max(0, cursor, available_start)
    if start >= run.output_length:
        return "", start, start
    relative_start = max(0, start - available_start)
    chunk = utf8_prefix(run.output_tail[relative_start:])
    return chunk, start, start + len(chunk)
