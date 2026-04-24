"""Event-driven intraday backtester.

- Consumes 1-minute candles.
- Resets positions + strategy state at each new trading day.
- Models execution delay (fill on the next bar's open) and slippage.
- Exits on SL, TP, time-stop, or end-of-day.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone

from ..config import settings
from ..domain.signals import Action, Candle, Signal
from ..domain.strategies.base import Strategy
from ..domain.trades import ClosedTrade, ExitReason, OpenPosition, Side
from .costs import CostModel
from .data_loader import split_by_day


@dataclass(slots=True)
class BacktestResult:
    strategy: str
    trades: list[ClosedTrade]
    equity_curve: list[tuple[datetime, float]]


class Backtester:
    def __init__(
        self,
        cost: CostModel | None = None,
        time_exit_seconds: int | None = None,
    ) -> None:
        self.cost = cost or CostModel(
            brokerage_per_trade=settings.backtest_brokerage_per_trade,
            slippage_bps=settings.backtest_slippage_bps,
            execution_delay_bars=settings.backtest_execution_delay_bars,
        )
        self.time_exit_seconds = time_exit_seconds or settings.signal_time_exit_seconds

    def run(self, strategy: Strategy, candles: list[Candle]) -> BacktestResult:
        trades: list[ClosedTrade] = []
        equity_curve: list[tuple[datetime, float]] = []
        equity = 0.0
        trade_id_seq = 0

        for day_slice in split_by_day(candles):
            strategy.reset()
            position: OpenPosition | None = None
            pending: tuple[Signal, int] | None = None  # (signal, bars_delayed_left)
            bars = day_slice.candles

            for i, bar in enumerate(bars):
                # 1. Check exits for an open position
                if position is not None:
                    exit_reason = self._check_exit(position, bar)
                    if exit_reason is not None:
                        closed, equity = self._close(
                            position, bar, exit_reason, equity, trade_id_seq
                        )
                        trades.append(closed)
                        equity_curve.append((bar.ts, equity))
                        position = None

                # 2. Execute pending signal if its delay has elapsed
                if pending is not None and position is None:
                    sig, remaining = pending
                    if remaining <= 0:
                        position = self._open(sig, bar, trade_id_seq + 1)
                        trade_id_seq += 1
                        pending = None
                    else:
                        pending = (sig, remaining - 1)

                # 3. Ask strategy for a new signal (skip if already positioned or pending)
                if position is None and pending is None:
                    sig = strategy.on_candle(bar)
                    if sig is not None and sig.action in (Action.BUY, Action.SELL):
                        pending = (sig, self.cost.execution_delay_bars)
                else:
                    # Feed bar to strategy so its state stays current, but ignore the signal
                    strategy.on_candle(bar)

            # End-of-day close
            if position is not None and bars:
                closed, equity = self._close(
                    position, bars[-1], ExitReason.TIME, equity, trade_id_seq
                )
                trades.append(closed)
                equity_curve.append((bars[-1].ts, equity))

        return BacktestResult(
            strategy=strategy.name, trades=trades, equity_curve=equity_curve
        )

    # ---- helpers ---------------------------------------------------------

    def _open(self, sig: Signal, bar: Candle, trade_id: int) -> OpenPosition:
        is_buy = sig.action is Action.BUY
        fill = self.cost.slip(bar.open, side_is_buy=is_buy)
        return OpenPosition(
            trade_id=trade_id,
            instrument=bar.instrument,
            side=Side.LONG if is_buy else Side.SHORT,
            entry=fill,
            stop_loss=sig.stop_loss,
            target=sig.target,
            opened_at=bar.ts,
            strategy=sig.strategy,
            qty=1,
        )

    def _check_exit(self, pos: OpenPosition, bar: Candle) -> ExitReason | None:
        if pos.side is Side.LONG:
            if bar.low <= pos.stop_loss:
                return ExitReason.STOP_LOSS
            if bar.high >= pos.target:
                return ExitReason.TARGET
        else:
            if bar.high >= pos.stop_loss:
                return ExitReason.STOP_LOSS
            if bar.low <= pos.target:
                return ExitReason.TARGET

        age = (bar.ts - pos.opened_at).total_seconds()
        if age >= self.time_exit_seconds:
            return ExitReason.TIME
        return None

    def _close(
        self,
        pos: OpenPosition,
        bar: Candle,
        reason: ExitReason,
        equity: float,
        trade_id_seq: int,
    ) -> tuple[ClosedTrade, float]:
        # Exit price = the level that triggered, slipped against us
        if reason is ExitReason.STOP_LOSS:
            raw = pos.stop_loss
        elif reason is ExitReason.TARGET:
            raw = pos.target
        else:
            raw = bar.close
        is_buy_exit = pos.side is Side.SHORT  # closing a short = buy
        fill = self.cost.slip(raw, side_is_buy=is_buy_exit)

        gross = pos.mtm(fill)
        costs = self.cost.round_trip_cost(pos.qty)
        equity += gross - costs

        closed = ClosedTrade(
            trade_id=pos.trade_id,
            instrument=pos.instrument,
            side=pos.side,
            entry=pos.entry,
            exit_price=fill,
            qty=pos.qty,
            gross_pnl=gross,
            costs=costs,
            reason=reason,
            strategy=pos.strategy,
            opened_at=pos.opened_at,
            closed_at=bar.ts.astimezone(timezone.utc) if bar.ts.tzinfo else bar.ts,
        )
        return closed, equity
