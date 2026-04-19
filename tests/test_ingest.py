from __future__ import annotations

import json
from types import SimpleNamespace

import pandas as pd

from data.ingest.completeness_report import CompletenessReport, analyze
from data.ingest.nse_bhavcopy import load_sample
from data.ingest.quandl_loader import parse_response
from data.ingest.upstox_historical import parse_candles


def test_bhavcopy_fixture_loads():
    df = load_sample()
    assert not df.empty
    assert {"symbol", "date", "open", "high", "low", "close", "volume"}.issubset(df.columns)


def test_quandl_parser():
    payload = {
        "dataset": {
            "column_names": ["date", "open", "high", "low", "close"],
            "data": [["2026-04-01", 100, 101, 99, 100.5]],
        }
    }
    df = parse_response(payload).to_frame()
    assert len(df) == 1
    assert list(df.columns) == ["date", "open", "high", "low", "close"]


def test_upstox_candle_parser():
    payload = {
        "data": {
            "candles": [
                ["2026-04-01T09:15:00+05:30", 100.0, 101.0, 99.5, 100.5, 1000, 0],
            ]
        }
    }
    df = parse_candles(payload)
    assert len(df) == 1
    assert df["close"].iloc[0] == 100.5
    assert str(df["ts"].dt.tz) == "Asia/Kolkata"


def test_completeness_flags_gap_correctly():
    # Fixture has ~8k bars for NIFTY; 5yr target ~ 472,500 -> gap ~98% -> go_paid=True
    fixture_df = pd.read_csv(
        "data/fixtures/ohlcv_1m_sample.csv", parse_dates=["ts"]
    )
    reports = analyze(fixture_df, target_years=5, gap_threshold=0.20)
    assert len(reports) == 2
    assert all(isinstance(r, CompletenessReport) for r in reports)
    # With only 22 days of fixture data the gap must be huge -> escalate
    assert all(r.go_paid for r in reports)


def test_completeness_passes_when_data_is_complete():
    # Fake a minute DataFrame with enough bars to meet a 0.1-year target
    idx = pd.date_range("2026-01-01 09:15", periods=10_000, freq="min", tz="Asia/Kolkata")
    df = pd.DataFrame({"ts": idx, "symbol": "TEST", "open": 1, "high": 1, "low": 1, "close": 1, "volume": 1})
    reports = analyze(df, target_years=0.1, gap_threshold=0.20)
    assert not reports[0].go_paid
