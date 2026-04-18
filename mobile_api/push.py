"""Firebase Cloud Messaging push sender.

Activation is opt-in: if `firebase_admin` isn't installed or `FIREBASE_CREDENTIALS`
isn't set, the sender silently no-ops so the backend still works for LAN-only use.
"""

from __future__ import annotations

import json
import logging
import os
from typing import List

from mobile_api.schemas import TradeEvent

logger = logging.getLogger(__name__)

_initialized = False
_enabled = False


def _ensure_initialized() -> None:
    global _initialized, _enabled
    if _initialized:
        return
    _initialized = True

    creds_path = os.getenv("FIREBASE_CREDENTIALS")
    if not creds_path or not os.path.exists(creds_path):
        logger.info("FCM disabled: FIREBASE_CREDENTIALS not configured")
        return

    try:
        import firebase_admin
        from firebase_admin import credentials
    except ImportError:
        logger.info("FCM disabled: install `firebase-admin` to enable push")
        return

    try:
        cred = credentials.Certificate(creds_path)
        firebase_admin.initialize_app(cred)
        _enabled = True
        logger.info("FCM initialized")
    except Exception:
        logger.exception("FCM init failed")


def _device_tokens() -> List[str]:
    raw = os.getenv("FCM_DEVICE_TOKENS", "").strip()
    if not raw:
        return []
    # Allow comma-separated tokens or a JSON array
    if raw.startswith("["):
        try:
            return [t for t in json.loads(raw) if isinstance(t, str)]
        except json.JSONDecodeError:
            return []
    return [t.strip() for t in raw.split(",") if t.strip()]


def send_event(event: TradeEvent) -> None:
    _ensure_initialized()
    if not _enabled:
        return
    tokens = _device_tokens()
    if not tokens:
        return

    try:
        from firebase_admin import messaging
    except ImportError:
        return

    title = _title_for(event)
    body = event.message
    try:
        message = messaging.MulticastMessage(
            tokens=tokens,
            notification=messaging.Notification(title=title, body=body),
            data={
                "kind": event.kind,
                "symbol": event.symbol or "",
                "price": str(event.price) if event.price is not None else "",
                "pnl": str(event.pnl) if event.pnl is not None else "",
            },
            android=messaging.AndroidConfig(priority="high"),
        )
        messaging.send_each_for_multicast(message)
    except Exception:
        logger.exception("FCM send failed")


def _title_for(event: TradeEvent) -> str:
    if event.kind == "kill_switch":
        return "Kill switch activated"
    if event.kind == "risk":
        return "Risk alert"
    if event.kind == "fill":
        return f"Fill: {event.symbol or ''} {event.side or ''}"
    if event.kind == "exit":
        return f"Exit: {event.symbol or ''} P&L ₹{event.pnl or 0:.0f}"
    if event.kind == "regime":
        return "Regime shift"
    return "Scalping Agent"
