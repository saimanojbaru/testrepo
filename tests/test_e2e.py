"""
End-to-end integration test.

Validates the full agent loop without external dependencies:
  cost model -> risk engine -> paper broker -> decision logging

Does not require pandas or network. Good smoke test for Phase 8.
"""

import tempfile
import os
from datetime import datetime
from unittest.mock import MagicMock

from backtest.costs import CostModel
from risk.engine import RiskEngine, RiskConfig
from broker.paper import PaperBroker
from broker.base import Order, Quote


def test_full_trading_loop_paper_mode():
    """
    Simulate: signal -> risk check -> paper broker fill -> risk engine records P&L.
    """
    with tempfile.TemporaryDirectory() as tmp:
        # 1. Setup
        cost_model = CostModel(broker="upstox")
        risk = RiskEngine(RiskConfig(
            trading_capital=100000,
            max_loss_per_day=2000,
            kill_switch_file=os.path.join(tmp, ".ks"),
        ))

        # Paper broker with mock quote provider
        def quote_provider(key):
            return Quote(instrument_key=key, ltp=100.0, timestamp=datetime.now())

        paper = PaperBroker(slippage_bps=5, quote_provider=quote_provider)

        # 2. Pre-trade check
        allowed, reason = risk.can_trade()
        assert allowed, f"Risk blocked: {reason}"

        # 3. Size position
        lots = risk.calculate_position_size(
            entry_price=100,
            stop_loss_price=95,
            win_rate=0.55,
            avg_win_loss_ratio=1.5,
        )
        assert lots >= 1

        # 4. Entry order (buy)
        entry = Order(
            order_id="",
            instrument_key="NSE_INDEX|Nifty 50",
            symbol="NIFTY",
            side="buy",
            quantity=lots,
            order_type="market",
            price=100,
        )
        entry_filled = paper.place_order(entry)
        assert entry_filled.status == "filled"
        assert entry_filled.filled_price > 100  # slippage added

        risk.open_position({
            "id": entry_filled.order_id,
            "symbol": "NIFTY",
            "entry_price": entry_filled.filled_price,
            "quantity": lots,
        })

        print(f"  Entry: {lots} lots @ ₹{entry_filled.filled_price:.2f}")

        # 5. Simulate price movement (profitable exit)
        def new_quote(key):
            return Quote(instrument_key=key, ltp=105.0, timestamp=datetime.now())
        paper.quote_provider = new_quote

        exit = Order(
            order_id="",
            instrument_key="NSE_INDEX|Nifty 50",
            symbol="NIFTY",
            side="sell",
            quantity=lots,
            order_type="market",
            price=105,
        )
        exit_filled = paper.place_order(exit)
        assert exit_filled.status == "filled"

        # 6. Calculate P&L via cost model
        pnl_gross = (exit_filled.filled_price - entry_filled.filled_price) * lots
        costs = cost_model.calculate_roundtrip_cost(
            entry_filled.filled_price, exit_filled.filled_price, "index_option"
        )
        pnl_net = pnl_gross - costs["total_cost"]

        risk.record_trade(pnl_net)
        risk.close_position(entry_filled.order_id)

        print(f"  Exit: ₹{exit_filled.filled_price:.2f}")
        print(f"  Gross P&L: ₹{pnl_gross:.2f}")
        print(f"  Costs: ₹{costs['total_cost']:.2f}")
        print(f"  Net P&L: ₹{pnl_net:.2f}")

        assert pnl_net > 0  # Profitable trade
        assert risk.daily_pnl.realized_pnl > 0
        assert risk.daily_pnl.trades_count == 1

        # 7. Risk engine state
        status = risk.status()
        print(f"  Risk status: {status}")
        assert not status["halted"]
        assert status["daily_pnl"] > 0


def test_risk_halts_after_losses():
    """Losing streak should activate kill switch and block further trades."""
    with tempfile.TemporaryDirectory() as tmp:
        risk = RiskEngine(RiskConfig(
            trading_capital=100000,
            max_loss_per_day=2000,
            kill_switch_file=os.path.join(tmp, ".ks"),
        ))

        # Simulate 3 losing trades
        risk.record_trade(-800)
        risk.record_trade(-800)
        risk.record_trade(-800)  # Total: -2400, exceeds -2000 cap

        allowed, reason = risk.can_trade()
        assert not allowed
        print(f"  Blocked after ₹{risk.daily_pnl.realized_pnl} loss: {reason}")


def test_paper_broker_tracks_positions():
    """Paper broker correctly tracks position open/close."""
    def quote_provider(key):
        return Quote(instrument_key=key, ltp=100, timestamp=datetime.now())

    paper = PaperBroker(quote_provider=quote_provider)

    # Buy
    buy = Order("", "NIFTY_CE", "NIFTY", "buy", 10, "market", price=100)
    paper.place_order(buy)
    assert len(paper.get_positions()) == 1
    assert paper.get_positions()[0].quantity == 10

    # Sell (close)
    sell = Order("", "NIFTY_CE", "NIFTY", "sell", 10, "market", price=105)
    paper.place_order(sell)
    assert len(paper.get_positions()) == 0  # Closed

    # Trade log
    log = paper.get_trade_log()
    assert len(log) == 2
    assert log[0]["side"] == "buy"
    assert log[1]["side"] == "sell"
    print(f"  Trade log: {len(log)} entries, all paper_mode={log[0]['paper_mode']}")


if __name__ == "__main__":
    print("\n=== E2E Test 1: Full trading loop (paper) ===")
    test_full_trading_loop_paper_mode()

    print("\n=== E2E Test 2: Risk engine halts on loss ===")
    test_risk_halts_after_losses()

    print("\n=== E2E Test 3: Paper broker position tracking ===")
    test_paper_broker_tracks_positions()

    print("\n✓ All end-to-end tests passed!")
