import 'package:flutter/foundation.dart';

enum TradeSide { long, short }

class PaperPosition {
  PaperPosition({
    required this.id,
    required this.side,
    required this.entry,
    required this.lots,
    this.slPoints,
    this.tpPoints,
    required this.openedAt,
  });

  final int id;
  final TradeSide side;
  final double entry;
  final int lots;
  final double? slPoints;
  final double? tpPoints;
  final DateTime openedAt;

  double mtm(double px) {
    final dir = side == TradeSide.long ? 1 : -1;
    return (px - entry) * dir * lots * _lotSize;
  }

  bool hitStop(double px) {
    if (slPoints == null) return false;
    final move = side == TradeSide.long ? entry - px : px - entry;
    return move >= slPoints!;
  }

  bool hitTarget(double px) {
    if (tpPoints == null) return false;
    final move = side == TradeSide.long ? px - entry : entry - px;
    return move >= tpPoints!;
  }
}

class ClosedTrade {
  ClosedTrade({
    required this.side,
    required this.entry,
    required this.exit,
    required this.lots,
    required this.grossPnl,
    required this.costs,
    required this.reason,
    required this.closedAt,
  });

  final TradeSide side;
  final double entry;
  final double exit;
  final int lots;
  final double grossPnl;
  final double costs;
  final String reason;
  final DateTime closedAt;

  double get netPnl => grossPnl - costs;
}

const _lotSize = 25;
const _roundTripCost = 40.0;

class PaperTrader extends ChangeNotifier {
  final List<PaperPosition> _open = [];
  final List<ClosedTrade> _closed = [];
  int _nextId = 1;

  List<PaperPosition> get open => List.unmodifiable(_open);
  List<ClosedTrade> get closed => List.unmodifiable(_closed);

  double realizedPnl() =>
      _closed.fold<double>(0, (sum, t) => sum + t.netPnl);

  double unrealizedPnl(double px) =>
      _open.fold<double>(0, (sum, p) => sum + p.mtm(px));

  double sessionPnl(double px) => realizedPnl() + unrealizedPnl(px);

  int get openCount => _open.length;
  int get tradesToday => _closed.length;

  void openTrade({
    required TradeSide side,
    required double price,
    required int lots,
    double? sl,
    double? tp,
  }) {
    _open.add(PaperPosition(
      id: _nextId++,
      side: side,
      entry: price,
      lots: lots,
      slPoints: sl,
      tpPoints: tp,
      openedAt: DateTime.now(),
    ));
    notifyListeners();
  }

  void closeTrade(int id, double price, {String reason = 'manual'}) {
    final idx = _open.indexWhere((p) => p.id == id);
    if (idx < 0) return;
    final p = _open.removeAt(idx);
    final gross = p.mtm(price);
    _closed.insert(
      0,
      ClosedTrade(
        side: p.side,
        entry: p.entry,
        exit: price,
        lots: p.lots,
        grossPnl: gross,
        costs: _roundTripCost * p.lots,
        reason: reason,
        closedAt: DateTime.now(),
      ),
    );
    notifyListeners();
  }

  void onTick(double px) {
    final toClose = <(int, String)>[];
    for (final p in _open) {
      if (p.hitStop(px)) {
        toClose.add((p.id, 'SL'));
      } else if (p.hitTarget(px)) {
        toClose.add((p.id, 'TP'));
      }
    }
    if (toClose.isEmpty) {
      notifyListeners();
      return;
    }
    for (final (id, reason) in toClose) {
      closeTrade(id, px, reason: reason);
    }
  }
}
