"""Pluggable data-connector framework.

Inspired by FinceptTerminal's 100+ connector pattern: every external data
source — broker WebSocket, free Yahoo quote API, NSE bhavcopy, local CSV —
is a single class that implements DataConnector. The signal engine, lab,
and backtester all consume the same Tick / Candle stream regardless of where
the data came from.
"""

from .base import (
    ConnectorCapability,
    ConnectorMetadata,
    DataConnector,
    register_connector,
    available_connectors,
    get_connector,
)
from .csv_replay import CsvReplayConnector
from .upstox_live import UpstoxLiveConnector
from .yahoo_quote import YahooQuoteConnector

__all__ = [
    "ConnectorCapability",
    "ConnectorMetadata",
    "DataConnector",
    "register_connector",
    "available_connectors",
    "get_connector",
    "CsvReplayConnector",
    "UpstoxLiveConnector",
    "YahooQuoteConnector",
]
