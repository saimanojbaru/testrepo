"""Application configuration."""
from pathlib import Path
from pydantic_settings import BaseSettings


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
DB_PATH = BASE_DIR.parent / "iip.db"


class Settings(BaseSettings):
    app_name: str = "Insurance Intelligence Pro API"
    app_version: str = "1.0.0"
    sec_user_agent: str = "InsuranceIntelligencePro research@example.com"
    sec_base_url: str = "https://data.sec.gov"
    sec_company_tickers_url: str = "https://www.sec.gov/files/company_tickers.json"
    cache_ttl_seconds: int = 60 * 60 * 6  # 6h memory cache
    long_cache_ttl_seconds: int = 60 * 60 * 24 * 7  # 7d disk cache
    request_timeout: float = 12.0
    max_concurrent_requests: int = 6
    enable_cors: bool = True

    class Config:
        env_prefix = "IIP_"


settings = Settings()
