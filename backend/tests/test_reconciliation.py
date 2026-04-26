"""Reconciliation engine tests."""

from __future__ import annotations

from datetime import datetime, timezone

from app.audit.reconciliation import (
    ContractNoteRow,
    LedgerRow,
    VarianceKind,
    parse_contract_note,
    reconcile,
)


def _ts() -> datetime:
    return datetime(2025, 1, 2, 10, 0, tzinfo=timezone.utc)


def test_clean_match_produces_no_variances():
    note = [
        ContractNoteRow(
            trade_id="T1",
            instrument="NIFTY24500CE",
            side="SELL",
            qty=75,
            price=120.0,
            charges=24.5,
            ts=_ts(),
        )
    ]
    ledger = [
        LedgerRow(
            trade_id="T1",
            instrument="NIFTY24500CE",
            side="SELL",
            qty=75,
            price=120.0,
            charges=24.5,
            ts=_ts(),
        )
    ]
    report = reconcile(note, ledger)
    assert report.is_clean
    assert report.matched == 1


def test_missing_in_ledger_flagged():
    note = [
        ContractNoteRow(
            trade_id="T9",
            instrument="NIFTY",
            side="BUY",
            qty=75,
            price=100.0,
            charges=10.0,
            ts=_ts(),
        )
    ]
    report = reconcile(note, [])
    assert any(
        v.kind is VarianceKind.MISSING_IN_LEDGER for v in report.variances
    )


def test_price_mismatch_flagged_outside_tolerance():
    note = [
        ContractNoteRow(
            trade_id="T2",
            instrument="X",
            side="BUY",
            qty=10,
            price=100.0,
            charges=5.0,
            ts=_ts(),
        )
    ]
    ledger = [
        LedgerRow(
            trade_id="T2",
            instrument="X",
            side="BUY",
            qty=10,
            price=101.0,  # +₹1 vs note
            charges=5.0,
            ts=_ts(),
        )
    ]
    report = reconcile(note, ledger)
    assert any(
        v.kind is VarianceKind.PRICE_MISMATCH for v in report.variances
    )


def test_qty_mismatch_flagged():
    note = [
        ContractNoteRow(
            trade_id="T3",
            instrument="X",
            side="BUY",
            qty=75,
            price=100.0,
            charges=5.0,
            ts=_ts(),
        )
    ]
    ledger = [
        LedgerRow(
            trade_id="T3",
            instrument="X",
            side="BUY",
            qty=50,
            price=100.0,
            charges=5.0,
            ts=_ts(),
        )
    ]
    report = reconcile(note, ledger)
    assert any(
        v.kind is VarianceKind.QTY_MISMATCH for v in report.variances
    )


def test_parse_contract_note_handles_csv():
    raw = (
        "trade_id,instrument,side,qty,price,charges,ts\n"
        "T1,NIFTY,SELL,75,120.00,24.50,2025-01-02T10:00:00\n"
        "T2,NIFTY,BUY,75,100.00,12.00,2025-01-02T10:30:00\n"
    )
    rows = parse_contract_note(raw)
    assert len(rows) == 2
    assert rows[0].trade_id == "T1"
    assert rows[1].qty == 75
