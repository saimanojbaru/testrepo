import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../brokers/upstox/upstox_client.dart';
import '../brokers/upstox/upstox_feed.dart';
import '../paper/market.dart';
import '../paper/paper_trader.dart';
import '../strategies/base.dart';
import '../strategies/runner.dart';

enum FeedMode { simulated, live }

class MarketController extends ChangeNotifier {
  MarketController()
      : _feed = MarketFeed(),
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
    _sub = _feed.stream.listen(_onTick);
    _feed.start();
  }

  FeedSource _feed;
  final PaperTrader trader;
  late final StrategyRunner runner;
  final Map<String, CandleAggregator> aggregators = {};
  final Map<String, List<double>> history = {};
  final Map<String, double> previousPrices = {};

  String selectedSymbol = 'NIFTY';
  bool autoExecuteSignals = true;
  FeedMode feedMode = FeedMode.simulated;

  StreamSubscription<Tick>? _sub;
  StreamSubscription<Signal>? _signalSub;

  FeedSource get feed => _feed;
  bool get isLiveFeed => feedMode == FeedMode.live;

  String? get feedError {
    final f = _feed;
    return f is UpstoxPollingFeed ? f.lastError : null;
  }

  Instrument get selectedInstrument =>
      Instrument.all.firstWhere((i) => i.symbol == selectedSymbol);

  CandleAggregator get selectedAggregator => aggregators[selectedSymbol]!;

  double price(String symbol) => _feed.priceOf(symbol);

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
    previousPrices[t.symbol] = _feed.priceOf(t.symbol);
    aggregators[t.symbol]!.add(t.price, t.ts);
    final h = history[t.symbol]!;
    h.add(t.price);
    if (h.length > 180) h.removeAt(0);
    if (t.symbol == selectedSymbol) {
      trader.onTick(t.price);
    }
    notifyListeners();
  }

  /// Swaps to a new feed source. Closes old, keeps aggregators + trader.
  void useFeed(FeedSource next, FeedMode mode) {
    _sub?.cancel();
    _feed.dispose();
    _feed = next;
    feedMode = mode;
    _sub = next.stream.listen(_onTick);
    next.start();
    notifyListeners();
  }

  void useLiveUpstox(UpstoxClient client) {
    useFeed(UpstoxPollingFeed(client: client), FeedMode.live);
  }

  void revertToSimulated() {
    useFeed(MarketFeed(), FeedMode.simulated);
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
    _feed.dispose();
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
