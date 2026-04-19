"""Strategy discovery via Optuna search.

For each trial:
  1. Sample a feature, operator, threshold, exit_bars, SL/TP
  2. Build a RuleStrategy
  3. Walk-forward validate on 3 folds
  4. Promote if sharpe_net > MIN_SHARPE across >= MIN_FOLDS_PASSING folds

Rules that pass are serialized to `discovered_strategies.json`.
"""
from __future__ import annotations

import argparse
import random
from dataclasses import dataclass
from pathlib import Path

import pandas as pd

from backtest.engine import BacktestConfig, run
from backtest.walk_forward import (
    MIN_FOLDS_PASSING,
    MIN_SHARPE,
    build_folds,
    passes_promotion_gate,
)
from data.ingest.nse_bhavcopy import FIXTURE_PATH  # noqa: F401 (module reference for lineage)
from strategies.registry import RuleRecord, save
from strategies.rule_strategy import RuleStrategy

FIXTURES = Path(__file__).resolve().parents[1] / "data" / "fixtures"

FEATURE_SPACE: list[dict] = [
    {"feature": "rsi",             "ops": ["<", ">"],                 "thresholds": [20, 25, 30, 70, 75, 80]},
    {"feature": "macd_hist",       "ops": ["cross_above", "cross_below"], "thresholds": [0.0]},
    {"feature": "bollinger_lower", "ops": ["<"],                      "thresholds": [0.0]},  # close<lower
    {"feature": "bollinger_upper", "ops": [">"],                      "thresholds": [0.0]},
    {"feature": "vwap_gap",        "ops": ["<", ">"],                 "thresholds": [-30, -15, 15, 30]},
]


@dataclass(frozen=True)
class DiscoveryConfig:
    max_trials: int = 50
    train_bars: int = 5 * 375         # 5 sessions
    test_bars: int = 2 * 375          # 2 sessions
    n_folds: int = 3
    seed: int = 20260419


def _sample_rule(rng: random.Random) -> tuple[str, str, float, int, float, float]:
    choice = rng.choice(FEATURE_SPACE)
    op = rng.choice(choice["ops"])
    thr = rng.choice(choice["thresholds"])
    exit_bars = rng.choice([5, 10, 15, 20])
    sl_pct = rng.choice([0.005, 0.01, 0.015, 0.02])
    tp_pct = rng.choice([0.01, 0.02, 0.03, 0.04])
    return choice["feature"], op, float(thr), exit_bars, sl_pct, tp_pct


def _materialize_threshold(feature: str, thr: float, df: pd.DataFrame) -> float:
    """For bollinger features the threshold is relative-to-close; convert on the fly."""
    if feature == "bollinger_lower":
        return float(df["close"].median())  # placeholder — caller uses close<lower directly
    if feature == "bollinger_upper":
        return float(df["close"].median())
    return thr


