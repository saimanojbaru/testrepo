"""DataConnector abstract base + registry."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Flag, auto
from typing import AsyncIterator, Awaitable, Callable

from ..domain.signals import Tick


class ConnectorCapability(Flag):
    """What a connector can do — bitmask, OR-able."""

    NONE = 0
    LIVE_TICKS = auto()        # streams real-time Ticks
    HISTORICAL_BARS = auto()   # can return historical OHLC
    BROKER_ORDERS = auto()     # can place orders (live trading)
    REQUIRES_AUTH = auto()     # needs api key / token


@dataclass(slots=True, frozen=True)
class ConnectorMetadata:
    id: str
    name: str
    description: str
    capabilities: ConnectorCapability
    config_keys: tuple[str, ...] = ()


class DataConnector(ABC):
    """Single contract every data source implements.

    Subclasses must provide:
      - meta : ConnectorMetadata
      - run(on_tick) : streams ticks (only required if LIVE_TICKS capability)
      - history(...) : (only required if HISTORICAL_BARS capability)
    """

    meta: ConnectorMetadata

    @abstractmethod
    async def run(self, on_tick: Callable[[Tick], Awaitable[None] | None]) -> None:
        """Open a stream and forward ticks to on_tick. Must be cancellable."""

    async def history(
        self, symbol: str, lookback_minutes: int
    ) -> AsyncIterator[Tick]:
        """Yield historical ticks/bars. Default: empty."""
        if False:  # pragma: no cover  -- AsyncIterator placeholder
            yield


_REGISTRY: dict[str, type[DataConnector]] = {}


def register_connector(cls: type[DataConnector]) -> type[DataConnector]:
    """Class decorator to add a connector to the registry."""
    if not hasattr(cls, "meta"):
        raise TypeError(f"{cls.__name__} must declare a meta attribute")
    _REGISTRY[cls.meta.id] = cls
    return cls


def available_connectors() -> list[ConnectorMetadata]:
    return [c.meta for c in _REGISTRY.values()]


def get_connector(connector_id: str, **kwargs) -> DataConnector:
    cls = _REGISTRY.get(connector_id)
    if cls is None:
        raise ValueError(f"Unknown connector: {connector_id}")
    return cls(**kwargs)
