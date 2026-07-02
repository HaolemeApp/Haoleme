from __future__ import annotations

import argparse
import shutil
import hashlib
import json
import os
import secrets
import sqlite3
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse

from . import __version__
from ._compat import remove_prefix, remove_suffix, unlink_missing


DEFAULT_MIN_ANDROID_VERSION_CODE = 22
PAIR_TTL_SECONDS = 600
PAIR_START_RATE_LIMIT = 20
APP_REGISTER_RATE_LIMIT = 20
PAIR_CONFIRM_RATE_LIMIT = 30
PAIR_CONFIRM_RATE_WINDOW_SECONDS = 60
READ_RATE_LIMIT = 120
WRITE_RATE_LIMIT = 180
READ_RATE_WINDOW_SECONDS = 60
AUTH_FAILURE_RATE_LIMIT = 120
MAX_JSON_BODY_BYTES = 2 * 1024 * 1024
MAX_OUTPUT_TAIL = 1_000_000
MAX_OUTPUT_CHUNKS = 20_000
MAX_LIST_OUTPUT_PREVIEW = 2000
MAX_LIST_E2EE_CIPHERTEXT = 64 * 1024
DEFAULT_LOG_MAX_BYTES = 50 * 1024 * 1024
DEVICE_ONLINE_WINDOW_SECONDS = 240
STALE_RUNNING_GRACE_SECONDS = 240
STALE_RUNNING_SECONDS = DEVICE_ONLINE_WINDOW_SECONDS + STALE_RUNNING_GRACE_SECONDS
DOWNLOADS_DIR_NAME = "downloads"
DEFAULT_BACKUP_KEEP = 14
DEFAULT_MONITOR_MIN_FREE_BYTES = 512 * 1024 * 1024
DEFAULT_MONITOR_MAX_BACKUP_AGE_HOURS = 30
SPACE_JOIN_CODE_TTL_SECONDS = 300


@dataclass(frozen=True)
class AuthContext:
    account_key: str
    token_hash: str
    scope: str
    device_id: str = ""
    device_name: str = ""


class RequestBodyTooLarge(ValueError):
    pass


class HaolemeCloudServer(ThreadingHTTPServer):
    def __init__(self, server_address: tuple[str, int], db_path: Path, min_android_version_code: int) -> None:
        super().__init__(server_address, HaolemeCloudHandler)
        self.db_path = db_path
        self.min_android_version_code = min_android_version_code
        self.pair_confirm_attempts: dict[str, list[float]] = {}
        self.pair_confirm_attempts_lock = threading.Lock()
        self.pair_start_attempts: dict[str, list[float]] = {}
        self.pair_start_attempts_lock = threading.Lock()
        self.app_register_attempts: dict[str, list[float]] = {}
        self.app_register_attempts_lock = threading.Lock()
        self.auth_failure_attempts: dict[str, list[float]] = {}
        self.auth_failure_attempts_lock = threading.Lock()
        self.read_attempts: dict[str, list[float]] = {}
        self.read_attempts_lock = threading.Lock()
        self.write_attempts: dict[str, list[float]] = {}
        self.write_attempts_lock = threading.Lock()
        init_db(db_path)


