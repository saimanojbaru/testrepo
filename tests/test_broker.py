from __future__ import annotations

import pytest

from broker.base import Order, OrderSide, OrderStatus, OrderType, Product
from broker.paper import FillEvent, PaperBroker


def _broker() -> PaperBroker:
    b = PaperBroker()
    b.update_price("NIFTY_OPT", 100.0)
    return b


def _buy(qty: int = 50, price: float = 100.0) -> Order:
    return Order(
        instrument_key="NIFTY_OPT",
        side=OrderSide.BUY,
        quantity=qty,
        order_type=OrderType.MARKET,
        product=Product.INTRADAY,
    )


def _sell(qty: int = 50, price: float = 100.0) -> Order:
    return Order(
        instrument_key="NIFTY_OPT",
        side=OrderSide.SELL,
        quantity=qty,
        order_type=OrderType.MARKET,
        product=Product.INTRADAY,
    )


def test_market_buy_fills_immediately():
    b = _broker()
    order = b.place_order(_buy())
    assert order.status == OrderStatus.FILLED
    assert order.filled_quantity == 50


def test_market_sell_fills_immediately():
    b = _broker()
    b.place_order(_buy())        # open long first
    order = b.place_order(_sell())
    assert order.status == OrderStatus.FILLED


def test_fill_updates_position():
    b = _broker()
    b.place_order(_buy())
    positions = b.positions()
    assert len(positions) == 1
    assert positions[0].quantity == 50


def test_round_trip_realized_pnl():
    b = _broker()
    b.place_order(_buy())
    # Price moves up
    b.update_price("NIFTY_OPT", 110.0)
    b.place_order(_sell())
    # Gross P&L = 10 * 50 = 500 minus costs
    assert b.pnl() > 0


def test_fill_listener_called_on_close():
    fills: list[FillEvent] = []
    b = PaperBroker(listener=lambda e: fills.append(e))
    b.update_price("NIFTY_OPT", 100.0)
    b.place_order(_buy())
    b.update_price("NIFTY_OPT", 105.0)
    b.place_order(_sell())
    closing = [f for f in fills if f.is_closing]
    assert len(closing) == 1
    assert closing[0].realized_pnl > 0


def test_square_off_all_clears_positions():
    b = _broker()
    b.place_order(_buy())
    assert len(b.positions()) == 1
    b.square_off_all()
    assert all(p.quantity == 0 for p in b.positions())


def test_no_reference_price_rejects():
    b = PaperBroker()
    order = b.place_order(
        Order(
            instrument_key="MISSING",
            side=OrderSide.BUY,
            quantity=50,
            order_type=OrderType.MARKET,
            product=Product.INTRADAY,
        )
    )
    assert order.status == OrderStatus.REJECTED
