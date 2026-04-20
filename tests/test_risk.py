from __future__ import annotations

import datetime as dt

import pytest

from risk.engine import RiskEngine, RiskViolationKind
from risk.kill_switch import KillSwitch
from risk.limits import RiskLimits
from risk.sizer import KellyStats, kelly_fraction, position_size


def _engine(tmp_path=None, **limit_overrides) -> tuple[RiskEngine, KillSwitch]:
    ks = KillSwitch(path=(tmp_path / ".ks") if tmp_path else __import__("pathlib").Path("/tmp/.ks_test"))
    limits = RiskLimits(**limit_overrides)
    engine = RiskEngine(limits=limits, capital=100_000.0, kill_switch=ks)
    return engine, ks


# --- Kelly sizer ---
def test_kelly_positive_edge():
    k = kelly_fraction(KellyStats(win_rate=0.60, avg_win=300.0, avg_loss=200.0))
    assert 0 < k <= 0.25


def test_kelly_zero_edge_returns_zero():
    k = kelly_fraction(KellyStats(win_rate=0.40, avg_win=100.0, avg_loss=200.0))
    assert k == 0.0


def test_kelly_capped_at_max():
    k = kelly_fraction(
        KellyStats(win_rate=0.99, avg_win=10_000.0, avg_loss=1.0),
        max_fraction=0.10,
    )
    assert k <= 0.10


def test_position_size_returns_int_lots():
    n = position_size(capital=100_000, price_per_contract=100.0, lot_size=50, fraction=0.05)
    assert isinstance(n, int)
    assert n >= 0


# --- Kill switch ---
def test_kill_switch_deny(tmp_path):
    eng, ks = _engine(tmp_path=tmp_path)
    ks.engage("test")
    decision = eng.evaluate("NSE_IDX|NF", 100.0, 50, KellyStats(0.55, 200.0, 150.0))
    assert not decision.approved
    assert decision.violation.kind == RiskViolationKind.KILL_SWITCH
    ks.disengage()


def test_daily_loss_cap_deny(tmp_path):
    eng, _ = _engine(tmp_path=tmp_path, max_daily_loss=500.0)
    eng.daily_pnl = -600.0
    decision = eng.evaluate("NSE_IDX|NF", 100.0, 50, KellyStats(0.55, 200.0, 150.0))
    assert not decision.approved
    assert decision.violation.kind == RiskViolationKind.DAILY_LOSS_CAP


def test_max_open_positions_deny(tmp_path):
    eng, _ = _engine(tmp_path=tmp_path, max_open_positions=1)
    eng.open_positions = 1
    decision = eng.evaluate("NSE_IDX|NF", 100.0, 50, KellyStats(0.55, 200.0, 150.0))
    assert not decision.approved
    assert decision.violation.kind == RiskViolationKind.MAX_OPEN_POSITIONS


def test_approved_trade_updates_counts(tmp_path):
    eng, _ = _engine(tmp_path=tmp_path)
    decision = eng.evaluate("NSE_IDX|NF", 100.0, 50, KellyStats(0.55, 200.0, 150.0))
    assert decision.approved
    eng.on_position_opened()
    assert eng.trades_today == 1
    assert eng.open_positions == 1
    eng.on_trade_closed(150.0)
    assert eng.daily_pnl == 150.0
    assert eng.open_positions == 0


def test_on_violation_callback_fired(tmp_path):
    violations: list = []
    eng, ks = _engine(tmp_path=tmp_path)
    eng.on_violation = violations.append
    ks.engage("cb_test")
    eng.evaluate("X", 100.0, 50, KellyStats(0.55, 200.0, 150.0))
    assert len(violations) == 1
    ks.disengage()


def test_day_roll_resets_daily_pnl(tmp_path):
    eng, _ = _engine(tmp_path=tmp_path)
    eng.daily_pnl = -300.0
    eng.trades_today = 5
    yesterday = dt.date.today() - dt.timedelta(days=1)
    eng.current_day = yesterday
    # Trigger roll by evaluating on today
    eng.evaluate("NSE_IDX|NF", 100.0, 50, KellyStats(0.55, 200.0, 150.0))
    assert eng.daily_pnl == 0.0
    assert eng.trades_today == 0
