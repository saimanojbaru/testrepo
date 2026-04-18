"""
Trading Agent: end-to-end orchestrator.

Loop on each bar (or tick):
1. Fetch latest features from data store
2. Classify market regime
3. Look up best strategy for that regime
4. Generate signal from strategy
5. Risk engine validates + sizes position
6. Broker executes (paper or live)
7. Log decision + outcome
"""

import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Optional, Dict
import pandas as pd

from regime.classifier import MarketRegimeClassifier
from risk.engine import RiskEngine, RiskConfig
from broker.base import Broker, Order
from strategies.base import Strategy, Context, Signal


logger = logging.getLogger(__name__)


@dataclass
class AgentConfig:
    """Agent configuration."""
    paper_mode: bool = True
    log_all_decisions: bool = True
    min_signal_confidence: float = 0.5
    instrument_key: str = "NSE_INDEX|Nifty 50"
    symbol: str = "NIFTY"


class TradingAgent:
    """
    End-to-end trading agent that combines all modules.

    Components:
    - regime_classifier: Picks regime per bar
    - strategy_registry: Maps regime → Strategy instance
    - risk_engine: Gatekeeper for all orders
    - broker: Executes (paper or live)
    """

    def __init__(
        self,
        regime_classifier: MarketRegimeClassifier,
        strategy_registry: Dict[int, Strategy],
        risk_engine: RiskEngine,
        broker: Broker,
        config: AgentConfig = None,
    ):
        self.regime_classifier = regime_classifier
        self.strategy_registry = strategy_registry
        self.risk_engine = risk_engine
        self.broker = broker
        self.config = config or AgentConfig()

        self.decision_log = []
        self.current_position_id: Optional[str] = None

    def on_bar(self, df: pd.DataFrame, current_index: int) -> Optional[Order]:
        """
        Called on each new bar of data.

        Returns:
            Order placed (or None if no trade)
        """
        # 1. Pre-flight risk check
        allowed, reason = self.risk_engine.can_trade()
        if not allowed:
            self._log_decision(df, current_index, None, None, f"RISK_BLOCK: {reason}")
            return None

        # 2. Extract regime features
        row = df.iloc[current_index]
        regime_features = {
            c: row.get(c, 0) for c in self.regime_classifier.feature_cols
        }

        # 3. Classify regime
        regime_label = self.regime_classifier.predict(regime_features)

        # 4. Select strategy for this regime
        strategy = self.strategy_registry.get(regime_label)
        if strategy is None:
            self._log_decision(df, current_index, regime_label, None, "NO_STRATEGY")
            return None

        # 5. Get signal from strategy
        position_dict = (
            {"id": self.current_position_id} if self.current_position_id else None
        )
        ctx = Context(
            df=df,
            current_index=current_index,
            position=position_dict,
            regime=str(regime_label),
        )
        signal = strategy.on_bar(ctx)

        if signal is None or signal.action == "hold":
            self._log_decision(df, current_index, regime_label, signal, "HOLD")
            return None

        if signal.confidence < self.config.min_signal_confidence:
            self._log_decision(df, current_index, regime_label, signal, "LOW_CONFIDENCE")
            return None

        # 6. Size position via risk engine
        entry_price = signal.target_price or row["close"]
        stop_loss = signal.stop_loss or (entry_price * 0.98 if signal.action == "buy" else entry_price * 1.02)

        lots = self.risk_engine.calculate_position_size(
            entry_price=entry_price,
            stop_loss_price=stop_loss,
            win_rate=0.55,  # Could be dynamic per strategy
            avg_win_loss_ratio=1.5,
        )

        # 7. Execute
        order = Order(
            order_id="",
            instrument_key=self.config.instrument_key,
            symbol=self.config.symbol,
            side=signal.action,
            quantity=lots,
            order_type="market",
            price=entry_price,
            timestamp=signal.timestamp or datetime.now(),
        )

        placed = self.broker.place_order(order)

        # 8. Update state
        if placed.status == "filled":
            if signal.action == "buy":
                self.current_position_id = placed.order_id
                self.risk_engine.open_position({
                    "id": placed.order_id,
                    "symbol": self.config.symbol,
                    "entry_price": placed.filled_price,
                    "quantity": placed.filled_quantity,
                })
            elif signal.action == "sell" and self.current_position_id:
                # Exit: calculate P&L
                positions = self.broker.get_positions()
                for p in positions:
                    if p.instrument_key == self.config.instrument_key:
                        pnl = (placed.filled_price - p.average_price) * placed.filled_quantity
                        self.risk_engine.record_trade(pnl)
                        break
                self.risk_engine.close_position(self.current_position_id)
                self.current_position_id = None

        self._log_decision(df, current_index, regime_label, signal, "EXECUTED", placed)
        return placed

    def _log_decision(
        self,
        df: pd.DataFrame,
        idx: int,
        regime: Optional[int],
        signal: Optional[Signal],
        action: str,
        order: Optional[Order] = None,
    ):
        """Log every decision for reconciliation + learning."""
        if not self.config.log_all_decisions:
            return

        entry = {
            "timestamp": df.iloc[idx].get("timestamp", datetime.now()),
            "regime": regime,
            "action": action,
            "signal_action": signal.action if signal else None,
            "signal_confidence": signal.confidence if signal else None,
            "strategy": signal.strategy_name if signal else None,
            "order_id": order.order_id if order else None,
            "fill_price": order.filled_price if order else None,
        }
        self.decision_log.append(entry)
        logger.info(f"[Agent] {entry}")

    def run_on_dataframe(self, df: pd.DataFrame):
        """Replay agent over a DataFrame (backtest / paper replay)."""
        for i in range(len(df)):
            self.on_bar(df, i)

    def status(self) -> dict:
        """Current agent status."""
        return {
            "paper_mode": self.config.paper_mode,
            "open_position": self.current_position_id,
            "risk_status": self.risk_engine.status(),
            "total_decisions": len(self.decision_log),
        }
