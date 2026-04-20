from __future__ import annotations

import hashlib

import pytest
from fastapi.testclient import TestClient

from broker.paper import PaperBroker
from mobile_api.auth import hash_secret, issue_token, verify_shared_secret, verify_token
from mobile_api.routes_rest import ApiDeps, build_router
from mobile_api.state_bus import StateBus
from risk.engine import RiskEngine
from risk.kill_switch import KillSwitch
from risk.limits import RiskLimits

from fastapi import FastAPI

_SECRET = "test-api-secret-xyz"
_SHARED = "phone-password-abc"


def _make_app():
    broker = PaperBroker()
    broker.update_price("TEST", 100.0)
    ks = KillSwitch(path=__import__("pathlib").Path("/tmp/.ks_mobile_test"))
    ks.disengage()
    risk = RiskEngine(
        limits=RiskLimits(max_daily_loss=2000),
        capital=100_000.0,
        kill_switch=ks,
    )
    bus = StateBus()
    deps = ApiDeps(
        broker=broker,
        risk=risk,
        bus=bus,
        api_secret=_SECRET,
        api_secret_hash=hash_secret(_SHARED),
    )
    app = FastAPI()
    app.include_router(build_router(deps))
    return app, deps


def _login(client: TestClient) -> str:
    resp = client.post("/login", json={"secret": _SHARED})
    assert resp.status_code == 200
    return resp.json()["token"]


def test_auth_issue_verify():
    token, exp = issue_token(_SECRET)
    assert verify_token(token, _SECRET)


def test_bad_token_rejected():
    assert not verify_token("not.a.jwt", _SECRET)


def test_login_valid_secret():
    app, _ = _make_app()
    client = TestClient(app)
    resp = client.post("/login", json={"secret": _SHARED})
    assert resp.status_code == 200
    assert "token" in resp.json()


def test_login_wrong_secret():
    app, _ = _make_app()
    client = TestClient(app)
    resp = client.post("/login", json={"secret": "WRONG"})
    assert resp.status_code == 401


def test_state_requires_auth():
    app, _ = _make_app()
    client = TestClient(app)
    resp = client.get("/state")
    assert resp.status_code == 401


def test_state_with_auth():
    app, _ = _make_app()
    client = TestClient(app)
    token = _login(client)
    resp = client.get("/state", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    data = resp.json()
    assert "daily_pnl" in data
    assert "kill_switch_engaged" in data


def test_kill_switch_engage_disengage():
    app, deps = _make_app()
    client = TestClient(app)
    token = _login(client)
    headers = {"Authorization": f"Bearer {token}"}

    resp = client.post("/kill-switch", json={"engage": True}, headers=headers)
    assert resp.status_code == 200
    assert resp.json()["engaged"] is True
    assert deps.risk.kill_switch.engaged()

    resp = client.post("/kill-switch", json={"engage": False}, headers=headers)
    assert resp.status_code == 200
    assert not deps.risk.kill_switch.engaged()


def test_risk_config_patch():
    app, deps = _make_app()
    client = TestClient(app)
    token = _login(client)
    headers = {"Authorization": f"Bearer {token}"}

    resp = client.post(
        "/risk-config",
        json={"max_daily_loss": 5000.0},
        headers=headers,
    )
    assert resp.status_code == 200
    assert deps.risk.limits.max_daily_loss == 5000.0
