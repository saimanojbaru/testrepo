"""Generate deterministic fixture data for tests and the discovery demo.

Running this script regenerates CSVs under data/fixtures/. The seed is fixed so
fixtures are reproducible and can be committed to the repo.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

FIXTURES_DIR = Path(__file__).parent
SEED = 20260419


def _synth_minute_bars(
    symbol: str,
    start: str,
    days: int,
    base_price: float,
    vol: float,
    drift: float,
    rng: np.random.Generator,
) -> pd.DataFrame:
    """Generate 1-minute bars for an Indian trading day: 09:15 -> 15:30 (375 mins)."""
    rows: list[dict] = []
    price = base_price
    day = pd.Timestamp(start, tz="Asia/Kolkata")
    bars_per_day = 375

    for _ in range(days):
        if day.weekday() >= 5:
            day += pd.Timedelta(days=1)
            continue
        session_start = day.replace(hour=9, minute=15, second=0, microsecond=0)
        # daily drift + intraday mean-reverting noise
        log_returns = rng.normal(loc=drift / bars_per_day, scale=vol, size=bars_per_day)
        closes = price * np.exp(np.cumsum(log_returns))
        highs = closes * (1.0 + np.abs(rng.normal(0, vol / 2, bars_per_day)))
        lows = closes * (1.0 - np.abs(rng.normal(0, vol / 2, bars_per_day)))
        opens = np.concatenate(([price], closes[:-1]))
        vols = rng.integers(low=5_000, high=50_000, size=bars_per_day)

        for i in range(bars_per_day):
            rows.append(
                {
                    "ts": session_start + pd.Timedelta(minutes=i),
                    "symbol": symbol,
                    "open": round(float(opens[i]), 2),
                    "high": round(float(max(opens[i], highs[i], closes[i])), 2),
                    "low": round(float(min(opens[i], lows[i], closes[i])), 2),
                    "close": round(float(closes[i]), 2),
                    "volume": int(vols[i]),
                }
            )
        price = float(closes[-1])
        day += pd.Timedelta(days=1)
    return pd.DataFrame(rows)


def _synth_eod(symbol: str, minute_df: pd.DataFrame) -> pd.DataFrame:
    out = (
        minute_df.assign(date=lambda d: d["ts"].dt.date)
        .groupby("date")
        .agg(
            open=("open", "first"),
            high=("high", "max"),
            low=("low", "min"),
            close=("close", "last"),
            volume=("volume", "sum"),
        )
        .reset_index()
    )
    out.insert(1, "symbol", symbol)
    return out


def generate_all() -> None:
    FIXTURES_DIR.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(SEED)

    nifty_min = _synth_minute_bars("NIFTY", "2026-03-02", 22, 22_000.0, 0.0012, 0.00008, rng)
    bn_min = _synth_minute_bars("BANKNIFTY", "2026-03-02", 22, 48_000.0, 0.0018, 0.00004, rng)
    minute = pd.concat([nifty_min, bn_min], ignore_index=True)
    minute.to_csv(FIXTURES_DIR / "ohlcv_1m_sample.csv", index=False)

    eod = pd.concat(
        [_synth_eod("NIFTY", nifty_min), _synth_eod("BANKNIFTY", bn_min)],
        ignore_index=True,
    )
    eod.to_csv(FIXTURES_DIR / "bhavcopy_sample.csv", index=False)

    # Synthetic option chain for discovery features: 5 strikes around ATM, CE+PE, 22 days
    chain_rows: list[dict] = []
    for symbol, underlying_df in [("NIFTY", nifty_min), ("BANKNIFTY", bn_min)]:
        eod_prices = underlying_df.groupby(underlying_df["ts"].dt.date)["close"].last()
        for date, underlying_close in eod_prices.items():
            atm = round(underlying_close / 50.0) * 50.0
            strikes = [atm + 50.0 * k for k in range(-2, 3)]
            for strike in strikes:
                for opt_type in ("CE", "PE"):
                    intrinsic = max(
                        0.0,
                        (underlying_close - strike) if opt_type == "CE" else (strike - underlying_close),
                    )
                    time_value = rng.uniform(15, 80)
                    premium = round(intrinsic + time_value, 2)
                    iv = round(rng.uniform(0.12, 0.22), 4)
                    oi = int(rng.integers(10_000, 200_000))
                    vol_ = int(rng.integers(500, 30_000))
                    chain_rows.append(
                        {
                            "date": date,
                            "underlying": symbol,
                            "expiry": date,
                            "strike": strike,
                            "option_type": opt_type,
                            "ltp": premium,
                            "bid": round(premium - 0.25, 2),
                            "ask": round(premium + 0.25, 2),
                            "iv": iv,
                            "open_interest": oi,
                            "volume": vol_,
                        }
                    )
    pd.DataFrame(chain_rows).to_csv(FIXTURES_DIR / "options_chain_sample.csv", index=False)


if __name__ == "__main__":
    generate_all()
    print(f"Fixtures regenerated under {FIXTURES_DIR}")