class HaolemeCloudHandler(BaseHTTPRequestHandler):
    server: HaolemeCloudServer

    def setup(self) -> None:
        self.request_started_at = time.time()
        super().setup()

    def handle_one_request(self) -> None:
        try:
            super().handle_one_request()
        except RequestBodyTooLarge:
            self.send_json({"error": "request body too large", "code": "request_body_too_large"}, status=HTTPStatus.REQUEST_ENTITY_TOO_LARGE)
        except (BrokenPipeError, ConnectionResetError, OSError):
            return

    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_cors_headers()
        self.end_headers()

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            auth = self.authenticated_context(allow_legacy=False)
            self.send_json(health_payload(self.server.db_path, self.server.min_android_version_code, detailed=auth is not None))
            return
        if parsed.path == "/stats" or parsed.path == "/api/admin/stats":
            self.send_stats_page(parsed)
            return
        if parsed.path.startswith("/downloads/"):
            self.send_download(remove_prefix(parsed.path, "/downloads/"))
            return

        auth = self.authenticated_context()
        if parsed.path.startswith("/api/") and not auth:
            self.send_unauthorized()
            return

        if parsed.path == "/api/devices/pending-interrupts":
            if auth.scope != "write":
                self.send_json({"error": "write token required", "code": "write_token_required"}, status=HTTPStatus.FORBIDDEN)
                return
            if not auth.device_id:
                self.send_json({"error": "missing device id", "code": "missing_device_id"}, status=HTTPStatus.BAD_REQUEST)
                return
            if not self.allow_read_attempt(auth):
                self.send_json(
                    {"error": "too many read requests, slow down", "code": "read_rate_limited"},
                    status=HTTPStatus.TOO_MANY_REQUESTS,
                )
                return
            self.send_json(
                {
                    "interrupts": list_pending_interrupts(
                        self.server.db_path,
                        auth.account_key,
                        auth.device_id,
                    )
                }
            )
            return

        if parsed.path.startswith("/api/runs/"):
            run_id = unquote(remove_prefix(parsed.path, "/api/runs/")).strip("/")
            if is_single_run_id(run_id):
                if auth.scope not in {"admin", "write"}:
                    self.send_json({"error": "read token required", "code": "read_token_required"}, status=HTTPStatus.FORBIDDEN)
                    return
                if not self.allow_read_attempt(auth):
                    self.send_json(
                        {"error": "too many read requests, slow down", "code": "read_rate_limited"},
                        status=HTTPStatus.TOO_MANY_REQUESTS,
                    )
                    return
                run = get_run(self.server.db_path, auth.account_key, run_id)
                if run is None:
                    self.send_json({"error": "run not found", "code": "run_not_found"}, status=HTTPStatus.NOT_FOUND)
                    return
                if not can_read_run(auth, run):
                    self.send_json({"error": "run not found", "code": "run_not_found"}, status=HTTPStatus.NOT_FOUND)
                    return
                query = parse_qs(parsed.query)
                self.send_json(
                    build_run_fetch_payload(
                        run,
                        output_since=parse_output_since(query),
                        output_length=parse_output_length(query),
                    )
                )
                return

        if parsed.path.startswith("/api/") and auth.scope != "admin":
            self.send_json({"error": "read token required", "code": "read_token_required"}, status=HTTPStatus.FORBIDDEN)
            return
        if parsed.path.startswith("/api/") and not self.allow_read_attempt(auth):
            self.send_json(
                {"error": "too many read requests, slow down", "code": "read_rate_limited"},
                status=HTTPStatus.TOO_MANY_REQUESTS,
            )
            return

        if parsed.path == "/api/runs":
            query = parse_qs(parsed.query)
            limit = parse_limit(query.get("limit", ["100"])[0])
            device_id = first_query_value(query, "deviceId")
            status_filter = first_query_value(query, "status")
            project_filter = first_query_value(query, "project")
            self.send_json({"runs": list_runs(self.server.db_path, auth.account_key, limit, device_id, status_filter, project_filter)})
            return

        if parsed.path == "/api/devices":
            self.send_json({"devices": list_devices(self.server.db_path, auth.account_key)})
            return

        if parsed.path == "/api/events":
            query = parse_qs(parsed.query)
            since = query.get("since", [None])[0]
            limit = parse_limit(query.get("limit", ["100"])[0])
            events = list_events(self.server.db_path, auth.account_key, since, limit)
            latest = max((run.get("updatedAt", "") for run in events), default=since or "")
            self.send_json({"events": events, "latest": latest})
            return

        self.send_json({"error": "not found", "code": "not_found"}, status=HTTPStatus.NOT_FOUND)

    def do_HEAD(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/downloads/"):
            self.send_download(remove_prefix(parsed.path, "/downloads/"), head_only=True)
            return
        if parsed.path == "/health":
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", "0")
            self.send_cors_headers()
            self.end_headers()
            return
        self.send_response(HTTPStatus.NOT_FOUND)
        self.send_header("Content-Length", "0")
        self.send_cors_headers()
        self.end_headers()

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/pair/start":
            if not self.allow_pair_start_attempt():
                self.send_json(
                    {"error": "too many pair requests, try again later", "code": "pair_start_rate_limited"},
                    status=HTTPStatus.TOO_MANY_REQUESTS,
                )
                return
            self.start_pairing()
            return
        if parsed.path == "/api/pair/status":
            self.pairing_status()
            return
        if parsed.path == "/api/pair/cancel":
            self.cancel_pairing()
            return
        if parsed.path == "/api/pair/info":
            self.pairing_info()
            return
        if parsed.path == "/api/pair/confirm":
            self.confirm_pairing()
            return
        if parsed.path == "/api/apps/register":
            if not self.allow_app_register_attempt():
                self.send_json(
                    {"error": "too many app registration requests, try again later", "code": "app_register_rate_limited"},
                    status=HTTPStatus.TOO_MANY_REQUESTS,
                )
                return
            self.register_app()
            return
        if parsed.path == "/api/space/join":
            self.join_sync_space()
            return

        auth = self.authenticated_context()
        if parsed.path.startswith("/api/") and not auth:
            self.send_unauthorized()
            return

        if parsed.path == "/api/space/share":
            if auth.scope != "admin":
                self.send_json({"error": "read token required", "code": "read_token_required"}, status=HTTPStatus.FORBIDDEN)
                return
            self.share_sync_space(auth)
            return
        if parsed.path.startswith("/api/devices/") and parsed.path.endswith("/rename"):
            if auth.scope != "admin":
                self.send_json({"error": "read token required", "code": "read_token_required"}, status=HTTPStatus.FORBIDDEN)
                return
            device_id = unquote(remove_suffix(remove_prefix(parsed.path, "/api/devices/"), "/rename")).strip("/")
            self.rename_device(auth, device_id)
            return
        if parsed.path == "/api/devices/heartbeat":
            if not self.allow_write_attempt(auth):
                self.send_json({"error": "too many write requests, slow down", "code": "write_rate_limited"}, status=HTTPStatus.TOO_MANY_REQUESTS)
                return
            self.device_heartbeat(auth)
            return
        if parsed.path.startswith("/api/runs/") and parsed.path.endswith("/interrupt"):
            if auth.scope != "admin":
                self.send_json({"error": "read token required", "code": "read_token_required"}, status=HTTPStatus.FORBIDDEN)
                return
            run_id = unquote(remove_suffix(remove_prefix(parsed.path, "/api/runs/"), "/interrupt")).strip("/")
            self.interrupt_run(auth, run_id)
            return
        if parsed.path == "/api/runs":
            if not self.allow_write_attempt(auth):
                self.send_json({"error": "too many write requests, slow down", "code": "write_rate_limited"}, status=HTTPStatus.TOO_MANY_REQUESTS)
                return
            payload = self.read_json()
            run = payload.get("run") if isinstance(payload.get("run"), dict) else payload
            if not isinstance(run, dict) or not run.get("id"):
                self.send_json({"error": "missing run", "code": "missing_run"}, status=HTTPStatus.BAD_REQUEST)
                return
            if payload.get("append"):
                stored = append_run_update(self.server.db_path, auth.account_key, run, auth)
                if stored is None:
                    self.send_json({"error": "run not found", "code": "run_not_found"}, status=HTTPStatus.NOT_FOUND)
                    return
                if auth.scope == "write":
                    stored["deviceId"] = auth.device_id
                    if not stored.get("deviceName"):
                        stored["deviceName"] = auth.device_name
                touch_token(self.server.db_path, auth.token_hash, stored.get("updatedAt", "") or iso_now())
                self.send_json({"ok": True, "outputLength": stored.get("outputLength", 0), "outputChunks": len(stored.get("outputChunks") or [])})
                return
            stored = normalize_run(run)
            if server_requires_e2ee() and not is_e2ee_run(stored):
                self.send_json(
                    {"error": "end-to-end encryption required", "code": "e2ee_required"},
                    status=HTTPStatus.BAD_REQUEST,
                )
                return
            if auth.scope == "write":
                stored["deviceId"] = auth.device_id
                if not stored.get("deviceName"):
                    stored["deviceName"] = auth.device_name
            upsert_run(self.server.db_path, auth.account_key, stored)
            if stored.get("deviceId"):
                upsert_device(
                    self.server.db_path,
                    auth.account_key,
                    stored.get("deviceId", ""),
                    stored.get("deviceName", "") or "好了么 CLI",
                    stored.get("updatedAt", "") or iso_now(),
                )
            touch_token(self.server.db_path, auth.token_hash, stored.get("updatedAt", "") or iso_now())
            self.send_json({"ok": True})
            return

        self.send_json({"error": "not found", "code": "not_found"}, status=HTTPStatus.NOT_FOUND)

    def interrupt_run(self, auth: AuthContext, run_id: str) -> None:
        if not run_id:
            self.send_json({"error": "missing run id", "code": "missing_run_id"}, status=HTTPStatus.BAD_REQUEST)
            return
        result, error_code = request_run_interrupt(self.server.db_path, auth.account_key, run_id)
        if error_code == "run_not_found":
            self.send_json({"error": "run not found", "code": "run_not_found"}, status=HTTPStatus.NOT_FOUND)
            return
        if error_code == "run_not_active":
            self.send_json({"error": "run is not active", "code": "run_not_active"}, status=HTTPStatus.CONFLICT)
            return
        self.send_json(
            {
                "ok": True,
                "interruptRequestedAt": result.get("interruptRequestedAt", "") if result else "",
            }
        )

    def rename_device(self, auth: AuthContext, device_id: str) -> None:
        if not device_id:
            self.send_json({"error": "missing device id", "code": "missing_device_id"}, status=HTTPStatus.BAD_REQUEST)
            return
        payload = self.read_json()
        name = str(payload.get("name") or "").strip()
        if not name:
            self.send_json({"error": "missing device name", "code": "missing_device_name"}, status=HTTPStatus.BAD_REQUEST)
            return
        device = rename_device(self.server.db_path, auth.account_key, device_id, name[:80])
        if device is None:
            self.send_json({"error": "device not found", "code": "device_not_found"}, status=HTTPStatus.NOT_FOUND)
            return
        self.send_json({"ok": True, "device": device})

    def device_heartbeat(self, auth: AuthContext) -> None:
        payload = self.read_json()
        if auth.scope == "write":
            device_id = auth.device_id
            device_name = auth.device_name
        else:
            device_id = str(payload.get("deviceId") or "").strip()
            device_name = str(payload.get("deviceName") or "").strip()
        if not device_id:
            self.send_json({"error": "missing device id", "code": "missing_device_id"}, status=HTTPStatus.BAD_REQUEST)
            return

        gpus = sanitize_gpus(payload.get("gpus")) if "gpus" in payload else None
        cpu = sanitize_cpu(payload.get("cpu")) if "cpu" in payload else None
        seen_at = iso_now()
        device = record_device_heartbeat(
            self.server.db_path,
            auth.account_key,
            device_id,
            device_name or "好了么 CLI",
            seen_at,
            gpus=gpus,
            cpu=cpu,
        )
        touch_token(self.server.db_path, auth.token_hash, seen_at)
        self.send_json({"ok": True, "device": device, "onlineWindowSeconds": DEVICE_ONLINE_WINDOW_SECONDS})

    def do_DELETE(self) -> None:
        parsed = urlparse(self.path)
        auth = self.authenticated_context()
        if parsed.path.startswith("/api/") and not auth:
            self.send_unauthorized()
            return
        if parsed.path.startswith("/api/") and auth.scope != "admin":
            self.send_json({"error": "read token required", "code": "read_token_required"}, status=HTTPStatus.FORBIDDEN)
            return

        if parsed.path.startswith("/api/runs/"):
            run_id = remove_prefix(parsed.path, "/api/runs/")
            deleted = delete_run(self.server.db_path, auth.account_key, run_id)
            if not deleted:
                self.send_json({"error": "run not found", "code": "run_not_found"}, status=HTTPStatus.NOT_FOUND)
                return
            self.send_json({"deleted": True})
            return

        if parsed.path == "/api/runs":
            deleted = delete_all_runs(self.server.db_path, auth.account_key)
            self.send_json({"deleted": deleted})
            return

        if parsed.path.startswith("/api/devices/") and parsed.path.endswith("/runs"):
            device_id = unquote(remove_suffix(remove_prefix(parsed.path, "/api/devices/"), "/runs")).strip("/")
            if not device_id:
                self.send_json({"error": "missing device id", "code": "missing_device_id"}, status=HTTPStatus.BAD_REQUEST)
                return
            if get_device(self.server.db_path, auth.account_key, device_id) is None:
                self.send_json({"error": "device not found", "code": "device_not_found"}, status=HTTPStatus.NOT_FOUND)
                return
            deleted = delete_runs_for_device(self.server.db_path, auth.account_key, device_id)
            self.send_json({"deleted": deleted})
            return

        if parsed.path.startswith("/api/devices/"):
            device_id = unquote(remove_prefix(parsed.path, "/api/devices/")).strip("/")
            revoked = revoke_device(self.server.db_path, auth.account_key, device_id)
            if not revoked:
                self.send_json({"error": "device not found", "code": "device_not_found"}, status=HTTPStatus.NOT_FOUND)
                return
            self.send_json({"revoked": True})
            return

        if parsed.path == "/api/account":
            deleted = delete_account(self.server.db_path, auth.account_key)
            self.send_json({"deleted": deleted})
            return

        self.send_json({"error": "not found", "code": "not_found"}, status=HTTPStatus.NOT_FOUND)

    def register_app(self) -> None:
        token = self.bearer_token()
        if not token or len(token) < 32:
            self.send_unauthorized()
            return
        payload = self.read_json()
        platform = str(payload.get("platform") or "android")[:24].lower()
        client_id = str(payload.get("clientId") or "").strip()
        if not client_id.startswith("app_"):
            client_id = "app_" + secrets.token_urlsafe(12).replace("-", "_")
        client_name = str(payload.get("clientName") or "Haoleme App")[:80]
        account_key = token_hash(token)
        registered_at = iso_now()
        store_app_token(
            self.server.db_path,
            account_key,
            client_id,
            client_name,
            platform,
            token,
            registered_at,
        )
        self.send_json({
            "ok": True,
            "account": "default",
            "clientId": client_id,
            "clientName": client_name,
            "registeredAt": registered_at,
            "spaceId": sync_space_id(account_key),
        })

    def start_pairing(self) -> None:
        cleanup_expired_pairs(self.server.db_path)
        cleanup_expired_space_join_codes(self.server.db_path)
        payload = self.read_json()
        device_name = str(payload.get("deviceName") or "好了么 CLI")[:80]
        requested_device_id = str(payload.get("deviceId") or "").strip()
        machine_id = normalize_machine_id(payload.get("machineId"))
        public_key = str(payload.get("publicKey") or "").strip()
        device_id = requested_device_id if is_valid_device_id(requested_device_id) else "dev_" + secrets.token_urlsafe(12).replace("-", "_")
        now = time.time()
        for _attempt in range(10):
            code = f"{secrets.randbelow(1000000):06d}"
            pair_token = secrets.token_urlsafe(32)
            if create_pair(self.server.db_path, code, pair_token, device_id, device_name, now, public_key, machine_id):
                self.send_json({
                    "code": code,
                    "pairToken": pair_token,
                    "expiresIn": PAIR_TTL_SECONDS,
                    "deviceId": device_id,
                    "deviceName": device_name,
                    "serverTime": iso_now(),
                })
                return
        self.send_json(
            {"error": "could not allocate pair code", "code": "pair_code_unavailable"},
            status=HTTPStatus.SERVICE_UNAVAILABLE,
        )

    def pairing_status(self) -> None:
        cleanup_expired_pairs(self.server.db_path)
        cleanup_expired_space_join_codes(self.server.db_path)
        payload = self.read_json()
        code = normalize_pair_code(payload.get("code"))
        pair_token = str(payload.get("pairToken") or "")
        pair = get_pair(self.server.db_path, code)
        if not code or not pair_token or pair is None or not pair_token_matches(pair["pair_token"], pair_token):
            self.send_json(
                {"error": "pair code expired or not found", "code": "pair_code_expired"},
                status=HTTPStatus.NOT_FOUND,
            )
            return
        if pair["status"] == "cancelled":
            self.send_json(
                {"error": "pair code cancelled", "code": "pair_code_cancelled"},
                status=HTTPStatus.GONE,
            )
            return
        if pair["status"] != "confirmed":
            self.send_json({
                "status": "pending",
                "deviceId": pair["device_id"] or "",
                "deviceName": pair["device_name"] or "",
                "expiresAt": iso_from_epoch(pair["expires_at"]),
            })
            return
        response = {
            "status": "confirmed",
            "account": pair["account"] or "default",
            "token": pair["token"],
            "deviceId": pair["device_id"] or "",
            "deviceName": pair["device_name"] or "",
            "pairedAt": pair["confirmed_at"] or "",
            "encryptedAccountKey": pair["encrypted_account_key"] or "",
            "encryptedAccountKeyAlgorithm": pair["encrypted_account_key_algorithm"] or "",
            "e2eeVersion": pair["e2ee_version"] or 0,
        }
        delete_pair(self.server.db_path, code)
        self.send_json(response)

    def pairing_info(self) -> None:
        cleanup_expired_pairs(self.server.db_path)
        cleanup_expired_space_join_codes(self.server.db_path)
        payload = self.read_json()
        code = normalize_pair_code(payload.get("code"))
        pair = get_pair(self.server.db_path, code)
        if pair is None:
            self.send_json(
                {"error": "pair code expired or not found", "code": "pair_code_expired"},
                status=HTTPStatus.NOT_FOUND,
            )
            return
        if pair["status"] == "cancelled":
            self.send_json({"error": "pair code cancelled", "code": "pair_code_cancelled"}, status=HTTPStatus.GONE)
            return
        if pair["status"] != "pending":
            self.send_json({"error": "pair code already used", "code": "pair_code_used"}, status=HTTPStatus.CONFLICT)
            return
        self.send_json({
            "status": "pending",
            "deviceId": pair["device_id"] or "",
            "deviceName": pair["device_name"] or "",
            "publicKey": pair["public_key"] or "",
            "expiresAt": iso_from_epoch(pair["expires_at"]),
            "serverTime": iso_now(),
        })

    def cancel_pairing(self) -> None:
        cleanup_expired_pairs(self.server.db_path)
        cleanup_expired_space_join_codes(self.server.db_path)
        payload = self.read_json()
        code = normalize_pair_code(payload.get("code"))
        pair_token = str(payload.get("pairToken") or "")
        if not code or not pair_token:
            self.send_json({"error": "missing pair credentials", "code": "missing_pair_credentials"}, status=HTTPStatus.BAD_REQUEST)
            return

        cancelled = cancel_pair(self.server.db_path, code, pair_token, iso_now())
        self.send_json({"cancelled": cancelled})

    def confirm_pairing(self) -> None:
        cleanup_expired_pairs(self.server.db_path)
        cleanup_expired_space_join_codes(self.server.db_path)
        if not self.allow_pair_confirm_attempt():
            self.send_json(
                {"error": "too many pair attempts, try again later", "code": "pair_rate_limited"},
                status=HTTPStatus.TOO_MANY_REQUESTS,
            )
            return
        payload = self.read_json()
        app_version_code = int_or_none(payload.get("appVersionCode"))
        platform = str(payload.get("platform") or "android")[:24].lower()
        if is_app_version_too_old(platform, app_version_code, self.server.min_android_version_code):
            self.send_json(
                {
                    "error": "app version too old",
                    "code": "app_version_too_old",
                    "minAndroidVersionCode": self.server.min_android_version_code,
                },
                status=HTTPStatus.UPGRADE_REQUIRED,
            )
            return

        app_token = self.bearer_token()
        if not app_token or len(app_token) < 16:
            self.send_unauthorized()
            return
        app_token_hash = token_hash(app_token)
        app_auth = find_app_token(self.server.db_path, app_token_hash)
        if app_auth is not None and app_auth["revoked_at"]:
            self.send_unauthorized()
            return
        if app_auth is not None:
            account_key = app_auth["account_key"]
            client_id = app_auth["client_id"] or ("app_pair_" + app_token_hash[:16])
            client_name = app_auth["client_name"] or "Haoleme App"
        else:
            account_key = app_token_hash
            client_id = "app_pair_" + app_token_hash[:16]
            client_name = "Haoleme App"

        code = normalize_pair_code(payload.get("code"))
        if not code:
            self.send_json({"error": "missing code", "code": "missing_pair_code"}, status=HTTPStatus.BAD_REQUEST)
            return

        pair = get_pair(self.server.db_path, code)
        if pair is None:
            self.send_json(
                {"error": "pair code expired or not found", "code": "pair_code_expired"},
                status=HTTPStatus.NOT_FOUND,
            )
            return
        if pair["status"] == "cancelled":
            self.send_json({"error": "pair code cancelled", "code": "pair_code_cancelled"}, status=HTTPStatus.GONE)
            return
        if pair["status"] != "pending":
            self.send_json({"error": "pair code already used", "code": "pair_code_used"}, status=HTTPStatus.CONFLICT)
            return

        confirmed_at = iso_now()
        device_token = secrets.token_urlsafe(32)
        encrypted_account_key = str(payload.get("encryptedAccountKey") or "").strip()
        encrypted_account_key_algorithm = str(payload.get("encryptedAccountKeyAlgorithm") or "")[:40]
        e2ee_version = int_or_none(payload.get("e2eeVersion"))
        confirmed_device_id = pair["device_id"] or ""
        confirmed_device_name = pair["device_name"] or "好了么 CLI"
        pair_machine_id = normalize_machine_id(safe_row_get(pair, "machine_id"))
        replace_device_id = str(payload.get("replaceDeviceId") or "").strip()
        reuse_device = None
        if pair_machine_id:
            reuse_device = get_device_by_machine_id(self.server.db_path, account_key, pair_machine_id, include_revoked=True)
        if reuse_device is None and is_valid_device_id(replace_device_id) and replace_device_id == confirmed_device_id:
            reuse_device = get_device(self.server.db_path, account_key, replace_device_id, include_revoked=True)
        if reuse_device is None:
            reuse_device = get_device(self.server.db_path, account_key, confirmed_device_id, include_revoked=True)
        if reuse_device is not None:
            confirmed_device_id = reuse_device["id"]
            confirmed_device_name = reuse_device["name"] or confirmed_device_name
        confirm_pair(
            self.server.db_path,
            code,
            device_token,
            confirmed_device_id,
            confirmed_device_name,
            app_version_code,
            str(payload.get("appVersionName") or "")[:40],
            platform,
            confirmed_at,
            encrypted_account_key,
            encrypted_account_key_algorithm,
            e2ee_version,
        )
        store_app_token(
            self.server.db_path,
            account_key,
            client_id,
            client_name,
            platform,
            app_token,
            confirmed_at,
        )
        upsert_device(
            self.server.db_path,
            account_key,
            confirmed_device_id,
            confirmed_device_name,
            confirmed_at,
            machine_id=pair_machine_id,
        )
        store_device_token(
            self.server.db_path,
            account_key,
            confirmed_device_id,
            confirmed_device_name,
            device_token,
            confirmed_at,
        )
        self.send_json({
            "ok": True,
            "account": "default",
            "deviceId": confirmed_device_id,
            "deviceName": confirmed_device_name,
            "pairedAt": confirmed_at,
            "serverTime": iso_now(),
        })

    def share_sync_space(self, auth: AuthContext) -> None:
        cleanup_expired_space_join_codes(self.server.db_path)
        payload = self.read_json()
        encryption_key = str(payload.get("encryptionKey") or "").strip()
        client_name = str(payload.get("clientName") or "")[:80]
        now = time.time()
        for _attempt in range(10):
            code = f"{secrets.randbelow(1000000):06d}"
            share_token = secrets.token_urlsafe(24)
            if create_space_join_code(
                self.server.db_path,
                code,
                share_token,
                auth.account_key,
                auth.token_hash,
                now,
                encryption_key,
                client_name,
            ):
                self.send_json({
                    "code": code,
                    "shareToken": share_token,
                    "expiresIn": SPACE_JOIN_CODE_TTL_SECONDS,
                    "expiresAt": iso_from_epoch(now + SPACE_JOIN_CODE_TTL_SECONDS),
                    "spaceId": sync_space_id(auth.account_key),
                    "serverTime": iso_now(),
                })
                return
        self.send_json(
            {"error": "could not allocate shared space code", "code": "space_code_unavailable"},
            status=HTTPStatus.SERVICE_UNAVAILABLE,
        )

    def join_sync_space(self) -> None:
        cleanup_expired_space_join_codes(self.server.db_path)
        if not self.allow_pair_confirm_attempt():
            self.send_json(
                {"error": "too many join attempts, try again later", "code": "space_rate_limited"},
                status=HTTPStatus.TOO_MANY_REQUESTS,
            )
            return
        payload = self.read_json()
        app_version_code = int_or_none(payload.get("appVersionCode"))
        platform = str(payload.get("platform") or "android")[:24].lower()
        if is_app_version_too_old(platform, app_version_code, self.server.min_android_version_code):
            self.send_json(
                {
                    "error": "app version too old",
                    "code": "app_version_too_old",
                    "minAndroidVersionCode": self.server.min_android_version_code,
                },
                status=HTTPStatus.UPGRADE_REQUIRED,
            )
            return

        code = normalize_pair_code(payload.get("code"))
        share_token = str(payload.get("shareToken") or "").strip()
        join = get_space_join_code(self.server.db_path, code)
        if join is None:
            self.send_json(
                {"error": "shared space code expired or not found", "code": "space_code_expired"},
                status=HTTPStatus.NOT_FOUND,
            )
            return
        if join["status"] != "pending":
            self.send_json({"error": "shared space code already used", "code": "space_code_used"}, status=HTTPStatus.CONFLICT)
            return
        if share_token and not share_token_matches(join["share_token"], share_token):
            self.send_json({"error": "invalid shared space QR token", "code": "space_share_token_invalid"}, status=HTTPStatus.FORBIDDEN)
            return

        client_token = secrets.token_urlsafe(32)
        client_id = "app_" + secrets.token_urlsafe(12).replace("-", "_")
        client_name = str(payload.get("clientName") or "Haoleme App")[:80]
        joined_at = iso_now()
        if not consume_space_join_code(self.server.db_path, code, joined_at):
            self.send_json({"error": "shared space code already used", "code": "space_code_used"}, status=HTTPStatus.CONFLICT)
            return
        store_app_token(
            self.server.db_path,
            join["account_key"],
            client_id,
            client_name,
            platform,
            client_token,
            joined_at,
        )
        self.send_json({
            "ok": True,
            "account": "sync-space",
            "token": client_token,
            "clientId": client_id,
            "clientName": client_name,
            "spaceId": sync_space_id(join["account_key"]),
            "joinedAt": joined_at,
            "serverTime": iso_now(),
            "encryptionKey": join["encryption_key"] or "",
        })

    def allow_pair_confirm_attempt(self) -> bool:
        now = time.time()
        remote = self.remote_addr()
        cutoff = now - PAIR_CONFIRM_RATE_WINDOW_SECONDS
        with self.server.pair_confirm_attempts_lock:
            attempts = [value for value in self.server.pair_confirm_attempts.get(remote, []) if value >= cutoff]
            if len(attempts) >= PAIR_CONFIRM_RATE_LIMIT:
                self.server.pair_confirm_attempts[remote] = attempts
                return False
            attempts.append(now)
            self.server.pair_confirm_attempts[remote] = attempts
            return True

    def allow_pair_start_attempt(self) -> bool:
        remote = self.remote_addr()
        return allow_rate(
            self.server.pair_start_attempts,
            self.server.pair_start_attempts_lock,
            remote,
            PAIR_START_RATE_LIMIT,
            PAIR_CONFIRM_RATE_WINDOW_SECONDS,
        )

    def allow_app_register_attempt(self) -> bool:
        remote = self.remote_addr()
        return allow_rate(
            self.server.app_register_attempts,
            self.server.app_register_attempts_lock,
            remote,
            APP_REGISTER_RATE_LIMIT,
            PAIR_CONFIRM_RATE_WINDOW_SECONDS,
        )

    def allow_read_attempt(self, auth: AuthContext) -> bool:
        remote = self.remote_addr()
        key = f"{auth.token_hash}:{remote}"
        return allow_rate(self.server.read_attempts, self.server.read_attempts_lock, key, READ_RATE_LIMIT, READ_RATE_WINDOW_SECONDS)

    def allow_write_attempt(self, auth: AuthContext) -> bool:
        remote = self.remote_addr()
        key = f"{auth.token_hash}:{remote}"
        return allow_rate(self.server.write_attempts, self.server.write_attempts_lock, key, WRITE_RATE_LIMIT, READ_RATE_WINDOW_SECONDS)

    def send_unauthorized(self) -> None:
        remote = self.remote_addr()
        allowed = allow_rate(
            self.server.auth_failure_attempts,
            self.server.auth_failure_attempts_lock,
            remote,
            AUTH_FAILURE_RATE_LIMIT,
            READ_RATE_WINDOW_SECONDS,
        )
        if not allowed:
            self.send_json({"error": "too many authentication failures", "code": "auth_rate_limited"}, status=HTTPStatus.TOO_MANY_REQUESTS)
            return
        self.send_json({"error": "unauthorized", "code": "unauthorized"}, status=HTTPStatus.UNAUTHORIZED)

    def remote_addr(self) -> str:
        peer = self.client_address[0] if self.client_address else ""
        # Trust forwarded client IP only from the local reverse proxy (nginx on
        # loopback), so rate limiting and logging see real clients, not 127.0.0.1.
        if peer in ("127.0.0.1", "::1"):
            real = (self.headers.get("X-Real-IP") or "").strip()
            if real:
                return real
            xff = (self.headers.get("X-Forwarded-For") or "").split(",")[0].strip()
            if xff:
                return xff
        return peer or "unknown"

    def send_stats_page(self, parsed) -> None:
        configured = server_stats_token()
        query = parse_qs(parsed.query)
        provided = (query.get("token", [""])[0] or self.bearer_token() or "").strip()
        if not configured:
            self.send_json(
                {"error": "stats endpoint not configured; set HAOLEME_STATS_TOKEN", "code": "stats_disabled"},
                status=HTTPStatus.NOT_FOUND,
            )
            return
        if not provided or not secrets.compare_digest(provided, configured):
            self.send_json({"error": "forbidden", "code": "forbidden"}, status=HTTPStatus.FORBIDDEN)
            return
        stats = active_user_stats(self.server.db_path)
        if parsed.path == "/api/admin/stats" or query.get("format", [""])[0] == "json":
            self.send_json(stats)
            return
        body = render_stats_html(stats).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        self.wfile.write(body)

    def authenticated_context(self, allow_legacy: bool = True) -> AuthContext | None:
        token = self.bearer_token()
        if not token or len(token) < 16:
            return None
        token_hash_value = token_hash(token)
        app_token = find_app_token(self.server.db_path, token_hash_value)
        if app_token is not None:
            if app_token["revoked_at"]:
                return None
            return AuthContext(
                account_key=app_token["account_key"],
                token_hash=token_hash_value,
                scope="admin",
                device_id=app_token["client_id"],
                device_name=app_token["client_name"],
            )
        stored = find_device_token(self.server.db_path, token_hash_value)
        if stored is not None:
            if stored["revoked_at"]:
                return None
            return AuthContext(
                account_key=stored["account_key"],
                token_hash=token_hash_value,
                scope=stored["scope"],
                device_id=stored["device_id"],
                device_name=stored["device_name"],
            )
        if allow_legacy and legacy_admin_token_allowed(self.server.db_path, token_hash_value):
            store_app_token(
                self.server.db_path,
                token_hash_value,
                "app_legacy_" + token_hash_value[:16],
                "Migrated App",
                "legacy",
                token,
                iso_now(),
            )
            return AuthContext(account_key=token_hash_value, token_hash=token_hash_value, scope="admin")
        return None

    def bearer_token(self) -> str:
        auth = self.headers.get("Authorization", "")
        return auth[7:].strip() if auth.lower().startswith("bearer ") else ""

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length") or "0")
        if length <= 0:
            return {}
        if length > MAX_JSON_BODY_BYTES:
            raise RequestBodyTooLarge("request body too large")
        raw = self.rfile.read(length).decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw)
            return payload if isinstance(payload, dict) else {}
        except json.JSONDecodeError:
            return {}

    def send_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_cors_headers()
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, OSError):
            return

    def send_download(self, raw_name: str, head_only: bool = False) -> None:
        name = unquote(raw_name).strip("/")
        if not name or "/" in name or "\\" in name or name.startswith("."):
            self.send_json({"error": "download not found", "code": "download_not_found"}, status=HTTPStatus.NOT_FOUND)
            return
        path = self.server.db_path.parent / DOWNLOADS_DIR_NAME / name
        if not path.is_file():
            self.send_json({"error": "download not found", "code": "download_not_found"}, status=HTTPStatus.NOT_FOUND)
            return
        file_size = path.stat().st_size
        range_header = self.headers.get("Range", "").strip()
        start = 0
        end = file_size - 1
        partial = False
        if range_header.startswith("bytes="):
            requested = remove_prefix(range_header, "bytes=").split(",", 1)[0].strip()
            if "-" in requested:
                raw_start, raw_end = requested.split("-", 1)
                try:
                    if raw_start:
                        start = int(raw_start)
                        end = int(raw_end) if raw_end else file_size - 1
                    elif raw_end:
                        suffix_length = int(raw_end)
                        start = max(file_size - suffix_length, 0)
                        end = file_size - 1
                    if start < 0 or end < start or start >= file_size:
                        self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        self.send_header("Content-Range", f"bytes */{file_size}")
                        self.send_header("Accept-Ranges", "bytes")
                        self.end_headers()
                        return
                    end = min(end, file_size - 1)
                    partial = True
                except ValueError:
                    start = 0
                    end = file_size - 1
                    partial = False
        content_type = "application/vnd.android.package-archive" if name.endswith(".apk") else "application/octet-stream"
        content_length = end - start + 1
        self.send_response(HTTPStatus.PARTIAL_CONTENT if partial else HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(content_length))
        self.send_header("Accept-Ranges", "bytes")
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
        self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        self.send_header("Cache-Control", "public, max-age=3600")
        self.send_cors_headers()
        self.end_headers()
        if head_only:
            return
        with path.open("rb") as file:
            file.seek(start)
            remaining = content_length
            while True:
                if remaining <= 0:
                    break
                chunk = file.read(min(1024 * 256, remaining))
                if not chunk:
                    break
                remaining -= len(chunk)
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError, OSError):
                    return

    def send_cors_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Haoleme-Account")

    def log_request(self, code: int | str = "-", size: int | str = "-") -> None:
        path = getattr(self, "path", "")
        if "?" in path:
            path = path.split("?", 1)[0]
        cloud_log({
            "ts": iso_now(),
            "event": "request",
            "remote": self.remote_addr(),
            "method": self.command,
            "path": path,
            "status": int(code) if str(code).isdigit() else code,
            "size": int(size) if str(size).isdigit() else size,
            "durationMs": int((time.time() - getattr(self, "request_started_at", time.time())) * 1000),
        })

    def log_message(self, fmt: str, *args: object) -> None:
        cloud_log({
            "ts": iso_now(),
            "event": "server",
            "remote": self.remote_addr(),
            "message": fmt % args,
        })


