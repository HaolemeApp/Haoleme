from __future__ import annotations

import json
import os
import secrets
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from ._compat import remove_prefix
from .store import RunStore


class HaolemeServer(ThreadingHTTPServer):
    def __init__(self, server_address: tuple[str, int], store: RunStore, token: str | None = None) -> None:
        super().__init__(server_address, HaolemeRequestHandler)
        self.store = store
        self.token = token


class HaolemeRequestHandler(BaseHTTPRequestHandler):
    server: HaolemeServer

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json({"ok": True})
            return

        if parsed.path.startswith("/api/") and not self.is_authorized():
            self.send_json({"error": "unauthorized"}, status=HTTPStatus.UNAUTHORIZED)
            return

        if parsed.path == "/api/runs":
            query = parse_qs(parsed.query)
            limit = parse_limit(query.get("limit", ["100"])[0])
            runs = [run.to_dict() for run in self.server.store.list_runs(limit=limit)]
            self.send_json({"runs": runs})
            return

        if parsed.path.startswith("/api/runs/"):
            run_id = remove_prefix(parsed.path, "/api/runs/")
            run = self.server.store.get_run(run_id)
            if run is None:
                self.send_json({"error": "run not found"}, status=HTTPStatus.NOT_FOUND)
                return
            self.send_json({"run": run.to_dict()})
            return

        if parsed.path == "/api/events":
            query = parse_qs(parsed.query)
            since = query.get("since", [None])[0]
            limit = parse_limit(query.get("limit", ["100"])[0])
            runs = [
                run.to_dict()
                for run in self.server.store.list_updated_since(since=since, limit=limit)
            ]
            latest = max((run["updatedAt"] for run in runs), default=since)
            self.send_json({"events": runs, "latest": latest})
            return

        self.send_json({"error": "not found"}, status=HTTPStatus.NOT_FOUND)

    def do_DELETE(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/") and not self.is_authorized():
            self.send_json({"error": "unauthorized"}, status=HTTPStatus.UNAUTHORIZED)
            return

        if parsed.path.startswith("/api/runs/"):
            run_id = remove_prefix(parsed.path, "/api/runs/")
            if self.server.store.delete_run(run_id):
                self.send_json({"deleted": True})
                return
            self.send_json({"error": "run not found"}, status=HTTPStatus.NOT_FOUND)
            return

        self.send_json({"error": "not found"}, status=HTTPStatus.NOT_FOUND)

    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_cors_headers()
        self.end_headers()

    def send_json(self, payload: dict, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_cors_headers()
        self.end_headers()
        self.wfile.write(body)

    def send_cors_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Haoleme-Token")

    def is_authorized(self) -> bool:
        token = self.server.token
        if not token:
            return True

        auth = self.headers.get("Authorization", "")
        if auth.startswith("Bearer "):
            return secrets.compare_digest(remove_prefix(auth, "Bearer ").strip(), token)

        header_token = self.headers.get("X-Haoleme-Token", "")
        return bool(header_token) and secrets.compare_digest(header_token, token)

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"[hao server] {self.address_string()} - {fmt % args}")


def parse_limit(raw: str) -> int:
    try:
        return max(1, min(int(raw), 500))
    except ValueError:
        return 100


def serve(host: str, port: int, store: RunStore | None = None, token: str | None = None) -> None:
    actual_store = store or RunStore()
    actual_token = token or os.environ.get("HAOLEME_TOKEN")
    httpd = HaolemeServer((host, port), actual_store, actual_token)
    print(f"好了么 server listening on http://{host}:{port}")
    print(f"Database: {actual_store.db_path}")
    if actual_token:
        print("Auth: enabled")
    elif host in {"0.0.0.0", "::"}:
        print("WARNING: server is listening publicly without a token.")
    httpd.serve_forever()
