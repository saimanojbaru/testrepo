from pydantic_settings import BaseSettings
from typing import Optional
from pathlib import Path

class Settings(BaseSettings):
    # Upstox API
    upstox_api_key: str
    upstox_secret_key: str

    # Zerodha Kite Connect
    kite_api_key: Optional[str] = None
    kite_access_token: Optional[str] = None

    # Data sources
    quandl_api_key: Optional[str] = None

    # Database
    database_url: str = "postgresql://scalping_user:scalping_password@localhost:5432/scalping_agent"

    # Monitoring
    telegram_bot_token: Optional[str] = None
    telegram_chat_id: Optional[str] = None

    # Broker settings
    primary_broker: str = "upstox"
    trading_capital: float = 100000
    max_loss_per_day: float = 2000
    max_loss_percentage: float = 0.02

    # Paper vs Live
    paper_trading: bool = True
    simulated_slippage_bps: float = 5

    # Feature engineering
    risk_free_rate: float = 0.06
    min_sharpe_for_strategy: float = 1.5

    # Backtest settings
    backtest_start_date: str = "2020-01-01"
    backtest_end_date: str = "2024-12-31"
    walk_forward_train_months: int = 6
    walk_forward_test_months: int = 1

    # Logging
    log_level: str = "INFO"
    log_file: str = "logs/agent.log"

    class Config:
        env_file = ".env"
        case_sensitive = False

settings = Settings()
