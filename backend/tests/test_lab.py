"""Strategy lab integration test."""

from __future__ import annotations

from pathlib import Path

from app.lab.runner import run_lab
from app.reporting.claude_analyst import ClaudeAnalyst


def test_lab_runs_all_strategies_over_sample_csv():
    path = Path(__file__).parent.parent / "data" / "sample_ohlcv.csv"
    result = run_lab(csv_path=str(path), instrument="NIFTY")
    assert result.candle_count > 30
    # 3 strategies registered
    assert len(result.strategy_metrics) == 3
    names = {m.strategy for m in result.strategy_metrics}
    assert names == {"momentum_breakout", "reversal_scalp", "range_breakout"}


def test_claude_analyst_heuristic_fallback_without_key():
    from app.backtest.metrics import Metrics

    analyst = ClaudeAnalyst(api_key=None)
    metrics = [
        Metrics(
            strategy="momentum_breakout",
            total_trades=30,
            win_rate=0.28,
            net_pnl=-450.0,
            gross_pnl=-300.0,
            costs=150.0,
            max_drawdown=900.0,
            profit_factor=0.7,
            avg_profit=50.0,
            avg_loss=-80.0,
            expectancy=-26.4,
        ),
        Metrics(
            strategy="reversal_scalp",
            total_trades=12,
            win_rate=0.58,
            net_pnl=320.0,
            gross_pnl=400.0,
            costs=80.0,
            max_drawdown=120.0,
            profit_factor=2.1,
            avg_profit=90.0,
            avg_loss=-45.0,
            expectancy=33.3,
        ),
    ]
    report = analyst.analyze(metrics, trades_by_strategy={})
    assert report.stub is True
    assert "reversal_scalp" in report.markdown
    assert "momentum_breakout" in report.markdown
    # The heuristic should flag the failing strategy
    assert "low win rate" in report.markdown.lower()
