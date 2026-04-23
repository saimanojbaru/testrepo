import 'dart:async';

import '../../paper/market.dart';
import 'upstox_client.dart';
import 'upstox_config.dart';

/// Polls Upstox REST for LTP at a fixed cadence and emits Ticks.
/// Drops in as a replacement for [MarketFeed] anywhere FeedSource is expected.
class UpstoxPollingFeed implements FeedSource {
  UpstoxPollingFeed({
    required this.client,
    this.pollMs = 1000,
    List<String>? symbols,
  }) : _symbols = symbols ?? Instrument.all.map((i) => i.symbol).toList();

  final UpstoxClient client;
  final int pollMs;
  final List<String> _symbols;

  Timer? _timer;
  final _ctrl = StreamController<Tick>.broadcast();
  final Map<String, double> _prices = {};
  int _consecutiveErrors = 0;
  bool _inFlight = false;

  /// Last known error (null = healthy). Consumers can watch this to flag
  /// "connection lost" banners.
  String? lastError;

  int get consecutiveErrors => _consecutiveErrors;

  @override
  Stream<Tick> get stream => _ctrl.stream;

  @override
  double priceOf(String symbol) =>
      _prices[symbol] ??
      Instrument.all.firstWhere((i) => i.symbol == symbol).basePrice;

  @override
  Stream<Tick> streamFor(String symbol) =>
      _ctrl.stream.where((t) => t.symbol == symbol);

  @override
  void start() {
    // Kick one immediate poll so the UI doesn't wait for the first tick.
    _tick();
    _timer ??= Timer.periodic(Duration(milliseconds: pollMs), (_) => _tick());
  }

  Future<void> _tick() async {
    if (_inFlight) return;
    _inFlight = true;
    try {
      final keys = _symbols
          .map(UpstoxConfig.keyFor)
          .whereType<String>()
          .toList();
      final quotes = await client.fetchLtp(keys);
      final now = DateTime.now();
      for (final q in quotes) {
        final sym = UpstoxConfig.symbolOf[q.instrumentKey];
        if (sym == null) continue;
        _prices[sym] = q.ltp;
        _ctrl.add(Tick(symbol: sym, price: q.ltp, ts: now));
      }
      _consecutiveErrors = 0;
      lastError = null;
    } catch (e) {
      _consecutiveErrors += 1;
      lastError = e.toString();
    } finally {
      _inFlight = false;
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _ctrl.close();
  }
}
