"""Token issuance/verification. LAN + Tailscale scope — single-user, long-lived.

Uses itsdangerous TimestampSigner (HMAC-SHA1 signed, base64 encoded) instead of
PyJWT to avoid the broken system `cryptography` dependency on this environment.
The Flutter client treats the token as an opaque string — no JWT parsing needed.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import hmac

from itsdangerous import BadSignature, SignatureExpired, TimestampSigner

DEFAULT_TTL = dt.timedelta(days=30)
_PAYLOAD = "scalp-mobile-v1"   # fixed payload; identity is the secret itself


def hash_secret(secret: str) -> str:
    return hashlib.sha256(secret.encode()).hexdigest()


def verify_shared_secret(presented: str, expected_hash: str) -> bool:
    return hmac.compare_digest(hash_secret(presented), expected_hash)


def issue_token(api_secret: str, ttl: dt.timedelta = DEFAULT_TTL) -> tuple[str, dt.datetime]:
    signer = TimestampSigner(api_secret)
    token = signer.sign(_PAYLOAD).decode()
    exp = dt.datetime.utcnow() + ttl
    return token, exp


def verify_token(token: str, api_secret: str) -> bool:
    signer = TimestampSigner(api_secret)
    try:
        signer.unsign(token, max_age=DEFAULT_TTL.total_seconds())
        return True
    except (BadSignature, SignatureExpired):
        return False
