"""
Risk engine tests.
"""

import os
import tempfile
from datetime import datetime

from risk.engine import RiskEngine, RiskConfig


def test_daily_loss_limit_triggers_kill_switch():
    """Losing beyond daily cap should activate kill switch."""
    with tempfile.TemporaryDirectory() as tmp:
        config = RiskConfig(
            trading_capital=100000,
            max_loss_per_day=2000,
            max_loss_percentage=0.02,
            kill_switch_file=os.path.join(tmp, ".kill_switch"),
        )
        engine = RiskEngine(config)

        assert not engine.check_kill_switch()

        # Record losing trades
        engine.record_trade(-1000)
        assert engine.can_trade()[0] is True

        engine.record_trade(-1500)  # Now at -2500, exceeds ₹2000 cap
        allowed, reason = engine.can_trade()

        assert not allowed
        assert "loss" in reason.lower() or "halted" in reason.lower() or "kill" in reason.lower()
        assert engine.check_kill_switch()

        print(f"  Daily loss test: {reason}")
        print(f"  Kill switch active: {engine.check_kill_switch()}")


def test_kelly_sizing():
    """Kelly sizing produces reasonable lot counts."""
    config = RiskConfig(trading_capital=100000, max_loss_percentage=0.02)
    engine = RiskEngine(config)

    # Profitable scenario: 60% win rate, 2:1 reward:risk
    lots = engine.calculate_position_size(
        entry_price=100,
        stop_loss_price=95,
        win_rate=0.60,
        avg_win_loss_ratio=2.0,
    )

    print(f"  Kelly sizing (60% WR, 2:1): {lots} lots")
    assert lots >= 1
    assert lots <= 500  # Sanity bound


def test_unprofitable_sizing_still_floor():
    """Losing strategy should still get min position size."""
    config = RiskConfig(trading_capital=100000)
    engine = RiskEngine(config)

    lots = engine.calculate_position_size(
        entry_price=100,
        stop_loss_price=95,
        win_rate=0.40,   # Losing
        avg_win_loss_ratio=1.0,
    )

    print(f"  Unprofitable Kelly sizing: {lots} lots (should be floor)")
    assert lots >= 1


def test_max_open_positions():
    """Can't exceed max open positions."""
    config = RiskConfig(max_open_positions=2)
    engine = RiskEngine(config)

    engine.open_position({"id": "1", "symbol": "NIFTY"})
    assert engine.can_trade()[0] is True

    engine.open_position({"id": "2", "symbol": "BANKNIFTY"})
    allowed, reason = engine.can_trade()
    assert not allowed
    assert "positions" in reason.lower()

    print(f"  Max positions test: {reason}")


def test_kill_switch_activation():
    """Manual kill switch works."""
    with tempfile.TemporaryDirectory() as tmp:
        config = RiskConfig(
            kill_switch_file=os.path.join(tmp, ".kill_switch"),
        )
        engine = RiskEngine(config)

        assert not engine.check_kill_switch()

        engine.activate_kill_switch("Manual test")
        assert engine.check_kill_switch()
        assert not engine.can_trade()[0]

        engine.clear_kill_switch()
        assert not engine.check_kill_switch()

        print("  Kill switch activate/clear: ✓")


if __name__ == "__main__":
    test_daily_loss_limit_triggers_kill_switch()
    test_kelly_sizing()
    test_unprofitable_sizing_still_floor()
    test_max_open_positions()
    test_kill_switch_activation()
    print("\n✓ All risk engine tests passed!")
