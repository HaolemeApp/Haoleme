from __future__ import annotations

import json
import os
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from ._compat import shlex_join


TERMINAL_STATUSES = {"succeeded", "failed", "cancelled"}
DEFAULT_OUTPUT_TAIL_CHARS = 300_000
MAX_OUTPUT_TAIL_CHARS = 1_000_000


class ClosingConnection(sqlite3.Connection):
    def __exit__(self, exc_type, exc_value, traceback) -> bool:
        try:
            return super().__exit__(exc_type, exc_value, traceback)
        finally:
            self.close()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def default_data_dir() -> Path:
    configured = os.environ.get("HAOLEME_HOME") or os.environ.get("REMINDER_HOME")
    if configured:
        return Path(configured).expanduser()
    home = Path.home()
    current = home / ".haoleme"
    legacy = home / ".haoleme"
    if legacy.exists() and not current.exists():
        return legacy
    return current


def default_db_path() -> Path:
    data_dir = default_data_dir()
    legacy_db = data_dir / "haoleme.db"
    if legacy_db.exists():
        return legacy_db
    return data_dir / "haoleme.db"


@dataclass(frozen=True)
class RunRecord:
    id: str
    command: list[str]
    cwd: str
    project: str
    status: str
    pid: int | None
    exit_code: int | None
    started_at: str
    ended_at: str | None
    updated_at: str
    stdout_tail: str
    stderr_tail: str
    output_tail: str
    cloud_synced_at: str

    @property
    def commandText(self) -> str:
        return shlex_join(self.command)

    @classmethod
    def from_row(cls, row: sqlite3.Row) -> "RunRecord":
        return cls(
            id=row["id"],
            command=json.loads(row["command"]),
            cwd=row["cwd"],
            project=row["project"] or "",
            status=row["status"],
            pid=row["pid"],
            exit_code=row["exit_code"],
            started_at=row["started_at"],
            ended_at=row["ended_at"],
            updated_at=row["updated_at"],
            stdout_tail=row["stdout_tail"] or "",
            stderr_tail=row["stderr_tail"] or "",
            output_tail=row["output_tail"] or "",
            cloud_synced_at=row["cloud_synced_at"] or "",
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "command": self.command,
            "commandText": self.commandText,
            "cwd": self.cwd,
            "project": self.project,
            "status": self.status,
            "pid": self.pid,
            "exitCode": self.exit_code,
            "startedAt": self.started_at,
            "endedAt": self.ended_at,
            "updatedAt": self.updated_at,
            "stdoutTail": self.stdout_tail,
            "stderrTail": self.stderr_tail,
            "outputTail": self.output_tail,
        }


