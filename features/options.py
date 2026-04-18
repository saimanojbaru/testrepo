"""
Options-specific features for scalping:
- Black-Scholes greeks (delta, gamma, theta, vega)
- IV (implied volatility) via Newton-Raphson
- IV rank, IV percentile
- Put-Call Ratio (PCR)
- Max pain
- OI buildup
"""

import math
from typing import Literal, Optional, Dict
import pandas as pd
import numpy as np


def _norm_cdf(x: float) -> float:
    """Standard normal CDF."""
    return 0.5 * (1 + math.erf(x / math.sqrt(2)))


def _norm_pdf(x: float) -> float:
    """Standard normal PDF."""
    return math.exp(-0.5 * x * x) / math.sqrt(2 * math.pi)


def black_scholes_price(
    spot: float,
    strike: float,
    tte_years: float,
    iv: float,
    option_type: Literal["CE", "PE"],
    rate: float = 0.06,
) -> float:
    """
    Black-Scholes option price.

    Args:
        spot: Current spot price
        strike: Strike price
        tte_years: Time to expiry in years
        iv: Implied volatility (annualized)
        option_type: 'CE' (call) or 'PE' (put)
        rate: Risk-free rate (6% default for India)
    """
    if tte_years <= 0 or iv <= 0:
        return max(0, spot - strike) if option_type == "CE" else max(0, strike - spot)

    d1 = (math.log(spot / strike) + (rate + 0.5 * iv * iv) * tte_years) / (iv * math.sqrt(tte_years))
    d2 = d1 - iv * math.sqrt(tte_years)

    if option_type == "CE":
        return spot * _norm_cdf(d1) - strike * math.exp(-rate * tte_years) * _norm_cdf(d2)
    else:
        return strike * math.exp(-rate * tte_years) * _norm_cdf(-d2) - spot * _norm_cdf(-d1)


def greeks(
    spot: float,
    strike: float,
    tte_years: float,
    iv: float,
    option_type: Literal["CE", "PE"],
    rate: float = 0.06,
) -> Dict[str, float]:
    """
    Calculate Black-Scholes greeks.

    Returns dict with: delta, gamma, theta, vega, rho
    """
    if tte_years <= 0 or iv <= 0:
        return {"delta": 0, "gamma": 0, "theta": 0, "vega": 0, "rho": 0}

    d1 = (math.log(spot / strike) + (rate + 0.5 * iv * iv) * tte_years) / (iv * math.sqrt(tte_years))
    d2 = d1 - iv * math.sqrt(tte_years)

    # Delta
    if option_type == "CE":
        delta = _norm_cdf(d1)
    else:
        delta = _norm_cdf(d1) - 1

    # Gamma (same for CE/PE)
    gamma = _norm_pdf(d1) / (spot * iv * math.sqrt(tte_years))

    # Theta (per day, not per year)
    term1 = -(spot * _norm_pdf(d1) * iv) / (2 * math.sqrt(tte_years))
    if option_type == "CE":
        term2 = -rate * strike * math.exp(-rate * tte_years) * _norm_cdf(d2)
    else:
        term2 = rate * strike * math.exp(-rate * tte_years) * _norm_cdf(-d2)
    theta = (term1 + term2) / 365

    # Vega (per 1% change in IV, not per unit)
    vega = spot * _norm_pdf(d1) * math.sqrt(tte_years) / 100

    # Rho
    if option_type == "CE":
        rho = strike * tte_years * math.exp(-rate * tte_years) * _norm_cdf(d2) / 100
    else:
        rho = -strike * tte_years * math.exp(-rate * tte_years) * _norm_cdf(-d2) / 100

    return {
        "delta": delta,
        "gamma": gamma,
        "theta": theta,
        "vega": vega,
        "rho": rho,
    }


