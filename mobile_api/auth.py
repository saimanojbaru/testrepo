"""Minimal JWT auth for a single-user LAN/Tailscale deployment."""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import time
from base64 import urlsafe_b64decode, urlsafe_b64encode
from datetime import datetime, timedelta, timezone

from fastapi import Header, HTTPException, status


TOKEN_TTL_DAYS = 30


def _secret() -> bytes:
    value = os.getenv("MOBILE_API_SECRET")
    if not value:
        raise RuntimeError(
            "MOBILE_API_SECRET is not set. Add it to .env before starting the backend."
        )
    return value.encode()


def _shared_secret() -> str:
    value = os.getenv("MOBILE_API_SHARED_SECRET")
    if not value:
        raise RuntimeError(
            "MOBILE_API_SHARED_SECRET is not set. Add it to .env before starting the backend."
        )
    return value


def _b64url(data: bytes) -> str:
    return urlsafe_b64encode(data).rstrip(b"=").decode()


def _b64url_decode(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return urlsafe_b64decode(data + padding)


def verify_login_secret(provided: str) -> bool:
    return hmac.compare_digest(provided, _shared_secret())


def issue_token(device_id: str) -> tuple[str, datetime]:
    expires_at = datetime.now(timezone.utc) + timedelta(days=TOKEN_TTL_DAYS)
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": device_id,
        "exp": int(expires_at.timestamp()),
        "iat": int(time.time()),
        "jti": secrets.token_hex(8),
    }
    header_b64 = _b64url(json.dumps(header, separators=(",", ":")).encode())
    payload_b64 = _b64url(json.dumps(payload, separators=(",", ":")).encode())
    signing_input = f"{header_b64}.{payload_b64}".encode()
    signature = hmac.new(_secret(), signing_input, hashlib.sha256).digest()
    token = f"{header_b64}.{payload_b64}.{_b64url(signature)}"
    return token, expires_at


def decode_token(token: str) -> dict:
    try:
        header_b64, payload_b64, signature_b64 = token.split(".")
    except ValueError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Malformed token")

    signing_input = f"{header_b64}.{payload_b64}".encode()
    expected = hmac.new(_secret(), signing_input, hashlib.sha256).digest()
    if not hmac.compare_digest(expected, _b64url_decode(signature_b64)):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Bad signature")

    payload = json.loads(_b64url_decode(payload_b64))
    if payload.get("exp", 0) < int(time.time()):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
    return payload


def require_bearer(authorization: str | None = Header(default=None)) -> dict:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    return decode_token(authorization.split(" ", 1)[1])


def require_token_query(token: str | None) -> dict:
    """For WebSocket handshakes that can't easily carry Authorization headers."""
    if not token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing token")
    return decode_token(token)