class RunStore:
    def __init__(self, db_path: Path | str | None = None, output_tail_chars: int | None = None) -> None:
        self.db_path = Path(db_path) if db_path else default_db_path()
        self.output_tail_chars = normalize_output_tail_chars(output_tail_chars)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.init_db()

    def connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, factory=ClosingConnection)
        conn.row_factory = sqlite3.Row
        return conn

    def init_db(self) -> None:
        with self.connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS runs (
                    id TEXT PRIMARY KEY,
                    command TEXT NOT NULL,
                    cwd TEXT NOT NULL,
                    status TEXT NOT NULL,
                    pid INTEGER,
                    exit_code INTEGER,
                    started_at TEXT NOT NULL,
                    ended_at TEXT,
                    updated_at TEXT NOT NULL,
                    stdout_tail TEXT NOT NULL DEFAULT '',
                    stderr_tail TEXT NOT NULL DEFAULT '',
                    output_tail TEXT NOT NULL DEFAULT '',
                    project TEXT NOT NULL DEFAULT '',
                    cloud_synced_at TEXT NOT NULL DEFAULT ''
                )
                """
            )
            columns = {
                row["name"]
                for row in conn.execute("PRAGMA table_info(runs)").fetchall()
            }
            if "output_tail" not in columns:
                conn.execute("ALTER TABLE runs ADD COLUMN output_tail TEXT NOT NULL DEFAULT ''")
            if "project" not in columns:
                conn.execute("ALTER TABLE runs ADD COLUMN project TEXT NOT NULL DEFAULT ''")
            if "cloud_synced_at" not in columns:
                conn.execute("ALTER TABLE runs ADD COLUMN cloud_synced_at TEXT NOT NULL DEFAULT ''")
                conn.execute(
                    """
                    UPDATE runs
                    SET cloud_synced_at = updated_at
                    WHERE cloud_synced_at = ''
                    """
                )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_runs_updated_at ON runs(updated_at DESC)"
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_runs_project_updated ON runs(project, updated_at DESC)"
            )

    def create_run(self, run_id: str, command: list[str], cwd: str, project: str = "") -> None:
        now = utc_now()
        project_name = normalize_project_name(project)
        with self.connect() as conn:
            conn.execute(
                """
                INSERT INTO runs (
                    id, command, cwd, project, status, pid, exit_code, started_at,
                    ended_at, updated_at, stdout_tail, stderr_tail, output_tail, cloud_synced_at
                ) VALUES (?, ?, ?, ?, 'created', NULL, NULL, ?, NULL, ?, '', '', '', '')
                """,
                (run_id, json.dumps(command), cwd, project_name, now, now),
            )

    def mark_running(self, run_id: str, pid: int) -> None:
        self._update(run_id, {"status": "running", "pid": pid})

    def append_output(self, run_id: str, stream: str, text: str, max_chars: int | None = None) -> None:
        if stream not in {"stdout_tail", "stderr_tail"}:
            raise ValueError(f"unsupported stream: {stream}")
        limit = normalize_output_tail_chars(max_chars if max_chars is not None else self.output_tail_chars)
        with self.connect() as conn:
            conn.execute(
                f"""
                UPDATE runs
                SET {stream} = substr({stream} || ?, -?),
                    output_tail = substr(output_tail || ?, -?),
                    cloud_synced_at = '',
                    updated_at = ?
                WHERE id = ?
                """,
                (text, limit, text, limit, utc_now(), run_id),
            )

    def finish_run(self, run_id: str, exit_code: int) -> None:
        status = "succeeded" if exit_code == 0 else "failed"
        now = utc_now()
        with self.connect() as conn:
            conn.execute(
                """
                UPDATE runs
                SET status = ?, exit_code = ?, ended_at = ?, updated_at = ?, cloud_synced_at = ''
                WHERE id = ?
                """,
                (status, exit_code, now, now, run_id),
            )

    def cancel_run(self, run_id: str, note: str = "") -> None:
        now = utc_now()
        limit = self.output_tail_chars
        with self.connect() as conn:
            if note:
                conn.execute(
                    """
                    UPDATE runs
                    SET status = 'cancelled',
                        exit_code = -1,
                        ended_at = ?,
                        updated_at = ?,
                        stderr_tail = substr(stderr_tail || ?, -?),
                        output_tail = substr(output_tail || ?, -?),
                        cloud_synced_at = ''
                    WHERE id = ?
                    """,
                    (now, now, note, limit, note, limit, run_id),
                )
            else:
                conn.execute(
                    """
                    UPDATE runs
                    SET status = 'cancelled', exit_code = -1, ended_at = ?, updated_at = ?, cloud_synced_at = ''
                    WHERE id = ?
                    """,
                    (now, now, run_id),
                )

    def get_run(self, run_id: str) -> RunRecord | None:
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM runs WHERE id = ?", (run_id,)).fetchone()
        return RunRecord.from_row(row) if row else None

    def delete_run(self, run_id: str) -> bool:
        with self.connect() as conn:
            cursor = conn.execute("DELETE FROM runs WHERE id = ?", (run_id,))
            return cursor.rowcount > 0

    def list_runs(self, limit: int = 100) -> list[RunRecord]:
        with self.connect() as conn:
            rows = conn.execute(
                "SELECT * FROM runs ORDER BY started_at DESC LIMIT ?",
                (limit,),
            ).fetchall()
        return [RunRecord.from_row(row) for row in rows]

    def list_active_runs(self, limit: int = 100) -> list[RunRecord]:
        with self.connect() as conn:
            rows = conn.execute(
                """
                SELECT * FROM runs
                WHERE status IN ('created', 'running')
                ORDER BY updated_at ASC
                LIMIT ?
                """,
                (limit,),
            ).fetchall()
        return [RunRecord.from_row(row) for row in rows]

    def list_updated_since(self, since: str | None, limit: int = 100) -> list[RunRecord]:
        with self.connect() as conn:
            if since:
                rows = conn.execute(
                    """
                    SELECT * FROM runs
                    WHERE updated_at > ?
                    ORDER BY updated_at ASC
                    LIMIT ?
                    """,
                    (since, limit),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM runs ORDER BY updated_at ASC LIMIT ?",
                    (limit,),
                ).fetchall()
        return [RunRecord.from_row(row) for row in rows]

    def mark_cloud_synced(self, run_id: str) -> None:
        with self.connect() as conn:
            conn.execute(
                "UPDATE runs SET cloud_synced_at = ? WHERE id = ?",
                (utc_now(), run_id),
            )

    def mark_cloud_pending(self, run_id: str) -> None:
        with self.connect() as conn:
            conn.execute("UPDATE runs SET cloud_synced_at = '' WHERE id = ?", (run_id,))

    def list_unsynced_runs(self, limit: int = 100) -> list[RunRecord]:
        with self.connect() as conn:
            rows = conn.execute(
                """
                SELECT * FROM runs
                WHERE cloud_synced_at = ''
                ORDER BY updated_at ASC
                LIMIT ?
                """,
                (limit,),
            ).fetchall()
        return [RunRecord.from_row(row) for row in rows]

    def count_unsynced_runs(self) -> int:
        with self.connect() as conn:
            row = conn.execute("SELECT COUNT(*) AS count FROM runs WHERE cloud_synced_at = ''").fetchone()
        return int(row["count"]) if row is not None else 0

    def _update(self, run_id: str, fields: dict[str, Any]) -> None:
        if not fields:
            return
        fields["updated_at"] = utc_now()
        fields["cloud_synced_at"] = ""
        assignments = ", ".join(f"{name} = ?" for name in fields)
        values = list(fields.values()) + [run_id]
        with self.connect() as conn:
            conn.execute(f"UPDATE runs SET {assignments} WHERE id = ?", values)


def normalize_project_name(value: str | None) -> str:
    return (value or "").strip()[:80]


def normalize_output_tail_chars(value: int | str | None = None) -> int:
    raw = value
    if raw is None:
        raw = os.environ.get("HAOLEME_CONSOLE_CHARS") or os.environ.get("HAOLEME_CONSOLE_CHARS") or DEFAULT_OUTPUT_TAIL_CHARS
    try:
        parsed = int(raw)
    except (TypeError, ValueError):
        parsed = DEFAULT_OUTPUT_TAIL_CHARS
    return max(30_000, min(parsed, MAX_OUTPUT_TAIL_CHARS))
