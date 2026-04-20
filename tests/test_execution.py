from __future__ import annotations

import hashlib
from pathlib import Path
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from broker.paper import PaperBroker
from execution.agent import TradingAgent, on_paper_fill_factory
from execution.order_manager import OrderManager, idempotency_key
from execution.tv_webhook import WebhookHandler, make_app
from risk.engine import RiskEngine
from risk.kill_switch import KillSwitch
from risk.limits import RiskLimits
from strategies.base import Signal, SignalDirection
from strategies.registry import RuleRecord
from strategies.rule_strategy import RuleStrategy


# --- Order manager ---
def test_idempotency_key_stable():
    import datetime as dt
    now = dt.datetime(2026, 4, 20, 10, 0)
    k1 = idempotency_key("NIFTY_OPT", "BUY", 50, "scalp", now)
    k2 = idempotency_key("NIFTY_OPT", "BUY", 50, "scalp", now)
    assert k1 == k2


def test_order_manager_deduplicates(tmp_path):
    broker = PaperBroker()
    broker.update_price("NIFTY_OPT", 100.0)
    from broker.base import Order, OrderSide, OrderType, Product
    om = OrderManager(broker=broker, log_dir=tmp_path / "log")
    order = Order(
        instrument_key="NIFTY_OPT",
        side=OrderSide.BUY,
        quantity=50,
        order_type=OrderType.MARKET,
        product=Product.INTRADAY,
        idempotency_key="test-key-abc",
    )
    r1 = om.submit(order)
    r2 = om.submit(order)
    assert r1 is r2  # same object returned for duplicate key


# --- TradingView webhook ---
def test_tv_webhook_valid_signal():
    secret = "mysecret"
    received: list = []
    handler = WebhookHandler(
        expected_secret_hash=hashlib.sha256(secret.encode()).hexdigest(),
        on_signal=lambda sym, sig: received.append((sym, sig)),
    )
    app = make_app(handler)
    client = TestClient(app)
    resp = client.post(
        "/tv/alert",
        json={"secret": secret, "symbol": "NIFTY", "direction": "LONG"},
    )
    assert resp.status_code == 200
    assert len(received) == 1
    assert received[0][0] == "NIFTY"
    assert received[0][1].direction == SignalDirection.LONG


def test_tv_webhook_bad_secret():
    handler = WebhookHandler(
        expected_secret_hash=hashlib.sha256(b"real").hexdigest(),
        on_signal=lambda s, sig: None,
    )
    client = TestClient(make_app(handler))
    resp = client.post(
        "/tv/alert",
        json={"secret": "wrong", "symbol": "NIFTY", "direction": "LONG"},
    )
    assert resp.status_code == 401


def test_tv_webhook_health():
    handler = WebhookHandler(
        expected_secret_hash=hashlib.sha256(b"x").hexdigest(),
        on_signal=lambda s, sig: None,
    )
    resp = TestClient(make_app(handler)).get("/healthz")
    assert resp.status_code == 200


# --- TradingAgent ---
def _make_agent(tmp_path) -> tuple[TradingAgent, PaperBroker]:
    rule = RuleRecord(
        key="test",
        feature="rsi",
        entry_op="<",
        entry_threshold=40.0,
        exit_bars=5,
        stop_loss_pct=0.01,
        take_profit_pct=0.02,
        sharpe_net=0.0,
        net_pnl=0.0,
        trades=0,
        fold_metrics=[],
    )
    strat = RuleStrategy(rule)
    broker = PaperBroker()
    broker.update_price("NIFTY_OPT", 100.0)
    ks = KillSwitch(path=tmp_path / ".ks")
    risk = RiskEngine(
        limits=RiskLimits(max_open_positions=5, max_daily_loss=50_000),
        capital=100_000.0,
        kill_switch=ks,
    )
    om = OrderManager(broker=broker, log_dir=tmp_path / "log")
    agent = TradingAgent(
        strategy=strat,
        broker=broker,
        risk=risk,
        order_mgr=om,
        instrument_key="NIFTY_OPT",
        lot_size=1,
    )
    broker.listener = on_paper_fill_factory(agent)
    return agent, broker


def test_agent_emits_bar_events(tmp_path):
    import pandas as pd
    from pathlib import Path

    events: list = []
    agent, _ = _make_agent(tmp_path)
    agent.event_hook = lambda kind, payload: events.append(kind)

    fixture = pd.read_csv(
        Path("data/fixtures/ohlcv_1m_sample.csv"), parse_dates=["ts"]
    )
    df = fixture[fixture["symbol"] == "NIFTY"].head(50).reset_index(drop=True)
    agent.prepare(df)
    for i in range(min(10, len(df) - 1)):
        bar = agent._prepared.iloc[i]
        agent.on_bar(bar, agent._prepared.iloc[:i+1])

    assert "bar" in events


def test_agent_respects_kill_switch(tmp_path):
    import pandas as pd
    from pathlib import Path

    agent, broker = _make_agent(tmp_path)
    broker.update_price("NIFTY_OPT", 100.0)
    agent.risk.kill_switch.engage("unit test")

    fixture = pd.read_csv(
        Path("data/fixtures/ohlcv_1m_sample.csv"), parse_dates=["ts"]
    )
    df = fixture[fixture["symbol"] == "NIFTY"].head(10).reset_index(drop=True)
    agent.prepare(df)
    # With kill switch on, no orders should be placed
    for i in range(5):
        bar = agent._prepared.iloc[i]
        agent.on_bar(bar, agent._prepared.iloc[:i+1])
    assert len(broker.positions()) == 0
    agent.risk.kill_switch.disengage()
