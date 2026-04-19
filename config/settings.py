from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", ".env.example"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    scalp_env: str = "dev"
    scalp_log_level: str = "INFO"

    scalp_db_url: str = "postgresql+psycopg://scalp:scalp@localhost:5432/scalp"

    upstox_api_key: str = ""
    upstox_api_secret: str = ""
    upstox_redirect_uri: str = "http://localhost:8000/upstox/callback"
    upstox_access_token: str = ""

    kite_api_key: str = ""
    kite_api_secret: str = ""
    kite_access_token: str = ""

    quandl_api_key: str = ""
    nse_bhavcopy_cache_dir: Path = Path("./data/cache/bhavcopy")

    telegram_bot_token: str = ""
    telegram_chat_id: str = ""

    scalp_capital: float = 100_000.0
    scalp_max_daily_loss: float = 2_000.0
    scalp_kelly_fraction: float = Field(default=0.25, ge=0.0, le=1.0)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
