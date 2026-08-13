from __future__ import annotations

import re
from dataclasses import dataclass

TQDM_RE = re.compile(r"(\d+(?:\.\d+)?)%\|[^|]*\|\s*(\d+)/(\d+)(?:\s*\[([\d:]+)<([\d:]+)[^\]]*\])?")
EPOCH_RE = re.compile(r"[Ee]poch[\s:=\[]*(\d+)\s*/\s*(\d+)")
LOSS_RE = re.compile(r"\bloss[\s:=]+([0-9]*\.?[0-9]+(?:[eE][+-]?\d+)?)")
ITER_RE = re.compile(r"\b(\d+)\s*/\s*(\d+)\b")


def parse_eta(text: str) -> int | None:
    parts = text.split(":")
    if not parts or not all(part.isdigit() for part in parts):
        return None
    numbers = [int(part) for part in parts]
    while len(numbers) < 3:
        numbers.insert(0, 0)
    return numbers[0] * 3600 + numbers[1] * 60 + numbers[2]


@dataclass
class Progress:
    percent: float | None = None
    eta_seconds: int | None = None
    loss: float | None = None
    epoch: tuple[int, int] | None = None


class ProgressParser:
    def __init__(self) -> None:
        self.state = Progress()

    def feed(self, text: str) -> bool:
        changed = False
        if not text:
            return False
        for line in re.split(r"[\r\n]", text):
            tqdm_match = TQDM_RE.search(line)
            if tqdm_match:
                self.state.percent = float(tqdm_match.group(1))
                changed = True
                if tqdm_match.group(5) and (eta := parse_eta(tqdm_match.group(5))) is not None:
                    self.state.eta_seconds = eta
            if match := EPOCH_RE.search(line):
                current, total = int(match.group(1)), int(match.group(2))
                if total > 0:
                    self.state.epoch = (current, total)
                    # Each cloud update parses a tail containing several epochs.
                    # Keep the newest epoch instead of freezing on the first one.
                    # A tqdm percentage on the same line is more precise.
                    if not tqdm_match:
                        self.state.percent = min(100.0, current * 100.0 / total)
                    changed = True
            if match := LOSS_RE.search(line):
                self.state.loss = float(match.group(1))
                changed = True
            elif self.state.percent is None and (match := ITER_RE.search(line)):
                current, total = int(match.group(1)), int(match.group(2))
                if 0 < current <= total <= 1_000_000:
                    self.state.percent = min(100.0, current * 100.0 / total)
                    changed = True
        return changed


def progress_fields(text: str) -> dict[str, float | int]:
    parser = ProgressParser()
    parser.feed(text[-8192:] if text else "")
    fields: dict[str, float | int] = {}
    if parser.state.percent is not None:
        fields["progress"] = round(float(parser.state.percent), 2)
    if parser.state.loss is not None:
        fields["lastLoss"] = float(parser.state.loss)
    if parser.state.eta_seconds is not None:
        fields["etaSeconds"] = int(parser.state.eta_seconds)
    return fields