def implied_volatility(
    market_price: float,
    spot: float,
    strike: float,
    tte_years: float,
    option_type: Literal["CE", "PE"],
    rate: float = 0.06,
    max_iter: int = 100,
    tolerance: float = 1e-5,
) -> Optional[float]:
    """
    Solve for IV via Newton-Raphson.

    Returns IV (annualized, e.g. 0.20 = 20%) or None if no convergence.
    """
    if market_price <= 0 or tte_years <= 0:
        return None

    iv = 0.20  # Initial guess

    for _ in range(max_iter):
        price = black_scholes_price(spot, strike, tte_years, iv, option_type, rate)
        diff = market_price - price

        if abs(diff) < tolerance:
            return iv

        # Vega (derivative of price w.r.t. iv)
        d1 = (math.log(spot / strike) + (rate + 0.5 * iv * iv) * tte_years) / (iv * math.sqrt(tte_years))
        vega_raw = spot * _norm_pdf(d1) * math.sqrt(tte_years)

        if vega_raw < 1e-10:
            break

        iv += diff / vega_raw
        iv = max(0.001, min(5.0, iv))  # Clamp to reasonable range

    return iv if 0.001 < iv < 5.0 else None


def iv_rank(current_iv: float, iv_history: pd.Series) -> float:
    """
    IV Rank = (current IV - min IV) / (max IV - min IV) * 100
    Range: 0..100
    """
    if iv_history.empty:
        return 50
    iv_min = iv_history.min()
    iv_max = iv_history.max()
    if iv_max == iv_min:
        return 50
    return (current_iv - iv_min) / (iv_max - iv_min) * 100


def iv_percentile(current_iv: float, iv_history: pd.Series) -> float:
    """
    IV Percentile = % of days where IV was below current_iv.
    Range: 0..100
    """
    if iv_history.empty:
        return 50
    return (iv_history < current_iv).mean() * 100


def put_call_ratio(df_chain: pd.DataFrame, by: str = "oi") -> float:
    """
    Put-Call Ratio from options chain.

    Args:
        df_chain: DataFrame with columns [option_type, oi, volume]
        by: 'oi' or 'volume'

    Returns:
        PCR value. PCR > 1 = bearish sentiment, < 1 = bullish
    """
    if df_chain.empty or by not in df_chain.columns:
        return 1.0

    puts = df_chain[df_chain["option_type"] == "PE"][by].sum()
    calls = df_chain[df_chain["option_type"] == "CE"][by].sum()

    return puts / calls if calls > 0 else 0


def max_pain(df_chain: pd.DataFrame) -> Optional[float]:
    """
    Strike at which option writers (sellers) have minimum payout.
    Often acts as a magnet for expiry price.

    Args:
        df_chain: DataFrame with columns [strike, option_type, oi]

    Returns:
        Max pain strike
    """
    if df_chain.empty:
        return None

    strikes = sorted(df_chain["strike"].unique())
    pain_at_strike = {}

    for s in strikes:
        # For each strike, calculate total writer pain if expiry settled here
        pain = 0
        for _, row in df_chain.iterrows():
            k = row["strike"]
            oi = row.get("oi", 0)
            if row["option_type"] == "CE":
                pain += max(s - k, 0) * oi
            else:  # PE
                pain += max(k - s, 0) * oi
        pain_at_strike[s] = pain

    # Max pain = strike with MIN total writer payout
    return min(pain_at_strike, key=pain_at_strike.get)


def add_options_features(df: pd.DataFrame, spot_col: str = "spot", strike_col: str = "strike",
                          tte_col: str = "tte_years", iv_col: str = "iv",
                          option_type_col: str = "option_type") -> pd.DataFrame:
    """
    Add greeks + IV-based features to an options DataFrame.

    Input columns: spot, strike, tte_years, iv, option_type
    Adds: delta, gamma, theta, vega, intrinsic, extrinsic
    """
    df = df.copy()

    def compute_row(r):
        g = greeks(r[spot_col], r[strike_col], r[tte_col], r[iv_col], r[option_type_col])
        return pd.Series(g)

    greeks_df = df.apply(compute_row, axis=1)
    df = pd.concat([df, greeks_df], axis=1)

    return df
