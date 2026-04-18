"""
Unit tests for data ingestion modules.
Tests URL construction, caching, and parsing (no live network calls).
"""

from datetime import datetime
from pathlib import Path

from data.ingest.nse_bhavcopy import NSEBhavcopyIngester
from data.ingest.upstox_historical import UpstoxHistoricalLoader


def test_nse_url_construction():
    """NSE bhavcopy URL follows expected pattern."""
    ingester = NSEBhavcopyIngester(cache_dir="/tmp/test_cache")
    date = datetime(2024, 1, 15)
    url = ingester._build_url(date, "fo")

    assert "2024" in url
    assert "JAN" in url
    assert "fo15JAN2024" in url
    assert url.endswith(".csv.zip")

    print(f"  NSE URL: {url}")


def test_nse_cache_path():
    """Cache path uses YYYYMMDD format."""
    ingester = NSEBhavcopyIngester(cache_dir="/tmp/test_cache")
    date = datetime(2024, 1, 15)
    path = ingester._cache_path(date, "fo")

    assert path.name == "fo_20240115.csv"
    print(f"  NSE cache path: {path}")


def test_upstox_instrument_keys():
    """Upstox maps symbols to correct instrument keys."""
    loader = UpstoxHistoricalLoader()

    assert loader.INSTRUMENT_KEYS["NIFTY"] == "NSE_INDEX|Nifty 50"
    assert loader.INSTRUMENT_KEYS["BANKNIFTY"] == "NSE_INDEX|Nifty Bank"
    assert loader.INSTRUMENT_KEYS["INDIAVIX"] == "NSE_INDEX|India VIX"

    print(f"  Upstox keys: {loader.INSTRUMENT_KEYS}")


def test_ingesters_create_cache_dir():
    """Ingesters create cache dir on init."""
    import tempfile
    with tempfile.TemporaryDirectory() as tmpdir:
        cache = Path(tmpdir) / "nse"
        NSEBhavcopyIngester(cache_dir=str(cache))
        assert cache.exists()

        cache2 = Path(tmpdir) / "upstox"
        UpstoxHistoricalLoader(cache_dir=str(cache2))
        assert cache2.exists()

    print("  ✓ Cache dirs created correctly")


if __name__ == "__main__":
    test_nse_url_construction()
    test_nse_cache_path()
    test_upstox_instrument_keys()
    test_ingesters_create_cache_dir()
    print("\n✓ All ingestion tests passed!")
