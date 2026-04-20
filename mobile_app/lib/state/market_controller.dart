import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../paper/market.dart';
import '../paper/paper_trader.dart';
import '../strategies/base.dart';
import '../strategies/runner.dart';

class MarketController extends ChangeNotifier {
  MarketController()
      : feed = MarketFeed(),
        trader = PaperTrader() {
    for (final i in Instrument.all) {
      aggregators[i.symbol] = CandleAggregator();
      history[i.symbol] = <double>[];
      previousPrices[i.symbol] = i.basePrice;
    }
    runner = StrategyRunner(aggregator: aggregators[selectedSymbol]!);
    runner.setSymbol(selectedSymbol);
    runner.attach();
    _signalSub = runner.onSignal.listen(_onSignal);
    _sub = feed.stream.listen(_onTick);
    feed.start();
  }

  final MarketFeed feed;
  final PaperTrader trader;
  late final StrategyRunner runner;
  final Map<String, CandleAggregator> aggregators = {};
  final Map<String, List<double>> history = {};
  final Map<String, double> previousPrices = {};

  String selectedSymbol = 'NIFTY';
  bool autoExecuteSignals = true;
  StreamSubscription<Tick>? _sub;
  StreamSubscription<Signal>? _signalSub;

  Instrument get selectedInstrument =>
      Instrument.all.firstWhere((i) => i.symbol == selectedSymbol);

  CandleAggregator get selectedAggregator => aggregators[selectedSymbol]!;

  double price(String symbol) => feed.priceOf(symbol);

  void selectSymbol(String symbol) {
    if (symbol == selectedSymbol) return;
    selectedSymbol = symbol;
    runner.setSymbol(symbol);
    runner.setSource(aggregators[symbol]!);
    notifyListeners();
  }

  void _onSignal(Signal sig) {
    if (autoExecuteSignals && sig.symbol == selectedSymbol) {
      executeSignal(sig);
    }
  }

  void setAutoExecute(bool v) {
    autoExecuteSignals = v;
    notifyListeners();
  }

  void _onTick(Tick t) {
    previousPrices[t.symbol] = feed.priceOf(t.symbol);
    aggregators[t.symbol]!.add(t.price, t.ts);
    final h = history[t.symbol]!;
    h.add(t.price);
    if (h.length > 180) h.removeAt(0);
    if (t.symbol == selectedSymbol) {
      trader.onTick(t.price);
    }
    notifyListeners();
  }

  void executeSignal(Signal sig) {
    trader.openTrade(
      side: sig.side == SignalSide.long ? TradeSide.long : TradeSide.short,
      price: sig.price,
      lots: 1,
      sl: sig.sl,
      tp: sig.tp,
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    _signalSub?.cancel();
    runner.dispose();
    for (final a in aggregators.values) {
      a.dispose();
    }
    feed.dispose();
    trader.dispose();
    super.dispose();
  }
}

final marketControllerProvider =
    ChangeNotifierProvider<MarketController>((ref) {
  final ctrl = MarketController();
  ref.onDispose(ctrl.dispose);
  return ctrl;
});
