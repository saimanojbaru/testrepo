"""
Upstox live broker adapter.

Requires valid Upstox access token. OAuth flow:
1. Get API key/secret from developer.upstox.com
2. Generate auth URL → user logs in → redirected with code
3. Exchange code for access token (valid ~24 hrs)
4. Use token for order placement, positions, quotes, WebSocket
"""

import logging
import requests
from typing import List, Optional
from datetime import datetime

from broker.base import Broker, Order, Position, Quote


logger = logging.getLogger(__name__)

UPSTOX_BASE_URL = "https://api.upstox.com/v2"


class UpstoxBroker(Broker):
    """
    Live Upstox broker adapter.
    Implements Broker interface via Upstox v2 REST API.
    """

    def __init__(self, access_token: str):
        self.access_token = access_token
        self.headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json",
        }

    def place_order(self, order: Order) -> Order:
        """Place an order via Upstox REST API."""
        url = f"{UPSTOX_BASE_URL}/order/place"
        payload = {
            "quantity": order.quantity,
            "product": order.product.upper(),  # MIS/CNC/NRML
            "validity": "DAY",
            "price": order.price or 0,
            "tag": "scalping_agent",
            "instrument_token": order.instrument_key,
            "order_type": order.order_type.upper(),
            "transaction_type": order.side.upper(),
            "disclosed_quantity": 0,
            "trigger_price": order.trigger_price or 0,
            "is_amo": False,
        }

        try:
            resp = requests.post(url, json=payload, headers=self.headers, timeout=10)
            data = resp.json()

            if data.get("status") == "success":
                order.order_id = data["data"]["order_id"]
                order.status = "pending"
                order.timestamp = datetime.now()
                logger.info(f"[Upstox] Order placed: {order.order_id}")
            else:
                logger.error(f"[Upstox] Place order failed: {data}")
                order.status = "rejected"

        except Exception as e:
            logger.error(f"[Upstox] Exception placing order: {e}")
            order.status = "error"

        return order

    def cancel_order(self, order_id: str) -> bool:
        """Cancel a pending order."""
        url = f"{UPSTOX_BASE_URL}/order/cancel"
        try:
            resp = requests.delete(
                url, params={"order_id": order_id}, headers=self.headers, timeout=10
            )
            return resp.json().get("status") == "success"
        except Exception as e:
            logger.error(f"[Upstox] Cancel failed: {e}")
            return False

    def modify_order(self, order_id: str, **kwargs) -> Order:
        """Modify a pending order."""
        url = f"{UPSTOX_BASE_URL}/order/modify"
        payload = {"order_id": order_id, **kwargs}
        resp = requests.put(url, json=payload, headers=self.headers, timeout=10)
        data = resp.json()
        # Return a stub Order; callers should fetch full status separately
        return Order(
            order_id=order_id,
            instrument_key="",
            symbol="",
            side="buy",
            quantity=kwargs.get("quantity", 0),
            order_type="limit",
            status=data.get("status", "unknown"),
        )

    def get_positions(self) -> List[Position]:
        """Fetch current open positions."""
        url = f"{UPSTOX_BASE_URL}/portfolio/short-term-positions"
        try:
            resp = requests.get(url, headers=self.headers, timeout=10)
            data = resp.json()

            if data.get("status") != "success":
                return []

            positions = []
            for p in data.get("data", []):
                positions.append(
                    Position(
                        instrument_key=p.get("instrument_token", ""),
                        symbol=p.get("tradingsymbol", ""),
                        quantity=int(p.get("quantity", 0)),
                        average_price=float(p.get("average_price", 0)),
                        last_price=float(p.get("last_price", 0)),
                        pnl=float(p.get("pnl", 0)),
                        realized_pnl=float(p.get("realised", 0)),
                        unrealized_pnl=float(p.get("unrealised", 0)),
                    )
                )
            return positions

        except Exception as e:
            logger.error(f"[Upstox] get_positions failed: {e}")
            return []

    def get_quote(self, instrument_key: str) -> Quote:
        """Fetch live quote."""
        url = f"{UPSTOX_BASE_URL}/market-quote/ltp"
        try:
            resp = requests.get(
                url,
                params={"instrument_key": instrument_key},
                headers=self.headers,
                timeout=10,
            )
            data = resp.json()
            quote_data = data.get("data", {}).get(instrument_key, {})
            return Quote(
                instrument_key=instrument_key,
                ltp=float(quote_data.get("last_price", 0)),
                timestamp=datetime.now(),
            )
        except Exception as e:
            logger.error(f"[Upstox] get_quote failed: {e}")
            return Quote(instrument_key=instrument_key, ltp=0)

    def get_order_status(self, order_id: str) -> Order:
        """Fetch order status."""
        url = f"{UPSTOX_BASE_URL}/order/details"
        try:
            resp = requests.get(
                url,
                params={"order_id": order_id},
                headers=self.headers,
                timeout=10,
            )
            data = resp.json().get("data", {})
            return Order(
                order_id=order_id,
                instrument_key=data.get("instrument_token", ""),
                symbol=data.get("tradingsymbol", ""),
                side=data.get("transaction_type", "buy").lower(),
                quantity=int(data.get("quantity", 0)),
                order_type=data.get("order_type", "market").lower(),
                price=float(data.get("price", 0)),
                status=data.get("status", "unknown").lower(),
                filled_quantity=int(data.get("filled_quantity", 0)),
                filled_price=float(data.get("average_price", 0)) or None,
            )
        except Exception as e:
            logger.error(f"[Upstox] get_order_status failed: {e}")
            return Order(order_id=order_id, instrument_key="", symbol="",
                         side="buy", quantity=0, order_type="market", status="error")

    def square_off_all(self) -> bool:
        """Emergency square-off all positions."""
        positions = self.get_positions()
        success = True
        for pos in positions:
            if pos.quantity == 0:
                continue
            exit_side = "sell" if pos.quantity > 0 else "buy"
            order = Order(
                order_id="",
                instrument_key=pos.instrument_key,
                symbol=pos.symbol,
                side=exit_side,
                quantity=abs(pos.quantity),
                order_type="market",
            )
            result = self.place_order(order)
            if result.status not in ("pending", "filled"):
                success = False
                logger.error(f"[Upstox] Square-off failed for {pos.symbol}")
        return success
