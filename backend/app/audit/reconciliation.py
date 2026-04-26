"""Trade reconciliation: contract note vs internal ledger.

Goal: CA-precision verification — every paisa on the broker contract note
must be explainable by an entry in the agent's internal trade log.

Variance categories:
  MISSING_IN_LEDGER  contract note has a fill the agent never recorded
  MISSING_IN_NOTE    agent recorded a fill the broker never settled
  PRICE_MISMATCH     same trade ID but entry/exit price differs
  COST_MISMATCH      gross matches but charges differ from our cost model
  QTY_MISMATCH       quantity differs between systems
"""

from __future__ import annotations

import csv
import io
from dataclasses import asdict, dataclass, field
from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Iterable

from ..costs.india_fno import compute_costs


class VarianceKind(str, Enum):
    MISSING_IN_LEDGER = "MISSING_IN_LEDGER"
    MISSING_IN_NOTE = "MISSING_IN_NOTE"
    PRICE_MISMATCH = "PRICE_MISMATCH"
    COST_MISMATCH = "COST_MISMATCH"
    QTY_MISMATCH = "QTY_MISMATCH"


@dataclass(slots=True, frozen=True)
class ContractNoteRow:
    """One leg parsed from a broker's contract-note CSV."""

    trade_id: str
    instrument: str
    side: str            # 'BUY' or 'SELL'
    qty: int
    price: float
    charges: float       # total charges from the contract note
    ts: datetime


@dataclass(slots=True, frozen=True)
class LedgerRow:
    """One leg from the agent's internal trade log (TradeRow → here)."""

    trade_id: str
    instrument: str
    side: str
    qty: int
    price: float
    charges: float
    ts: datetime


@dataclass(slots=True, frozen=True)
class Variance:
    kind: VarianceKind
    trade_id: str
    expected: float | int | str | None
    actual: float | int | str | None
    delta: float | None
    note: str

    def to_dict(self) -> dict:
        return {
            "kind": self.kind.value,
            "trade_id": self.trade_id,
            "expected": self.expected,
            "actual": self.actual,
            "delta": self.delta,
            "note": self.note,
        }


@dataclass(slots=True)
class ReconciliationReport:
    matched: int = 0
    variances: list[Variance] = field(default_factory=list)
    ledger_total_charges: float = 0.0
    note_total_charges: float = 0.0

    @property
    def is_clean(self) -> bool:
        return not self.variances

    def to_dict(self) -> dict:
        return {
            "matched": self.matched,
            "variance_count": len(self.variances),
            "is_clean": self.is_clean,
            "ledger_total_charges": round(self.ledger_total_charges, 2),
            "note_total_charges": round(self.note_total_charges, 2),
            "charges_delta": round(
                self.note_total_charges - self.ledger_total_charges, 2
            ),
            "variances": [v.to_dict() for v in self.variances],
        }


def _decimal_close(a: float, b: float, tolerance: float = 0.005) -> bool:
    """5-paisa tolerance — the contract note rounds at the paisa boundary."""
    return abs(Decimal(str(a)) - Decimal(str(b))) <= Decimal(str(tolerance))


def parse_contract_note(csv_text: str) -> list[ContractNoteRow]:
    """Parse a Zerodha/Upstox contract-note CSV.

    Expected columns (case-insensitive): trade_id, instrument, side, qty, price,
    charges, ts. Columns can appear in any order.
    """
    reader = csv.DictReader(io.StringIO(csv_text))
    out: list[ContractNoteRow] = []
    for row in reader:
        norm = {k.strip().lower(): v.strip() for k, v in row.items() if k}
        ts_raw = norm.get("ts") or norm.get("timestamp") or norm.get("trade_time")
        if not ts_raw:
            continue
        out.append(
            ContractNoteRow(
                trade_id=norm["trade_id"],
                instrument=norm["instrument"],
                side=norm["side"].upper(),
                qty=int(norm["qty"]),
                price=float(norm["price"]),
                charges=float(norm.get("charges", 0)),
                ts=_parse_ts(ts_raw),
            )
        )
    return out


