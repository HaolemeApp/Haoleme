from __future__ import annotations

import shlex
from pathlib import Path
from typing import Iterable


def shlex_join(split_command: Iterable[str]) -> str:
    """Backport of shlex.join (added in Python 3.8)."""
    return " ".join(shlex.quote(arg) for arg in split_command)


def unlink_missing(path: Path) -> None:
    """Backport of Path.unlink(missing_ok=True); the kwarg was added in Python 3.8."""
    try:
        path.unlink()
    except FileNotFoundError:
        pass


def remove_prefix(value: str, prefix: str) -> str:
    """Backport of str.removeprefix (added in Python 3.9)."""
    if prefix and value.startswith(prefix):
        return value[len(prefix):]
    return value


def remove_suffix(value: str, suffix: str) -> str:
    """Backport of str.removesuffix (added in Python 3.9)."""
    if suffix and value.endswith(suffix):
        return value[: -len(suffix)]
    return value
