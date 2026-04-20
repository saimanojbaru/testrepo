import 'dart:async';
import 'dart:collection';

import 'package:flutter/foundation.dart';

import '../paper/market.dart';
import 'base.dart';
import 'concrete.dart';

class StrategyRunner extends ChangeNotifier {
  StrategyRunner({required CandleAggregator aggregator})
      : _aggregator = aggregator;

  CandleAggregator _aggregator;
  final Queue<Signal> _feed = Queue<Signal>();
  static const _maxFeed = 40;
  StreamSubscription<List<Candle>>? _sub;
  String _symbol = 'NIFTY';
  final _newSignalCtrl = StreamController<Signal>.broadcast();

  List<Signal> get feed => _feed.toList(growable: false);
  List<Strategy> get strategies => StrategyRegistry.all;
  Stream<Signal> get onSignal => _newSignalCtrl.stream;

  void setSymbol(String symbol) {
    _symbol = symbol;
  }

  void setSource(CandleAggregator aggregator) {
    if (identical(aggregator, _aggregator)) return;
    _sub?.cancel();
    _aggregator = aggregator;
    _sub = _aggregator.stream.listen(_onCandles);
  }

  void attach() {
    _sub = _aggregator.stream.listen(_onCandles);
  }

  void _onCandles(List<Candle> candles) {
    if (candles.length < 3) return;
    for (final s in StrategyRegistry.all) {
      if (!s.enabled) continue;
      final sig = s.onCandles(_symbol, candles);
      if (sig != null) {
        s.recordSignal();
        _feed.addFirst(sig);
        while (_feed.length > _maxFeed) {
          _feed.removeLast();
        }
        _newSignalCtrl.add(sig);
      }
    }
    notifyListeners();
  }

  void clearFeed() {
    _feed.clear();
    notifyListeners();
  }

  int get activeCount =>
      StrategyRegistry.all.where((s) => s.enabled).length;

  @override
  void dispose() {
    _sub?.cancel();
    _newSignalCtrl.close();
    super.dispose();
  }
}