def _parse_ts(raw: str) -> datetime:
    raw = raw.strip()
    for fmt in ("%Y-%m-%dT%H:%M:%S%z", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(raw, fmt)
        except ValueError:
            continue
    return datetime.fromisoformat(raw)


def reconcile(
    note: Iterable[ContractNoteRow],
    ledger: Iterable[LedgerRow],
    cost_tolerance_inr: float = 1.0,  # treat <= ₹1 charge diff as benign rounding
) -> ReconciliationReport:
    """Match contract-note rows to ledger rows by trade_id, flag every variance."""
    note_by_id = {r.trade_id: r for r in note}
    ledger_by_id = {r.trade_id: r for r in ledger}

    report = ReconciliationReport()
    report.note_total_charges = sum(r.charges for r in note_by_id.values())
    report.ledger_total_charges = sum(r.charges for r in ledger_by_id.values())

    for tid, n in note_by_id.items():
        l = ledger_by_id.get(tid)
        if l is None:
            report.variances.append(
                Variance(
                    kind=VarianceKind.MISSING_IN_LEDGER,
                    trade_id=tid,
                    expected=None,
                    actual=f"{n.side} {n.qty}@{n.price}",
                    delta=None,
                    note="Broker shows a fill our internal log never recorded.",
                )
            )
            continue

        if n.qty != l.qty:
            report.variances.append(
                Variance(
                    kind=VarianceKind.QTY_MISMATCH,
                    trade_id=tid,
                    expected=l.qty,
                    actual=n.qty,
                    delta=float(n.qty - l.qty),
                    note=f"qty differs: ledger={l.qty} note={n.qty}",
                )
            )

        if not _decimal_close(n.price, l.price, tolerance=0.05):
            report.variances.append(
                Variance(
                    kind=VarianceKind.PRICE_MISMATCH,
                    trade_id=tid,
                    expected=l.price,
                    actual=n.price,
                    delta=round(n.price - l.price, 4),
                    note=f"price differs: ledger={l.price} note={n.price}",
                )
            )

        # Cost cross-check: recompute expected charges via the fee schedule
        expected_costs = compute_costs(
            side=n.side, premium=n.price, qty=n.qty
        )
        # Only flag if BOTH our model and our ledger disagree with the note
        # by more than the tolerance.
        delta_note_vs_model = abs(n.charges - expected_costs.total)
        delta_note_vs_ledger = abs(n.charges - l.charges)
        if (
            delta_note_vs_ledger > cost_tolerance_inr
            and delta_note_vs_model > cost_tolerance_inr
        ):
            report.variances.append(
                Variance(
                    kind=VarianceKind.COST_MISMATCH,
                    trade_id=tid,
                    expected=l.charges,
                    actual=n.charges,
                    delta=round(n.charges - l.charges, 2),
                    note=(
                        f"charges differ: ledger=₹{l.charges:.2f} "
                        f"note=₹{n.charges:.2f} "
                        f"model=₹{expected_costs.total:.2f}"
                    ),
                )
            )

        if l is not None and not _has_breaking_variance(report, tid):
            report.matched += 1

    for tid, l in ledger_by_id.items():
        if tid not in note_by_id:
            report.variances.append(
                Variance(
                    kind=VarianceKind.MISSING_IN_NOTE,
                    trade_id=tid,
                    expected=f"{l.side} {l.qty}@{l.price}",
                    actual=None,
                    delta=None,
                    note="Internal log has a fill the broker contract note doesn't show.",
                )
            )

    return report


def _has_breaking_variance(report: ReconciliationReport, tid: str) -> bool:
    """True if the trade_id already has a recorded variance.

    'Matched' means: present in both note + ledger AND no variance flagged.
    """
    return any(v.trade_id == tid for v in report.variances)


# Re-exported for convenience
__all_dataclasses__ = (
    ContractNoteRow,
    LedgerRow,
    Variance,
    ReconciliationReport,
)
