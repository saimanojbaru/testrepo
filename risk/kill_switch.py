"""File-backed kill switch — cheap, inspectable, works from Telegram/CLI/mobile API.

The presence of the flag file means 'halt all trading; square off open positions'.
"""
from __future__ import annotations

import datetime as dt
import os
from dataclasses import dataclass
from pathlib import Path

DEFAULT_PATH = Path("./.kill_switch")


@dataclass
class KillSwitch:
    path: Path = DEFAULT_PATH

    def engaged(self) -> bool:
        return self.path.exists()

    def engage(self, reason: str = "manual") -> None:
        payload = f"{dt.datetime.utcnow().isoformat()}Z\t{reason}\n"
        self.path.write_text(payload)
        # Make world-readable so ops tools (mobile API, Telegram bot) can inspect it.
        try:
            os.chmod(self.path, 0o644)
        except OSError:
            pass

    def disengage(self) -> None:
        if self.path.exists():
            self.path.unlink()

    def reason(self) -> str | None:
        if not self.path.exists():
            return None
        parts = self.path.read_text().strip().split("\t", 1)
        return parts[1] if len(parts) > 1 else "unknown"
