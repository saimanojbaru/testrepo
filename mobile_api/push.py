"""Firebase Cloud Messaging push — kill-switch / daily-loss / fills.

Requires firebase-admin + a service-account JSON. Gated behind env so CI + tests
don't need FCM credentials.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


FCM_CREDS_ENV = "FCM_SERVICE_ACCOUNT_JSON"
FCM_DEVICE_TOKEN_ENV = "FCM_DEVICE_TOKEN"


@dataclass
class PushConfig:
    service_account_path: Path | None = None
    device_token: str = ""


def _load_config() -> PushConfig:
    sa = os.environ.get(FCM_CREDS_ENV)
    return PushConfig(
        service_account_path=Path(sa) if sa else None,
        device_token=os.environ.get(FCM_DEVICE_TOKEN_ENV, ""),
    )


def send_push(title: str, body: str, data: dict[str, Any] | None = None) -> bool:
    """Best-effort push. Returns False (silently) if FCM isn't configured."""
    cfg = _load_config()
    if not cfg.service_account_path or not cfg.device_token:
        return False
    try:
        import firebase_admin  # type: ignore[import-not-found]
        from firebase_admin import credentials, messaging  # type: ignore[import-not-found]
    except ImportError:
        return False
    if not firebase_admin._apps:
        cred = credentials.Certificate(str(cfg.service_account_path))
        firebase_admin.initialize_app(cred)
    msg = messaging.Message(
        notification=messaging.Notification(title=title, body=body),
        data={k: str(v) for k, v in (data or {}).items()},
        token=cfg.device_token,
    )
    messaging.send(msg)
    return True
