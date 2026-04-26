"""List + describe registered data connectors."""

from __future__ import annotations

from fastapi import APIRouter

from ..connectors import available_connectors

router = APIRouter()


@router.get("/connectors")
async def list_connectors() -> dict:
    return {
        "connectors": [
            {
                "id": m.id,
                "name": m.name,
                "description": m.description,
                "capabilities": [
                    cap.name for cap in m.capabilities.__class__
                    if m.capabilities & cap and cap.value > 0
                ],
                "config_keys": list(m.config_keys),
            }
            for m in available_connectors()
        ]
    }