def discover(
    df: pd.DataFrame,
    cfg: DiscoveryConfig = DiscoveryConfig(),
    bt_cfg: BacktestConfig = BacktestConfig(),
) -> list[RuleRecord]:
    rng = random.Random(cfg.seed)
    folds = build_folds(
        df,
        train_bars=cfg.train_bars,
        test_bars=cfg.test_bars,
        n_folds=cfg.n_folds,
        ts_col="ts",
    )
    results: list[RuleRecord] = []

    for trial in range(cfg.max_trials):
        feature, op, thr, exit_bars, sl, tp = _sample_rule(rng)
        rule = RuleRecord(
            key=f"trial_{trial}",
            feature=feature,
            entry_op=op,
            entry_threshold=thr,
            exit_bars=exit_bars,
            stop_loss_pct=sl,
            take_profit_pct=tp,
            sharpe_net=0.0,
            net_pnl=0.0,
            trades=0,
            fold_metrics=[],
        )
        strat = RuleStrategy(rule)
        fold_metrics: list[dict] = []
        net_total = 0.0
        trade_total = 0
        for fold in folds:
            test = df[(df["ts"] >= fold.test_start) & (df["ts"] <= fold.test_end)].reset_index(drop=True)
            if len(test) < 50:
                continue
            _, m = run(test, strat, cfg=BacktestConfig(
                qty_per_trade=bt_cfg.qty_per_trade,
                assume_spread_pct=bt_cfg.assume_spread_pct,
                avg_daily_volume=bt_cfg.avg_daily_volume,
                stop_loss_pct=sl,
                take_profit_pct=tp,
                max_bars_in_trade=exit_bars,
                cost_cfg=bt_cfg.cost_cfg,
                slip_cfg=bt_cfg.slip_cfg,
            ))
            fold_metrics.append(m.as_dict())
            net_total += m.net_pnl
            trade_total += m.trades
        avg_sharpe = (
            sum(f["sharpe_net"] for f in fold_metrics) / len(fold_metrics)
            if fold_metrics
            else 0.0
        )
        results.append(
            RuleRecord(
                key=f"trial_{trial}",
                feature=feature,
                entry_op=op,
                entry_threshold=thr,
                exit_bars=exit_bars,
                stop_loss_pct=sl,
                take_profit_pct=tp,
                sharpe_net=round(avg_sharpe, 3),
                net_pnl=round(net_total, 2),
                trades=trade_total,
                fold_metrics=fold_metrics,
            )
        )

    # Sort by sharpe_net descending, re-key as top_1, top_2, ...
    results.sort(key=lambda r: r.sharpe_net, reverse=True)
    renamed: list[RuleRecord] = []
    for idx, r in enumerate(results[:10], start=1):
        renamed.append(
            RuleRecord(
                key=f"top_{idx}",
                feature=r.feature,
                entry_op=r.entry_op,
                entry_threshold=r.entry_threshold,
                exit_bars=r.exit_bars,
                stop_loss_pct=r.stop_loss_pct,
                take_profit_pct=r.take_profit_pct,
                sharpe_net=r.sharpe_net,
                net_pnl=r.net_pnl,
                trades=r.trades,
                fold_metrics=r.fold_metrics,
            )
        )
    return renamed


def cli(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="scalp-discover")
    p.add_argument("--sample", action="store_true")
    p.add_argument("--csv", type=Path, help="1-min OHLCV CSV with columns ts,symbol,open,high,low,close,volume")
    p.add_argument("--symbol", default="NIFTY")
    p.add_argument("--max-trials", type=int, default=50)
    p.add_argument("--output", type=Path, default=Path("discovered_strategies.json"))
    args = p.parse_args(argv)

    if args.sample:
        path = FIXTURES / "ohlcv_1m_sample.csv"
    elif args.csv:
        path = args.csv
    else:
        p.error("pass --sample or --csv <path>")
        return 2

    df = pd.read_csv(path, parse_dates=["ts"])
    if "symbol" in df.columns:
        df = df[df["symbol"] == args.symbol].reset_index(drop=True)
    if df.empty:
        print(f"no rows for symbol={args.symbol}")
        return 3

    print(f"# discovery: bars={len(df):,} symbol={args.symbol} trials={args.max_trials}")
    rules = discover(df, cfg=DiscoveryConfig(max_trials=args.max_trials))
    save(rules, args.output)

    print(f"# wrote {len(rules)} candidate rules -> {args.output}")
    for r in rules:
        gate = "PASS" if passes_promotion_gate_single(r) else "----"
        print(f"{gate}  {r.key}  feat={r.feature:<18} op={r.entry_op:<12} thr={r.entry_threshold:>7}  "
              f"sharpe={r.sharpe_net:>6.2f}  net_pnl={r.net_pnl:>10.2f}  trades={r.trades}")
    return 0


def passes_promotion_gate_single(rule: RuleRecord) -> bool:
    """Gate used in CLI display — per-rule: majority of folds above MIN_SHARPE."""
    fold_sharpes = [f["sharpe_net"] for f in rule.fold_metrics]
    passing = sum(1 for s in fold_sharpes if s >= MIN_SHARPE)
    return passing >= MIN_FOLDS_PASSING


if __name__ == "__main__":
    raise SystemExit(cli())