def init_db(db_path: Path) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    with connect(db_path) as db:
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS pairs (
                code TEXT PRIMARY KEY,
                pair_token TEXT NOT NULL,
                device_id TEXT NOT NULL DEFAULT '',
                device_name TEXT NOT NULL,
                machine_id TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL,
                account TEXT,
                token TEXT,
                created_at REAL NOT NULL,
                expires_at REAL NOT NULL,
                confirmed_at TEXT,
                app_version_code INTEGER,
                app_version_name TEXT,
                platform TEXT,
                public_key TEXT NOT NULL DEFAULT '',
                encrypted_account_key TEXT NOT NULL DEFAULT '',
                encrypted_account_key_algorithm TEXT NOT NULL DEFAULT '',
                e2ee_version INTEGER
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS runs (
                account_key TEXT NOT NULL,
                id TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT '',
                device_id TEXT NOT NULL DEFAULT '',
                device_name TEXT NOT NULL DEFAULT '',
                project TEXT NOT NULL DEFAULT '',
                payload TEXT NOT NULL,
                PRIMARY KEY (account_key, id)
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS devices (
                account_key TEXT NOT NULL,
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_seen_at TEXT NOT NULL,
                manual_name INTEGER NOT NULL DEFAULT 0,
                machine_id TEXT NOT NULL DEFAULT '',
                revoked_at TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (account_key, id)
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS device_tokens (
                token_hash TEXT PRIMARY KEY,
                account_key TEXT NOT NULL,
                device_id TEXT NOT NULL,
                device_name TEXT NOT NULL,
                scope TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_used_at TEXT NOT NULL,
                revoked_at TEXT NOT NULL DEFAULT ''
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS app_tokens (
                token_hash TEXT PRIMARY KEY,
                account_key TEXT NOT NULL,
                client_id TEXT NOT NULL,
                client_name TEXT NOT NULL,
                platform TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_used_at TEXT NOT NULL,
                revoked_at TEXT NOT NULL DEFAULT ''
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS space_join_codes (
                code TEXT PRIMARY KEY,
                share_token TEXT NOT NULL,
                account_key TEXT NOT NULL,
                created_by_token_hash TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at REAL NOT NULL,
                expires_at REAL NOT NULL,
                confirmed_at TEXT NOT NULL DEFAULT '',
                encryption_key TEXT NOT NULL DEFAULT '',
                client_name TEXT NOT NULL DEFAULT ''
            )
            """
        )
        ensure_column(db, "pairs", "device_id", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "pairs", "machine_id", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "pairs", "public_key", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "pairs", "encrypted_account_key", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "pairs", "encrypted_account_key_algorithm", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "pairs", "e2ee_version", "INTEGER")
        ensure_column(db, "runs", "status", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "runs", "device_id", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "runs", "device_name", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "runs", "project", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "devices", "manual_name", "INTEGER NOT NULL DEFAULT 0")
        ensure_column(db, "devices", "machine_id", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "devices", "revoked_at", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "devices", "gpus", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "devices", "gpus_updated_at", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "devices", "cpu", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "devices", "cpu_updated_at", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "app_tokens", "revoked_at", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "space_join_codes", "encryption_key", "TEXT NOT NULL DEFAULT ''")
        ensure_column(db, "space_join_codes", "client_name", "TEXT NOT NULL DEFAULT ''")
        db.execute("CREATE INDEX IF NOT EXISTS idx_runs_account_updated ON runs(account_key, updated_at DESC)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_runs_account_status_updated ON runs(account_key, status, updated_at DESC)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_runs_account_device_updated ON runs(account_key, device_id, updated_at DESC)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_runs_account_project_updated ON runs(account_key, project, updated_at DESC)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_devices_account_seen ON devices(account_key, revoked_at, last_seen_at DESC)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_devices_account_machine ON devices(account_key, machine_id)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_device_tokens_device ON device_tokens(account_key, device_id)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_app_tokens_account ON app_tokens(account_key, revoked_at, last_used_at DESC)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_space_join_codes_account ON space_join_codes(account_key, status, expires_at)")


def connect(db_path: Path) -> sqlite3.Connection:
    db = sqlite3.connect(db_path)
    db.row_factory = sqlite3.Row
    return db


def ensure_column(db: sqlite3.Connection, table: str, column: str, declaration: str) -> None:
    columns = {row["name"] for row in db.execute(f"PRAGMA table_info({table})").fetchall()}
    if column not in columns:
        db.execute(f"ALTER TABLE {table} ADD COLUMN {column} {declaration}")


def health_payload(db_path: Path, min_android_version_code: int, detailed: bool = True) -> dict[str, Any]:
    db_ok = False
    db_error = ""
    stats: dict[str, int] = {}
    try:
        init_db(db_path)
        with connect(db_path) as db:
            db.execute("PRAGMA quick_check").fetchone()
            for table in ("runs", "devices", "device_tokens", "app_tokens", "pairs", "space_join_codes"):
                row = db.execute(f"SELECT COUNT(*) AS count FROM {table}").fetchone()
                stats[table] = int(row["count"]) if row is not None else 0
        db_ok = True
    except Exception as exc:
        db_error = str(exc)

    data_dir = db_path.parent
    disk_ok = False
    disk_error = ""
    free_bytes = 0
    try:
        data_dir.mkdir(parents=True, exist_ok=True)
        usage = shutil.disk_usage(data_dir)
        free_bytes = int(usage.free)
        disk_ok = free_bytes > 64 * 1024 * 1024
    except Exception as exc:
        disk_error = str(exc)

    ok = db_ok and disk_ok
    payload: dict[str, Any] = {
        "ok": ok,
        "service": "haoleme-cloud",
        "version": __version__,
        "time": iso_now(),
        "pairing": {
            "minAndroidVersionCode": min_android_version_code,
            "pairCodeDigits": 6,
            "expiresIn": PAIR_TTL_SECONDS,
        },
        "security": {
            "e2eeRequired": server_requires_e2ee(),
            "legacyAdminTokens": server_allows_legacy_admin_tokens(),
            "legacyExistingAccounts": server_allows_existing_legacy_accounts(),
        },
    }
    if detailed:
        payload["storage"] = {
            "engine": "sqlite",
            "ok": db_ok,
            "path": str(db_path),
            "error": db_error,
            "stats": stats,
        }
        payload["disk"] = {
            "ok": disk_ok,
            "freeBytes": free_bytes,
            "error": disk_error,
        }
    return payload


def backup_database(db_path: Path, backup_dir: Path, keep: int = DEFAULT_BACKUP_KEEP) -> Path:
    init_db(db_path)
    backup_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_path = backup_dir / f"haoleme-cloud-{stamp}.db"
    with connect(db_path) as source:
        with sqlite3.connect(backup_path) as target:
            source.backup(target)
    backup_path.chmod(0o600)
    verified = verify_sqlite_database(backup_path)
    if not verified["ok"]:
        unlink_missing(backup_path)
        raise RuntimeError(f"backup verification failed: {verified.get('error') or verified.get('quickCheck')}")
    write_backup_checksum(backup_path)
    prune_backups(backup_dir, keep)
    return backup_path


def prune_backups(backup_dir: Path, keep: int) -> None:
    if keep <= 0:
        return
    backups = sorted(backup_dir.glob("haoleme-cloud-*.db"), key=lambda path: path.stat().st_mtime, reverse=True)
    for old in backups[keep:]:
        unlink_missing(old.with_suffix(old.suffix + ".sha256"))
        unlink_missing(old)


def verify_sqlite_database(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {"ok": False, "path": str(path), "error": "file not found", "sizeBytes": 0}
    size = path.stat().st_size
    try:
        with sqlite3.connect(path) as db:
            row = db.execute("PRAGMA quick_check").fetchone()
            quick_check = str(row[0] if row else "")
        return {
            "ok": quick_check.lower() == "ok",
            "path": str(path),
            "sizeBytes": size,
            "quickCheck": quick_check,
            "error": "" if quick_check.lower() == "ok" else quick_check,
        }
    except Exception as exc:
        return {"ok": False, "path": str(path), "sizeBytes": size, "quickCheck": "", "error": str(exc)}


def write_backup_checksum(path: Path) -> Path:
    checksum = sha256_file(path)
    checksum_path = path.with_suffix(path.suffix + ".sha256")
    checksum_path.write_text(f"{checksum}  {path.name}\n", encoding="utf-8")
    checksum_path.chmod(0o600)
    return checksum_path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        while True:
            chunk = file.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def latest_backup_status(backup_dir: Path, max_age_hours: int = DEFAULT_MONITOR_MAX_BACKUP_AGE_HOURS) -> dict[str, Any]:
    backups = sorted(backup_dir.glob("haoleme-cloud-*.db"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not backups:
        return {"ok": False, "path": "", "ageSeconds": None, "checksumOk": False, "error": "no backups found"}
    latest = backups[0]
    age_seconds = max(0, int(time.time() - latest.stat().st_mtime))
    verify = verify_sqlite_database(latest)
    checksum_path = latest.with_suffix(latest.suffix + ".sha256")
    checksum_ok = False
    checksum_error = ""
    if checksum_path.is_file():
        expected = checksum_path.read_text(encoding="utf-8", errors="replace").split()[0].strip()
        checksum_ok = bool(expected) and secrets.compare_digest(expected, sha256_file(latest))
        if not checksum_ok:
            checksum_error = "checksum mismatch"
    else:
        checksum_error = "checksum file missing"
    age_ok = age_seconds <= max_age_hours * 3600
    ok = bool(verify["ok"] and checksum_ok and age_ok)
    error = ""
    if not verify["ok"]:
        error = str(verify.get("error") or verify.get("quickCheck") or "backup verification failed")
    elif not checksum_ok:
        error = checksum_error
    elif not age_ok:
        error = f"latest backup is older than {max_age_hours}h"
    return {
        "ok": ok,
        "path": str(latest),
        "ageSeconds": age_seconds,
        "maxAgeHours": max_age_hours,
        "sizeBytes": latest.stat().st_size,
        "quickCheck": verify.get("quickCheck", ""),
        "checksumPath": str(checksum_path),
        "checksumOk": checksum_ok,
        "error": error,
    }


def monitor_payload(
    db_path: Path,
    backup_dir: Path,
    min_android_version_code: int,
    min_free_bytes: int = DEFAULT_MONITOR_MIN_FREE_BYTES,
    max_backup_age_hours: int = DEFAULT_MONITOR_MAX_BACKUP_AGE_HOURS,
) -> dict[str, Any]:
    health = health_payload(db_path, min_android_version_code, detailed=True)
    disk = health.get("disk") if isinstance(health.get("disk"), dict) else {}
    free_bytes = int(disk.get("freeBytes") or 0)
    disk["minFreeBytes"] = min_free_bytes
    disk["ok"] = bool(disk.get("ok")) and free_bytes >= min_free_bytes
    if free_bytes and free_bytes < min_free_bytes:
        disk["error"] = f"free disk below {min_free_bytes} bytes"
    audit = permission_audit(db_path)
    backup = latest_backup_status(backup_dir, max_backup_age_hours)
    checks = {
        "health": bool(health.get("ok")) and bool(disk.get("ok")),
        "permissions": bool(audit.get("ok")),
        "backup": bool(backup.get("ok")),
    }
    ok = all(checks.values())
    return {
        "ok": ok,
        "service": "haoleme-cloud",
        "version": __version__,
        "time": iso_now(),
        "checks": checks,
        "health": health,
        "permissions": audit,
        "backup": backup,
    }


def send_monitor_alert(payload: dict[str, Any], webhook_url: str, timeout: float = 5.0) -> dict[str, Any]:
    webhook_url = webhook_url.strip()
    if not webhook_url:
        return {"sent": False, "error": "no webhook configured"}
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        webhook_url,
        data=body,
        headers={"Content-Type": "application/json; charset=utf-8", "User-Agent": f"haoleme-cloud/{__version__}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return {"sent": 200 <= response.status < 300, "status": response.status, "error": ""}
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        return {"sent": False, "error": str(exc)}


def permission_audit(db_path: Path) -> dict[str, Any]:
    init_db(db_path)
    checks: list[dict[str, Any]] = []

    def add(name: str, ok: bool, detail: str = "") -> None:
        checks.append({"name": name, "ok": ok, "detail": detail})

    with connect(db_path) as db:
        add("runs primary key is account scoped", primary_key_columns(db, "runs") == ["account_key", "id"])
        add("devices primary key is account scoped", primary_key_columns(db, "devices") == ["account_key", "id"])
        add("device tokens are hashed", "token_hash" in table_columns(db, "device_tokens"))
        add("app tokens are hashed", "token_hash" in table_columns(db, "app_tokens"))
        row = db.execute("SELECT COUNT(*) AS count FROM device_tokens WHERE scope != 'write'").fetchone()
        non_write = int(row["count"]) if row is not None else 0
        add("device tokens are write scoped", non_write == 0, f"{non_write} non-write token(s)")
        row = db.execute("SELECT COUNT(*) AS count FROM app_tokens WHERE account_key = '' OR token_hash = ''").fetchone()
        bad_app_tokens = int(row["count"]) if row is not None else 0
        add("app tokens have account and token hash", bad_app_tokens == 0, f"{bad_app_tokens} bad app token row(s)")
        row = db.execute("SELECT COUNT(*) AS count FROM devices WHERE account_key = '' OR id = ''").fetchone()
        bad_devices = int(row["count"]) if row is not None else 0
        add("devices have account and id", bad_devices == 0, f"{bad_devices} bad device row(s)")
        row = db.execute("SELECT COUNT(*) AS count FROM runs WHERE account_key = '' OR id = ''").fetchone()
        bad_runs = int(row["count"]) if row is not None else 0
        add("runs have account and id", bad_runs == 0, f"{bad_runs} bad run row(s)")

    ok = all(check["ok"] for check in checks)
    return {"ok": ok, "checks": checks}


def table_columns(db: sqlite3.Connection, table: str) -> list[str]:
    return [row["name"] for row in db.execute(f"PRAGMA table_info({table})").fetchall()]


def primary_key_columns(db: sqlite3.Connection, table: str) -> list[str]:
    rows = db.execute(f"PRAGMA table_info({table})").fetchall()
    return [row["name"] for row in sorted((row for row in rows if row["pk"]), key=lambda row: row["pk"])]


def cloud_log(payload: dict[str, Any]) -> None:
    line = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    log_path = (os.environ.get("HAOLEME_CLOUD_LOG") or os.environ.get("REMINDER_CLOUD_LOG", "")).strip()
    if log_path:
        path = Path(log_path).expanduser()
        path.parent.mkdir(parents=True, exist_ok=True)
        rotate_cloud_log_if_needed(path)
        with path.open("a", encoding="utf-8") as file:
            file.write(line + "\n")
        return
    print(line, file=sys.stderr, flush=True)


def rotate_cloud_log_if_needed(path: Path) -> None:
    try:
        max_bytes = int(os.environ.get("HAOLEME_CLOUD_LOG_MAX_BYTES", str(DEFAULT_LOG_MAX_BYTES)))
    except ValueError:
        max_bytes = DEFAULT_LOG_MAX_BYTES
    if max_bytes <= 0:
        return
    try:
        if not path.exists() or path.stat().st_size < max_bytes:
            return
        rotated = path.with_name(path.name + ".1")
        unlink_missing(rotated)
        path.replace(rotated)
    except OSError:
        return


def create_pair(
    db_path: Path,
    code: str,
    pair_token: str,
    device_id: str,
    device_name: str,
    now: float,
    public_key: str = "",
    machine_id: str = "",
) -> bool:
    try:
        with connect(db_path) as db:
            db.execute(
                """
                INSERT INTO pairs(code, pair_token, device_id, device_name, machine_id, status, account, created_at, expires_at, public_key)
                VALUES (?, ?, ?, ?, ?, 'pending', 'default', ?, ?, ?)
                """,
                (code, token_hash(pair_token), device_id, device_name, normalize_machine_id(machine_id), now, now + PAIR_TTL_SECONDS, public_key[:4096]),
            )
        return True
    except sqlite3.IntegrityError:
        return False


def get_pair(db_path: Path, code: str) -> sqlite3.Row | None:
    if not code:
        return None
    with connect(db_path) as db:
        return db.execute("SELECT * FROM pairs WHERE code = ?", (code,)).fetchone()


def delete_pair(db_path: Path, code: str) -> None:
    if not code:
        return
    with connect(db_path) as db:
        db.execute("DELETE FROM pairs WHERE code = ?", (code,))


def confirm_pair(
    db_path: Path,
    code: str,
    token: str,
    device_id: str,
    device_name: str,
    app_version_code: int | None,
    app_version_name: str,
    platform: str,
    confirmed_at: str,
    encrypted_account_key: str = "",
    encrypted_account_key_algorithm: str = "",
    e2ee_version: int | None = None,
) -> None:
    with connect(db_path) as db:
        db.execute(
            """
            UPDATE pairs
            SET status = 'confirmed',
                account = 'default',
                token = ?,
                device_id = ?,
                device_name = ?,
                confirmed_at = ?,
                app_version_code = ?,
                app_version_name = ?,
                platform = ?,
                encrypted_account_key = ?,
                encrypted_account_key_algorithm = ?,
                e2ee_version = ?
            WHERE code = ?
            """,
            (
                token,
                device_id,
                device_name,
                confirmed_at,
                app_version_code,
                app_version_name,
                platform,
                encrypted_account_key[:4096],
                encrypted_account_key_algorithm[:40],
                e2ee_version,
                code,
            ),
        )


def cancel_pair(db_path: Path, code: str, pair_token: str, cancelled_at: str) -> bool:
    if not code or not pair_token:
        return False
    with connect(db_path) as db:
        pair = db.execute("SELECT pair_token FROM pairs WHERE code = ? AND status = 'pending'", (code,)).fetchone()
        if pair is None or not pair_token_matches(pair["pair_token"], pair_token):
            return False
        cursor = db.execute(
            """
            UPDATE pairs
            SET status = 'cancelled',
                confirmed_at = ?
            WHERE code = ? AND status = 'pending'
            """,
            (cancelled_at, code),
        )
        return cursor.rowcount > 0


def cleanup_expired_pairs(db_path: Path) -> None:
    with connect(db_path) as db:
        db.execute("DELETE FROM pairs WHERE expires_at < ?", (time.time(),))


def create_space_join_code(
    db_path: Path,
    code: str,
    share_token: str,
    account_key: str,
    created_by_token_hash: str,
    now: float,
    encryption_key: str = "",
    client_name: str = "",
) -> bool:
    if not code or not share_token or not account_key:
        return False
    try:
        with connect(db_path) as db:
            db.execute(
                """
                INSERT INTO space_join_codes(
                    code, share_token, account_key, created_by_token_hash, status,
                    created_at, expires_at, encryption_key, client_name
                )
                VALUES (?, ?, ?, ?, 'pending', ?, ?, ?, ?)
                """,
                (
                    code,
                    token_hash(share_token),
                    account_key,
                    created_by_token_hash,
                    now,
                    now + SPACE_JOIN_CODE_TTL_SECONDS,
                    encryption_key[:256],
                    client_name,
                ),
            )
        return True
    except sqlite3.IntegrityError:
        return False


def get_space_join_code(db_path: Path, code: str) -> sqlite3.Row | None:
    if not code:
        return None
    with connect(db_path) as db:
        return db.execute("SELECT * FROM space_join_codes WHERE code = ?", (code,)).fetchone()


def consume_space_join_code(db_path: Path, code: str, confirmed_at: str) -> bool:
    if not code:
        return False
    with connect(db_path) as db:
        cursor = db.execute(
            """
            UPDATE space_join_codes
            SET status = 'confirmed',
                confirmed_at = ?
            WHERE code = ? AND status = 'pending'
            """,
            (confirmed_at, code),
        )
        return cursor.rowcount > 0


def cleanup_expired_space_join_codes(db_path: Path) -> None:
    with connect(db_path) as db:
        db.execute("DELETE FROM space_join_codes WHERE expires_at < ?", (time.time(),))


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def pair_token_matches(stored: str, candidate: str) -> bool:
    if not stored or not candidate:
        return False
    hashed = token_hash(candidate)
    return secrets.compare_digest(stored, hashed) or secrets.compare_digest(stored, candidate)


def share_token_matches(stored: str, candidate: str) -> bool:
    return pair_token_matches(stored, candidate)


def sync_space_id(account_key: str) -> str:
    digest = hashlib.sha256(("haoleme-space:" + account_key).encode("utf-8")).hexdigest()
    return "sp_" + digest[:16]


def store_device_token(
    db_path: Path,
    account_key: str,
    device_id: str,
    device_name: str,
    token: str,
    created_at: str,
) -> None:
    if not account_key or not device_id or not token:
        return
    with connect(db_path) as db:
        db.execute(
            """
            INSERT INTO device_tokens(token_hash, account_key, device_id, device_name, scope, created_at, last_used_at, revoked_at)
            VALUES (?, ?, ?, ?, 'write', ?, ?, '')
            ON CONFLICT(token_hash) DO UPDATE SET
                account_key = excluded.account_key,
                device_id = excluded.device_id,
                device_name = excluded.device_name,
                scope = excluded.scope,
                last_used_at = excluded.last_used_at,
                revoked_at = ''
            """,
            (token_hash(token), account_key, device_id, device_name, created_at, created_at),
        )


def store_app_token(
    db_path: Path,
    account_key: str,
    client_id: str,
    client_name: str,
    platform: str,
    token: str,
    created_at: str,
) -> None:
    if not account_key or not client_id or not token:
        return
    with connect(db_path) as db:
        db.execute(
            """
            INSERT INTO app_tokens(token_hash, account_key, client_id, client_name, platform, created_at, last_used_at, revoked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, '')
            ON CONFLICT(token_hash) DO UPDATE SET
                account_key = excluded.account_key,
                client_id = excluded.client_id,
                client_name = excluded.client_name,
                platform = excluded.platform,
                last_used_at = excluded.last_used_at,
                revoked_at = ''
            """,
            (token_hash(token), account_key, client_id, client_name, platform[:24], created_at, created_at),
        )


def find_app_token(db_path: Path, token_hash_value: str) -> sqlite3.Row | None:
    if not token_hash_value:
        return None
    with connect(db_path) as db:
        return db.execute(
            """
            SELECT token_hash, account_key, client_id, client_name, platform, revoked_at
            FROM app_tokens
            WHERE token_hash = ?
            """,
            (token_hash_value,),
        ).fetchone()


def account_has_cloud_data(db_path: Path, account_key: str) -> bool:
    if not account_key:
        return False
    with connect(db_path) as db:
        for table in ("runs", "devices", "device_tokens", "space_join_codes"):
            row = db.execute(f"SELECT 1 FROM {table} WHERE account_key = ? LIMIT 1", (account_key,)).fetchone()
            if row is not None:
                return True
    return False


def authenticate_device_token(db_path: Path, token_hash_value: str) -> sqlite3.Row | None:
    row = find_device_token(db_path, token_hash_value)
    if row is None or row["revoked_at"]:
        return None
    return row


def find_device_token(db_path: Path, token_hash_value: str) -> sqlite3.Row | None:
    if not token_hash_value:
        return None
    with connect(db_path) as db:
        return db.execute(
            """
            SELECT token_hash, account_key, device_id, device_name, scope, revoked_at
            FROM device_tokens
            WHERE token_hash = ?
            """,
            (token_hash_value,),
        ).fetchone()


def touch_token(db_path: Path, token_hash_value: str, used_at: str) -> None:
    if not token_hash_value:
        return
    with connect(db_path) as db:
        db.execute(
            "UPDATE device_tokens SET last_used_at = ? WHERE token_hash = ? AND revoked_at = ''",
            (used_at, token_hash_value),
        )
        db.execute(
            "UPDATE app_tokens SET last_used_at = ? WHERE token_hash = ? AND revoked_at = ''",
            (used_at, token_hash_value),
        )


def upsert_run(db_path: Path, account_key: str, run: dict[str, Any]) -> None:
    with connect(db_path) as db:
        existing_row = db.execute(
            "SELECT payload FROM runs WHERE account_key = ? AND id = ?",
            (account_key, run["id"]),
        ).fetchone()
        if existing_row is not None:
            try:
                existing = json.loads(existing_row["payload"])
            except json.JSONDecodeError:
                existing = {}
            if existing.get("interruptRequestedAt") and not run.get("interruptRequestedAt"):
                run["interruptRequestedAt"] = existing["interruptRequestedAt"]
            # Output is streamed via append_run_update (outputChunks / tails). A
            # full payload replace here must NOT drop it, or the final status
            # upsert on completion wipes the console. Preserve accumulated output
            # whenever this upsert didn't carry its own.
            if not run.get("outputChunks") and existing.get("outputChunks"):
                run["outputChunks"] = existing["outputChunks"]
                if not run.get("outputLength") and existing.get("outputLength"):
                    run["outputLength"] = existing["outputLength"]
            for tail in ("outputTail", "stdoutTail", "stderrTail"):
                if not run.get(tail) and existing.get(tail):
                    run[tail] = existing[tail]
        db.execute(
            """
            INSERT INTO runs(account_key, id, updated_at, status, device_id, device_name, project, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(account_key, id) DO UPDATE SET
                updated_at = excluded.updated_at,
                status = excluded.status,
                device_id = excluded.device_id,
                device_name = excluded.device_name,
                project = excluded.project,
                payload = excluded.payload
            """,
            (
                account_key,
                run["id"],
                run["updatedAt"],
                run.get("status", ""),
                run.get("deviceId", ""),
                run.get("deviceName", ""),
                normalize_project_name(run.get("project")),
                json.dumps(run, ensure_ascii=False),
            ),
        )


def list_runs(
    db_path: Path,
    account_key: str,
    limit: int,
    device_id: str = "",
    status_filter: str = "",
    project_filter: str = "",
) -> list[dict[str, Any]]:
    status_filter = normalize_status_filter(status_filter)
    project_filter = normalize_project_filter(project_filter)
    with connect(db_path) as db:
        expire_stale_running_runs(db, account_key)
        where = ["account_key = ?"]
        values: list[Any] = [account_key]
        if device_id:
            where.append("device_id = ?")
            values.append(device_id)
        if status_filter:
            if status_filter == "running":
                where.append("status IN ('created', 'running')")
            else:
                where.append("status = ?")
                values.append(status_filter)
        if project_filter == "__none__":
            where.append("project = ''")
        elif project_filter:
            where.append("project = ?")
            values.append(project_filter)
        values.append(limit)
        rows = db.execute(
            f"""
            SELECT payload FROM runs
            WHERE {" AND ".join(where)}
            ORDER BY updated_at DESC
            LIMIT ?
            """,
            values,
        ).fetchall()
        names = device_names(db, account_key)
    return [
        decode_run(
            row["payload"],
            names,
            output_limit=MAX_LIST_OUTPUT_PREVIEW,
            include_e2ee=True,
            include_output_chunks=False,
        )
        for row in rows
    ]


def list_events(db_path: Path, account_key: str, since: str | None, limit: int) -> list[dict[str, Any]]:
    with connect(db_path) as db:
        expire_stale_running_runs(db, account_key)
        if since:
            rows = db.execute(
                """
                SELECT payload FROM runs
                WHERE account_key = ? AND updated_at > ?
                ORDER BY updated_at ASC
                LIMIT ?
                """,
                (account_key, since, limit),
            ).fetchall()
        else:
            rows = db.execute(
                """
                SELECT payload FROM runs
                WHERE account_key = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """,
                (account_key, limit),
            ).fetchall()
        names = device_names(db, account_key)
    return [
        decode_run(
            row["payload"],
            names,
            output_limit=MAX_LIST_OUTPUT_PREVIEW,
            include_e2ee=True,
            include_output_chunks=False,
        )
        for row in rows
    ]


def get_run(db_path: Path, account_key: str, run_id: str) -> dict[str, Any] | None:
    with connect(db_path) as db:
        expire_stale_running_runs(db, account_key)
        row = db.execute(
            "SELECT payload FROM runs WHERE account_key = ? AND id = ?",
            (account_key, run_id),
        ).fetchone()
        names = device_names(db, account_key)
    return None if row is None else decode_run(row["payload"], names)


def append_run_update(
    db_path: Path,
    account_key: str,
    patch: dict[str, Any],
    auth: Any,
) -> dict[str, Any] | None:
    run_id = str(patch.get("id") or "").strip()
    if not run_id:
        return None
    with connect(db_path) as db:
        row = db.execute(
            "SELECT payload FROM runs WHERE account_key = ? AND id = ?",
            (account_key, run_id),
        ).fetchone()
        if row is None:
            return None
        try:
            existing = json.loads(row["payload"])
        except json.JSONDecodeError:
            existing = {}
        if auth.scope == "write":
            device_id = str(existing.get("deviceId") or "")
            if device_id and device_id != auth.device_id:
                return None
        for key in ("status", "pid", "exitCode", "endedAt", "updatedAt", "deviceId", "deviceName", "project"):
            if key in patch and patch.get(key) is not None:
                existing[key] = patch[key]
        if auth.scope == "write" and auth.device_id:
            existing["deviceId"] = auth.device_id
            if not str(existing.get("deviceName") or "").strip():
                existing["deviceName"] = auth.device_name
        if existing.get("interruptRequestedAt") and not patch.get("interruptRequestedAt"):
            pass
        elif patch.get("interruptRequestedAt"):
            existing["interruptRequestedAt"] = patch["interruptRequestedAt"]

        chunk = patch.get("e2eeOutputChunk")
        if isinstance(chunk, dict) and chunk.get("ciphertext"):
            chunks = existing.get("outputChunks")
            if not isinstance(chunks, list):
                chunks = []
            chunk_copy = {
                "v": int_or_none(chunk.get("v")) or 1,
                "alg": str(chunk.get("alg") or "AES-256-GCM")[:40],
                "nonce": str(chunk.get("nonce") or "")[:128],
                "ciphertext": str(chunk.get("ciphertext") or ""),
                "seq": len(chunks),
            }
            chunks.append(chunk_copy)
            existing["outputChunks"] = chunks[-MAX_OUTPUT_CHUNKS:]
            if patch.get("outputLength") is not None:
                existing["outputLength"] = max(0, int_or_none(patch.get("outputLength")) or 0)
        else:
            for target, delta_key in (
                ("outputTail", "outputDelta"),
                ("stdoutTail", "stdoutDelta"),
                ("stderrTail", "stderrDelta"),
            ):
                delta = str(patch.get(delta_key) or "")
                if not delta:
                    continue
                merged = str(existing.get(target) or "") + delta
                existing[target] = merged[-MAX_OUTPUT_TAIL:]
            existing["outputLength"] = len(str(existing.get("outputTail") or ""))

        stored = normalize_run(existing)
        db.execute(
            """
            INSERT INTO runs(account_key, id, updated_at, status, device_id, device_name, project, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(account_key, id) DO UPDATE SET
                updated_at = excluded.updated_at,
                status = excluded.status,
                device_id = excluded.device_id,
                device_name = excluded.device_name,
                project = excluded.project,
                payload = excluded.payload
            """,
            (
                account_key,
                stored["id"],
                stored["updatedAt"],
                stored.get("status", ""),
                stored.get("deviceId", ""),
                stored.get("deviceName", ""),
                normalize_project_name(stored.get("project")),
                json.dumps(stored, ensure_ascii=False),
            ),
        )
    if stored.get("deviceId"):
        upsert_device(
            db_path,
            account_key,
            stored.get("deviceId", ""),
            stored.get("deviceName", "") or "好了么 CLI",
            stored.get("updatedAt", "") or iso_now(),
        )
    return stored


def parse_output_since(query: dict[str, list[str]]) -> int:
    raw = first_query_value(query, "outputSince")
    if not raw:
        return 0
    try:
        return max(0, int(raw))
    except ValueError:
        return 0


def parse_output_length(query: dict[str, list[str]]) -> int:
    raw = first_query_value(query, "outputLength")
    if not raw:
        return 0
    try:
        return max(0, int(raw))
    except ValueError:
        return 0


def build_run_fetch_payload(
    run: dict[str, Any],
    *,
    output_since: int = 0,
    output_length: int = 0,
) -> dict[str, Any]:
    if output_since <= 0 and output_length <= 0:
        return {"run": run}

    slim = dict(run)
    chunks = slim.get("outputChunks")
    if isinstance(chunks, list) and chunks:
        slim["stdoutTail"] = ""
        slim["stderrTail"] = ""
        slim["outputTail"] = ""
        new_chunks = chunks[output_since:] if output_since > 0 else chunks
        slim["outputChunkCount"] = len(chunks)
        slim.pop("outputChunks", None)
        return {
            "run": slim,
            "outputChunks": new_chunks,
            "outputLength": int_or_none(slim.get("outputLength")) or 0,
            "incremental": True,
        }

    output_tail = str(slim.get("outputTail") or "")
    if output_length > 0 and output_length < len(output_tail):
        append_text = output_tail[output_length:]
    else:
        append_text = output_tail
    slim["stdoutTail"] = ""
    slim["stderrTail"] = ""
    slim["outputTail"] = ""
    payload: dict[str, Any] = {
        "run": slim,
        "outputLength": len(output_tail),
        "incremental": True,
    }
    if append_text:
        payload["outputAppend"] = append_text
    return payload


def list_pending_interrupts(db_path: Path, account_key: str, device_id: str) -> list[dict[str, Any]]:
    if not device_id:
        return []
    with connect(db_path) as db:
        expire_stale_running_runs(db, account_key)
        rows = db.execute(
            """
            SELECT id, payload FROM runs
            WHERE account_key = ? AND device_id = ? AND status IN ('created', 'running')
            ORDER BY updated_at DESC
            LIMIT 50
            """,
            (account_key, device_id),
        ).fetchall()
    interrupts: list[dict[str, Any]] = []
    for row in rows:
        try:
            run = json.loads(row["payload"])
        except json.JSONDecodeError:
            continue
        requested_at = str(run.get("interruptRequestedAt") or "").strip()
        if requested_at:
            interrupts.append({"id": row["id"], "interruptRequestedAt": requested_at})
    return interrupts


def request_run_interrupt(
    db_path: Path,
    account_key: str,
    run_id: str,
) -> tuple[dict[str, Any] | None, str | None]:
    with connect(db_path) as db:
        expire_stale_running_runs(db, account_key)
        row = db.execute(
            "SELECT payload FROM runs WHERE account_key = ? AND id = ?",
            (account_key, run_id),
        ).fetchone()
        if row is None:
            return None, "run_not_found"
        try:
            run = json.loads(row["payload"])
        except json.JSONDecodeError:
            return None, "run_not_found"
        status = str(run.get("status") or "")
        if status not in {"created", "running"}:
            return None, "run_not_active"
        if not run.get("interruptRequestedAt"):
            run["interruptRequestedAt"] = iso_now()
        stored = normalize_run(run)
        if run.get("interruptRequestedAt"):
            stored["interruptRequestedAt"] = str(run["interruptRequestedAt"])
        db.execute(
            """
            INSERT INTO runs(account_key, id, updated_at, status, device_id, device_name, project, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(account_key, id) DO UPDATE SET
                updated_at = excluded.updated_at,
                status = excluded.status,
                device_id = excluded.device_id,
                device_name = excluded.device_name,
                project = excluded.project,
                payload = excluded.payload
            """,
            (
                account_key,
                stored["id"],
                stored["updatedAt"],
                stored.get("status", ""),
                stored.get("deviceId", ""),
                stored.get("deviceName", ""),
                normalize_project_name(stored.get("project")),
                json.dumps(stored, ensure_ascii=False),
            ),
        )
    return stored, None


def delete_run(db_path: Path, account_key: str, run_id: str) -> bool:
    with connect(db_path) as db:
        cursor = db.execute("DELETE FROM runs WHERE account_key = ? AND id = ?", (account_key, run_id))
        return cursor.rowcount > 0


def delete_all_runs(db_path: Path, account_key: str) -> int:
    with connect(db_path) as db:
        cursor = db.execute("DELETE FROM runs WHERE account_key = ?", (account_key,))
        return cursor.rowcount


def delete_runs_for_device(db_path: Path, account_key: str, device_id: str) -> int:
    if not device_id:
        return 0
    with connect(db_path) as db:
        cursor = db.execute(
            "DELETE FROM runs WHERE account_key = ? AND device_id = ?",
            (account_key, device_id),
        )
        return cursor.rowcount


def delete_account(db_path: Path, account_key: str) -> int:
    if not account_key:
        return 0
    deleted = 0
    with connect(db_path) as db:
        for table in ("runs", "devices", "device_tokens", "app_tokens", "space_join_codes"):
            cursor = db.execute(f"DELETE FROM {table} WHERE account_key = ?", (account_key,))
            deleted += cursor.rowcount
    return deleted


def upsert_device(db_path: Path, account_key: str, device_id: str, name: str, seen_at: str, machine_id: str = "") -> None:
    if not device_id:
        return
    clean_machine_id = normalize_machine_id(machine_id)
    with connect(db_path) as db:
        db.execute(
            """
            INSERT INTO devices(account_key, id, name, created_at, last_seen_at, machine_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(account_key, id) DO UPDATE SET
                name = CASE
                    WHEN devices.manual_name = 1 THEN devices.name
                    ELSE excluded.name
                END,
                machine_id = CASE
                    WHEN excluded.machine_id != '' THEN excluded.machine_id
                    ELSE devices.machine_id
                END,
                last_seen_at = excluded.last_seen_at,
                revoked_at = ''
            """,
            (account_key, device_id, name or "好了么 CLI", seen_at, seen_at, clean_machine_id),
        )


def record_device_heartbeat(
    db_path: Path,
    account_key: str,
    device_id: str,
    name: str,
    seen_at: str,
    gpus: list[dict[str, Any]] | None = None,
    cpu: dict[str, Any] | None = None,
) -> dict[str, Any] | None:
    upsert_device(db_path, account_key, device_id, name, seen_at)
    if gpus is not None:
        with connect(db_path) as db:
            db.execute(
                "UPDATE devices SET gpus = ?, gpus_updated_at = ? WHERE account_key = ? AND id = ?",
                (json.dumps(gpus, ensure_ascii=False), seen_at, account_key, device_id),
            )
    if cpu is not None:
        with connect(db_path) as db:
            db.execute(
                "UPDATE devices SET cpu = ?, cpu_updated_at = ? WHERE account_key = ? AND id = ?",
                (json.dumps(cpu, ensure_ascii=False), seen_at, account_key, device_id),
            )
    return get_device(db_path, account_key, device_id)


def sanitize_gpus(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        return []
    out: list[dict[str, Any]] = []
    for item in value[:16]:
        if not isinstance(item, dict):
            continue
        out.append({
            "index": int_or_none(item.get("index")),
            "name": str(item.get("name") or "")[:80],
            "utilization": int_or_none(item.get("utilization")),
            "memoryUsed": int_or_none(item.get("memoryUsed")),
            "memoryTotal": int_or_none(item.get("memoryTotal")),
            "temperature": int_or_none(item.get("temperature")),
        })
    return out


def sanitize_cpu(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {}
    out: dict[str, Any] = {}
    utilization = int_or_none(value.get("utilization"))
    if utilization is not None:
        out["utilization"] = max(0, min(100, utilization))
    cores = int_or_none(value.get("cores"))
    if cores is not None:
        out["cores"] = max(1, min(4096, cores))
    load1 = float_or_none(value.get("load1"))
    if load1 is not None:
        out["load1"] = round(max(0.0, min(100000.0, load1)), 2)
    return out


def rename_device(db_path: Path, account_key: str, device_id: str, name: str) -> dict[str, Any] | None:
    if not device_id or not name:
        return None
    seen_at = iso_now()
    with connect(db_path) as db:
        cursor = db.execute(
            """
            UPDATE devices
            SET name = ?, manual_name = 1, last_seen_at = ?
            WHERE account_key = ? AND id = ?
            """,
            (name, seen_at, account_key, device_id),
        )
        if cursor.rowcount == 0:
            return None
        db.execute(
            "UPDATE runs SET device_name = ? WHERE account_key = ? AND device_id = ?",
            (name, account_key, device_id),
        )
        row = db.execute(
            """
            SELECT id, name, created_at, last_seen_at FROM devices
            WHERE account_key = ? AND id = ?
            """,
            (account_key, device_id),
        ).fetchone()
    if row is None:
        return None
    return format_device(row)


def get_device(db_path: Path, account_key: str, device_id: str, include_revoked: bool = False) -> dict[str, Any] | None:
    if not account_key or not device_id:
        return None
    revoked_filter = "" if include_revoked else "AND revoked_at = ''"
    with connect(db_path) as db:
        row = db.execute(
            f"""
            SELECT id, name, created_at, last_seen_at, revoked_at, gpus, gpus_updated_at, cpu, cpu_updated_at
            FROM devices
            WHERE account_key = ? AND id = ? {revoked_filter}
            """,
            (account_key, device_id),
        ).fetchone()
    return None if row is None else format_device(row)


def get_device_by_machine_id(db_path: Path, account_key: str, machine_id: str, include_revoked: bool = False) -> dict[str, Any] | None:
    clean_machine_id = normalize_machine_id(machine_id)
    if not account_key or not clean_machine_id:
        return None
    revoked_filter = "" if include_revoked else "AND revoked_at = ''"
    with connect(db_path) as db:
        row = db.execute(
            f"""
            SELECT id, name, created_at, last_seen_at, revoked_at, gpus, gpus_updated_at, cpu, cpu_updated_at
            FROM devices
            WHERE account_key = ? AND machine_id = ? {revoked_filter}
            ORDER BY last_seen_at DESC
            LIMIT 1
            """,
            (account_key, clean_machine_id),
        ).fetchone()
    return None if row is None else format_device(row)


def list_devices(db_path: Path, account_key: str) -> list[dict[str, Any]]:
    with connect(db_path) as db:
        rows = db.execute(
            """
            SELECT d.id,
                   d.name,
                   d.created_at,
                   d.last_seen_at,
                   d.revoked_at,
                   d.gpus,
                   d.gpus_updated_at,
                   d.cpu,
                   d.cpu_updated_at,
                   MAX(t.last_used_at) AS token_last_used_at
            FROM devices d
            LEFT JOIN device_tokens t
                ON t.account_key = d.account_key
               AND t.device_id = d.id
               AND t.revoked_at = ''
            WHERE d.account_key = ? AND d.revoked_at = ''
            GROUP BY d.id, d.name, d.created_at, d.last_seen_at, d.revoked_at, d.gpus, d.gpus_updated_at, d.cpu, d.cpu_updated_at
            ORDER BY last_seen_at DESC
            """,
            (account_key,),
        ).fetchall()
    return [format_device(row) for row in rows]


def format_device(row: sqlite3.Row) -> dict[str, Any]:
    gpus: list[dict[str, Any]] = []
    raw_gpus = safe_row_get(row, "gpus")
    if raw_gpus:
        try:
            parsed = json.loads(raw_gpus)
            if isinstance(parsed, list):
                gpus = parsed
        except (TypeError, ValueError):
            gpus = []
    cpu: dict[str, Any] = {}
    raw_cpu = safe_row_get(row, "cpu")
    if raw_cpu:
        try:
            parsed_cpu = json.loads(raw_cpu)
            if isinstance(parsed_cpu, dict):
                cpu = sanitize_cpu(parsed_cpu)
        except (TypeError, ValueError):
            cpu = {}
    return {
        "id": row["id"],
        "name": row["name"],
        "createdAt": row["created_at"],
        "lastSeenAt": row["last_seen_at"],
        "tokenLastUsedAt": safe_row_get(row, "token_last_used_at") or "",
        "revokedAt": safe_row_get(row, "revoked_at") or "",
        "online": is_recent_timestamp(row["last_seen_at"], DEVICE_ONLINE_WINDOW_SECONDS),
        "onlineWindowSeconds": DEVICE_ONLINE_WINDOW_SECONDS,
        "gpus": gpus,
        "gpusUpdatedAt": safe_row_get(row, "gpus_updated_at") or "",
        "cpu": cpu,
        "cpuUpdatedAt": safe_row_get(row, "cpu_updated_at") or "",
    }


def revoke_device(db_path: Path, account_key: str, device_id: str) -> bool:
    if not device_id:
        return False
    revoked_at = iso_now()
    with connect(db_path) as db:
        cursor = db.execute(
            """
            UPDATE devices
            SET revoked_at = ?, last_seen_at = ?
            WHERE account_key = ? AND id = ? AND revoked_at = ''
            """,
            (revoked_at, revoked_at, account_key, device_id),
        )
        db.execute(
            """
            UPDATE device_tokens
            SET revoked_at = ?, last_used_at = ?
            WHERE account_key = ? AND device_id = ? AND revoked_at = ''
            """,
            (revoked_at, revoked_at, account_key, device_id),
        )
        return cursor.rowcount > 0


def safe_row_get(row: sqlite3.Row, name: str) -> Any:
    try:
        return row[name]
    except (IndexError, KeyError):
        return None


def device_names(db: sqlite3.Connection, account_key: str) -> dict[str, str]:
    rows = db.execute("SELECT id, name FROM devices WHERE account_key = ?", (account_key,)).fetchall()
    return {row["id"]: row["name"] for row in rows}


def expire_stale_running_runs(db: sqlite3.Connection, account_key: str, now: str | None = None) -> int:
    # NOTE: We no longer auto-mark running/created runs as 'cancelled' just because
    # the device last_seen_at is stale (e.g. temporary network drop on the computer).
    # The command may still be executing locally.
    # The CLI heartbeat daemon on the device will:
    #   - locally check PID in reconcile_orphaned_running_runs (using is_process_running)
    #   - only cancel locally if the pid is truly dead
    #   - sync the real status (still running, or final succeeded/failed) when network recovers.
    #
    # Server should reflect the last reported status from the client.
    # This supports long-running jobs surviving transient disconnects.
    # (Previously this function would set cancelled + note "Device went offline...")
    now_value = now or iso_now()
    # We still query to keep the function, but do no mutations for runs.
    # If in future we want very-long-time cleanup, we can add a separate slow timeout.
    rows = db.execute(
        """
        SELECT r.id, r.updated_at, r.device_id, r.payload, d.last_seen_at
        FROM runs r
        LEFT JOIN devices d
            ON d.account_key = r.account_key
           AND d.id = r.device_id
           AND d.revoked_at = ''
        WHERE r.account_key = ? AND r.status IN ('created', 'running')
        """,
        (account_key,),
    ).fetchall()
    # Do not cancel here anymore.
    return 0


def decode_run(
    payload_json: str,
    names: dict[str, str],
    output_limit: int = MAX_OUTPUT_TAIL,
    include_e2ee: bool = True,
    include_output_chunks: bool = True,
    output_chunk_limit: int | None = None,
) -> dict[str, Any]:
    run = json.loads(payload_json)
    device_id = str(run.get("deviceId") or "")
    if device_id in names:
        run["deviceName"] = names[device_id]
    if output_limit < MAX_OUTPUT_TAIL:
        for key in ("stdoutTail", "stderrTail", "outputTail"):
            value = str(run.get(key) or "")
            if len(value) > output_limit:
                run[key] = value[-output_limit:]
    if not include_e2ee:
        run.pop("e2ee", None)
    elif output_limit < MAX_OUTPUT_TAIL:
        e2ee = run.get("e2ee")
        if isinstance(e2ee, dict) and len(str(e2ee.get("ciphertext") or "")) > MAX_LIST_E2EE_CIPHERTEXT:
            run.pop("e2ee", None)
            run["e2eeOmitted"] = True
    if not include_output_chunks:
        chunks = run.pop("outputChunks", None)
        if isinstance(chunks, list):
            run["outputChunkCount"] = len(chunks)
    elif output_chunk_limit is not None:
        chunks = run.get("outputChunks")
        if isinstance(chunks, list):
            run["outputChunkCount"] = len(chunks)
            if output_chunk_limit <= 0:
                run.pop("outputChunks", None)
            elif len(chunks) > output_chunk_limit:
                run["outputChunks"] = chunks[-output_chunk_limit:]
                run["outputChunkOffset"] = len(chunks) - output_chunk_limit
    return run


def is_single_run_id(run_id: str) -> bool:
    return bool(run_id) and "/" not in run_id


def can_read_run(auth: AuthContext, run: dict[str, Any]) -> bool:
    if auth.scope == "admin":
        return True
    if auth.scope == "write":
        device_id = str(run.get("deviceId") or "")
        return bool(device_id) and device_id == auth.device_id
    return False


def normalize_run(run: dict[str, Any]) -> dict[str, Any]:
    output_tail = str(run.get("outputTail") or "")[-MAX_OUTPUT_TAIL:]
    stdout_tail = str(run.get("stdoutTail") or "")[-MAX_OUTPUT_TAIL:]
    stderr_tail = str(run.get("stderrTail") or "")[-MAX_OUTPUT_TAIL:]
    command = run.get("command")
    normalized = {
        "id": str(run.get("id") or ""),
        "command": [str(item) for item in command] if isinstance(command, list) else [],
        "commandText": str(run.get("commandText") or ""),
        "cwd": str(run.get("cwd") or ""),
        "status": str(run.get("status") or "unknown"),
        "pid": run.get("pid"),
        "exitCode": run.get("exitCode"),
        "startedAt": str(run.get("startedAt") or ""),
        "endedAt": run.get("endedAt"),
        "updatedAt": str(run.get("updatedAt") or iso_now()),
        "deviceId": str(run.get("deviceId") or ""),
        "deviceName": str(run.get("deviceName") or ""),
        "project": normalize_project_name(run.get("project")),
        "stdoutTail": stdout_tail,
        "stderrTail": stderr_tail,
        "outputTail": output_tail,
    }
    e2ee = run.get("e2ee")
    if isinstance(e2ee, dict):
        normalized["e2ee"] = {
            "v": int_or_none(e2ee.get("v")) or 0,
            "alg": str(e2ee.get("alg") or "")[:40],
            "nonce": str(e2ee.get("nonce") or "")[:128],
            "ciphertext": str(e2ee.get("ciphertext") or ""),
        }
    interrupt_requested_at = str(run.get("interruptRequestedAt") or "").strip()
    if interrupt_requested_at:
        normalized["interruptRequestedAt"] = interrupt_requested_at
    output_chunks = run.get("outputChunks")
    if isinstance(output_chunks, list) and output_chunks:
        normalized["outputChunks"] = [
            {
                "v": int_or_none(item.get("v")) or 1,
                "alg": str(item.get("alg") or "AES-256-GCM")[:40],
                "nonce": str(item.get("nonce") or "")[:128],
                "ciphertext": str(item.get("ciphertext") or ""),
                "seq": int_or_none(item.get("seq")) or index,
            }
            for index, item in enumerate(output_chunks)
            if isinstance(item, dict) and str(item.get("ciphertext") or "")
        ]
    output_length = int_or_none(run.get("outputLength"))
    if output_length is not None:
        normalized["outputLength"] = max(0, output_length)
    return normalized


def normalize_pair_code(value: object) -> str:
    code = "".join(ch for ch in str(value or "") if ch.isdigit())
    return code if len(code) == 6 else ""


def parse_limit(raw: str) -> int:
    try:
        return max(1, min(int(raw), 500))
    except ValueError:
        return 100


def normalize_status_filter(value: str) -> str:
    value = (value or "").strip().lower()
    return value if value in {"running", "failed", "succeeded"} else ""


def normalize_project_name(value: object) -> str:
    return str(value or "").strip()[:80]


def normalize_project_filter(value: str) -> str:
    value = (value or "").strip()
    if value == "__none__":
        return value
    return normalize_project_name(value)


def normalize_machine_id(value: object) -> str:
    raw = str(value or "").strip()
    if raw.startswith("machine_") and 16 <= len(raw) <= 96 and all(ch.isalnum() or ch == "_" for ch in raw):
        return raw
    return ""


def is_e2ee_run(run: dict[str, Any]) -> bool:
    e2ee = run.get("e2ee")
    if not isinstance(e2ee, dict):
        return False
    return (
        int_or_none(e2ee.get("v")) == 1
        and str(e2ee.get("alg") or "") == "AES-256-GCM"
        and bool(str(e2ee.get("nonce") or ""))
        and bool(str(e2ee.get("ciphertext") or ""))
    )


def is_valid_device_id(value: str) -> bool:
    return value.startswith("dev_") and 8 <= len(value) <= 80 and all(ch.isalnum() or ch == "_" for ch in value)


def first_query_value(query: dict[str, list[str]], name: str) -> str:
    values = query.get(name, [])
    return values[0].strip() if values else ""


def int_or_none(value: object) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def float_or_none(value: object) -> float | None:
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def is_app_version_too_old(platform: str, app_version_code: int | None, min_android_version_code: int) -> bool:
    return platform.lower() == "android" and app_version_code is not None and app_version_code < min_android_version_code


def allow_rate(
    attempts_by_key: dict[str, list[float]],
    lock: threading.Lock,
    key: str,
    limit: int,
    window_seconds: int,
) -> bool:
    now = time.time()
    cutoff = now - window_seconds
    with lock:
        attempts = [value for value in attempts_by_key.get(key, []) if value >= cutoff]
        if len(attempts) >= limit:
            attempts_by_key[key] = attempts
            return False
        attempts.append(now)
        attempts_by_key[key] = attempts
        return True


def env_flag(name: str, default: bool = False) -> bool:
    value = os.environ.get(name, "")
    if not value:
        return default
    return value.strip().lower() not in {"0", "false", "no", "off"}


def server_requires_e2ee() -> bool:
    return env_flag("HAOLEME_REQUIRE_E2EE", env_flag("REMINDER_REQUIRE_E2EE", False))


def server_stats_token() -> str:
    return (os.environ.get("HAOLEME_STATS_TOKEN") or os.environ.get("REMINDER_STATS_TOKEN") or "").strip()


def render_stats_html(stats: dict[str, Any]) -> str:
    def card(label: str, value: Any, sub: str = "") -> str:
        sub_html = f'<div class="s">{sub}</div>' if sub else ""
        return f'<div class="card"><div class="v">{value}</div><div class="l">{label}</div>{sub_html}</div>'

    cards = "".join([
        card("在线 Online", stats["appOnline"], "近 5 分钟"),
        card("日活 DAU", stats["appDau"], "近 24 小时"),
        card("月活 MAU", stats["appMau"], "近 30 天"),
        card("总账号 Accounts", stats["appTotalAccounts"], f'{stats["appInstalls"]} 个安装'),
        card("在线服务器 Servers", f'{stats["serversOnline"]}/{stats["serversTotal"]}', "近 5 分钟"),
    ])
    return (
        "<!doctype html><html lang=\"zh\"><head>"
        "<meta charset=\"utf-8\">"
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        "<meta http-equiv=\"refresh\" content=\"30\">"
        "<title>好了么 · 活跃数据</title><style>"
        ":root{color-scheme:light dark}"
        "body{font-family:-apple-system,system-ui,'PingFang SC',sans-serif;margin:0;padding:18px;background:#0b0f14;color:#e7edf3}"
        "h1{font-size:18px;font-weight:600;margin:4px 0 16px}"
        ".grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}"
        ".card{background:#161c24;border:1px solid #232c38;border-radius:14px;padding:16px}"
        ".v{font-size:34px;font-weight:700;line-height:1.1}"
        ".l{font-size:13px;color:#9fb0c3;margin-top:6px}"
        ".s{font-size:11px;color:#67788c;margin-top:2px}"
        ".ts{font-size:11px;color:#67788c;margin-top:16px;text-align:center}"
        "</style></head><body>"
        "<h1>好了么 · 活跃数据</h1>"
        f"<div class=\"grid\">{cards}</div>"
        f"<div class=\"ts\">更新于 {stats['generatedAt']} · 每 30 秒自动刷新</div>"
        "</body></html>"
    )


def server_allows_legacy_admin_tokens() -> bool:
    return env_flag("HAOLEME_ALLOW_LEGACY_ADMIN_TOKENS", env_flag("REMINDER_ALLOW_LEGACY_ADMIN_TOKENS", False))


def server_allows_existing_legacy_accounts() -> bool:
    return env_flag("HAOLEME_ALLOW_EXISTING_LEGACY_ACCOUNTS", True)


def legacy_admin_token_allowed(db_path: Path, account_key: str) -> bool:
    if server_allows_legacy_admin_tokens():
        return True
    return server_allows_existing_legacy_accounts() and account_has_cloud_data(db_path, account_key)


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def iso_from_epoch(value: float) -> str:
    return datetime.fromtimestamp(float(value), timezone.utc).isoformat().replace("+00:00", "Z")


def is_recent_timestamp(value: str, window_seconds: int) -> bool:
    return is_recent_timestamp_at(value, window_seconds, iso_now())


def is_recent_timestamp_at(value: str, window_seconds: int, now: str) -> bool:
    try:
        timestamp = datetime.fromisoformat(str(value).replace("Z", "+00:00")).timestamp()
        now_timestamp = datetime.fromisoformat(str(now).replace("Z", "+00:00")).timestamp()
    except (TypeError, ValueError):
        return False
    return (now_timestamp - timestamp) <= window_seconds


def serve(host: str, port: int, db_path: Path, min_android_version_code: int) -> None:
    server = HaolemeCloudServer((host, port), db_path, min_android_version_code)
    cloud_log({"ts": iso_now(), "event": "startup", "listen": f"http://{host}:{port}", "db": str(db_path), "version": __version__})
    server.serve_forever()


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] == "serve":
        args = args[1:]
    if args and args[0] == "backup":
        return backup_command(args[1:])
    if args and args[0] == "health":
        return health_command(args[1:])
    if args and args[0] == "audit-permissions":
        return audit_permissions_command(args[1:])
    if args and args[0] == "monitor":
        return monitor_command(args[1:])
    if args and args[0] == "stats":
        return stats_command(args[1:])

    parser = argparse.ArgumentParser(prog="haoleme-cloud")
    parser.add_argument("--host", default=os.environ.get("HAOLEME_CLOUD_HOST") or os.environ.get("REMINDER_CLOUD_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("HAOLEME_CLOUD_PORT") or os.environ.get("REMINDER_CLOUD_PORT", "8000")))
    parser.add_argument("--db", default=os.environ.get("HAOLEME_CLOUD_DB") or os.environ.get("REMINDER_CLOUD_DB", "/data/haoleme-cloud.db"))
    parser.add_argument(
        "--min-android-version-code",
        type=int,
        default=int(os.environ.get("MIN_ANDROID_VERSION_CODE", str(DEFAULT_MIN_ANDROID_VERSION_CODE))),
    )
    ns = parser.parse_args(args)
    serve(ns.host, ns.port, Path(ns.db), ns.min_android_version_code)
    return 0


def backup_command(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="haoleme-cloud backup")
    parser.add_argument("--db", default=os.environ.get("HAOLEME_CLOUD_DB") or os.environ.get("REMINDER_CLOUD_DB", "/data/haoleme-cloud.db"))
    parser.add_argument("--dir", default=os.environ.get("HAOLEME_CLOUD_BACKUP_DIR") or os.environ.get("REMINDER_CLOUD_BACKUP_DIR", "/data/backups"))
    parser.add_argument("--keep", type=int, default=int(os.environ.get("HAOLEME_CLOUD_BACKUP_KEEP") or os.environ.get("REMINDER_CLOUD_BACKUP_KEEP", str(DEFAULT_BACKUP_KEEP))))
    ns = parser.parse_args(argv)
    path = backup_database(Path(ns.db), Path(ns.dir), ns.keep)
    print(path)
    return 0


def health_command(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="haoleme-cloud health")
    parser.add_argument("--db", default=os.environ.get("HAOLEME_CLOUD_DB") or os.environ.get("REMINDER_CLOUD_DB", "/data/haoleme-cloud.db"))
    parser.add_argument(
        "--min-android-version-code",
        type=int,
        default=int(os.environ.get("MIN_ANDROID_VERSION_CODE", str(DEFAULT_MIN_ANDROID_VERSION_CODE))),
    )
    ns = parser.parse_args(argv)
    payload = health_payload(Path(ns.db), ns.min_android_version_code)
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if payload.get("ok") else 1


def audit_permissions_command(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="haoleme-cloud audit-permissions")
    parser.add_argument("--db", default=os.environ.get("HAOLEME_CLOUD_DB") or os.environ.get("REMINDER_CLOUD_DB", "/data/haoleme-cloud.db"))
    ns = parser.parse_args(argv)
    payload = permission_audit(Path(ns.db))
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if payload.get("ok") else 1


def active_user_stats(db_path: Path) -> dict[str, Any]:
    """Active-user metrics for the operator: online / DAU / MAU.

    A "user" is a distinct account_key. App activity is tracked via
    app_tokens.last_used_at (touched on every app request, including the
    background poll), so recency of that timestamp gives liveness.
    """
    online_window = 300        # 5 minutes
    day = 86400
    month = 30 * day
    with connect(db_path) as db:
        app_rows = db.execute(
            "SELECT account_key, last_used_at FROM app_tokens WHERE revoked_at = ''"
        ).fetchall()
        device_rows = db.execute(
            "SELECT account_key, last_seen_at FROM devices WHERE revoked_at = ''"
        ).fetchall()

    def accounts_within(rows: list[sqlite3.Row], ts_key: str, seconds: int) -> set[str]:
        return {
            row["account_key"]
            for row in rows
            if is_recent_timestamp(row[ts_key] or "", seconds)
        }

    return {
        "generatedAt": iso_now(),
        "appOnline": len(accounts_within(app_rows, "last_used_at", online_window)),
        "appDau": len(accounts_within(app_rows, "last_used_at", day)),
        "appMau": len(accounts_within(app_rows, "last_used_at", month)),
        "appTotalAccounts": len({row["account_key"] for row in app_rows}),
        "appInstalls": len(app_rows),
        "serversOnline": len(accounts_within(device_rows, "last_seen_at", online_window)),
        "serversTotal": len({row["account_key"] for row in device_rows}),
        "onlineWindowSeconds": online_window,
    }


def stats_command(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="haoleme-cloud stats")
    parser.add_argument("--db", default=os.environ.get("HAOLEME_CLOUD_DB") or os.environ.get("REMINDER_CLOUD_DB", "/data/haoleme-cloud.db"))
    parser.add_argument("--json", action="store_true", help="print raw JSON instead of a summary")
    ns = parser.parse_args(argv)
    stats = active_user_stats(Path(ns.db))
    if ns.json:
        print(json.dumps(stats, ensure_ascii=False, indent=2))
        return 0
    print("好了么 cloud — active users")
    print(f"  Online (app, <=5min): {stats['appOnline']}")
    print(f"  DAU    (app, <=24h):  {stats['appDau']}")
    print(f"  MAU    (app, <=30d):  {stats['appMau']}")
    print(f"  Total accounts:       {stats['appTotalAccounts']}  ({stats['appInstalls']} app install(s))")
    print(f"  Servers online:       {stats['serversOnline']} / {stats['serversTotal']}")
    print(f"  As of:                {stats['generatedAt']}")
    return 0


def monitor_command(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="haoleme-cloud monitor")
    parser.add_argument("--db", default=os.environ.get("HAOLEME_CLOUD_DB") or os.environ.get("REMINDER_CLOUD_DB", "/data/haoleme-cloud.db"))
    parser.add_argument("--backup-dir", default=os.environ.get("HAOLEME_CLOUD_BACKUP_DIR") or os.environ.get("REMINDER_CLOUD_BACKUP_DIR", "/data/backups"))
    parser.add_argument(
        "--min-android-version-code",
        type=int,
        default=int(os.environ.get("MIN_ANDROID_VERSION_CODE", str(DEFAULT_MIN_ANDROID_VERSION_CODE))),
    )
    parser.add_argument(
        "--min-free-mb",
        type=int,
        default=int(os.environ.get("HAOLEME_MONITOR_MIN_FREE_MB", str(DEFAULT_MONITOR_MIN_FREE_BYTES // (1024 * 1024)))),
    )
    parser.add_argument(
        "--max-backup-age-hours",
        type=int,
        default=int(os.environ.get("HAOLEME_MONITOR_MAX_BACKUP_AGE_HOURS", str(DEFAULT_MONITOR_MAX_BACKUP_AGE_HOURS))),
    )
    parser.add_argument("--alert-webhook", default=os.environ.get("HAOLEME_ALERT_WEBHOOK_URL", ""))
    ns = parser.parse_args(argv)
    payload = monitor_payload(
        Path(ns.db),
        Path(ns.backup_dir),
        ns.min_android_version_code,
        min_free_bytes=max(1, ns.min_free_mb) * 1024 * 1024,
        max_backup_age_hours=max(1, ns.max_backup_age_hours),
    )
    if not payload.get("ok") and ns.alert_webhook.strip():
        payload["alert"] = send_monitor_alert(payload, ns.alert_webhook)
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if payload.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
