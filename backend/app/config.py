"""Application configuration loaded from environment variables.

All side-effectful integrations (Upstox, Telegram, Claude) are optional — the
backend boots with the values unset and falls back to offline behaviour so
local development and tests never require external credentials.
"""

from __future__ import annotations

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # Storage
    database_url: str = Field(
        default="sqlite+aiosqlite:///./scalping.db",
        description="SQLAlchemy URL. Swap to postgres://... in prod.",
    )
    redis_url: str | None = Field(
        default=None,
        description="Optional Redis URL for real-time state. In-memory fallback used if unset.",
    )

    # Upstox
    upstox_access_token: str | None = None
    upstox_symbols: list[str] = Field(
        default_factory=lambda: ["NSE_INDEX|Nifty 50", "NSE_INDEX|Nifty Bank"]
    )

    # Telegram
    telegram_bot_token: str | None = None
    telegram_chat_id: str | None = None

    # Claude
    anthropic_api_key: str | None = None
    claude_model: str = "claude-opus-4-7"

    # Risk defaults (tight, single-instrument scalping)
    risk_max_trades_per_hour: int = 6
    risk_consecutive_loss_halt: int = 3
    risk_daily_loss_limit: float = 2000.0
    risk_cooldown_seconds: int = 180

    # Signal engine defaults
    signal_momentum_threshold: float = 0.003  # 0.3%
    signal_stop_loss_pct: float = 0.004       # 0.4%
    signal_target_pct: float = 0.008          # 0.8%
    signal_time_exit_seconds: int = 240       # 4 minutes
    signal_breakout_lookback: int = 20        # bars

    # Backtesting
    backtest_brokerage_per_trade: float = 20.0
    backtest_slippage_bps: float = 2.0
    backtest_execution_delay_bars: int = 1


settings = Settings()
