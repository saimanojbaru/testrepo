import 'dart:async';
import 'dart:math';

class Instrument {
  const Instrument({
    required this.symbol,
    required this.label,
    required this.basePrice,
    required this.lotSize,
    required this.volatility,
  });

  final String symbol;
  final String label;
  final double basePrice;
  final int lotSize;
  final double volatility;

  static const nifty = Instrument(
    symbol: 'NIFTY',
    label: 'Nifty 50',
    basePrice: 24500,
    lotSize: 25,
    volatility: 2.5,
  );

  static const bankNifty = Instrument(
    symbol: 'BANKNIFTY',
    label: 'Bank Nifty',
    basePrice: 52000,
    lotSize: 15,
    volatility: 6.0,
  );

  static const finNifty = Instrument(
    symbol: 'FINNIFTY',
    label: 'Fin Nifty',
    basePrice: 23400,
    lotSize: 40,
    volatility: 2.2,
  );

  static const sensex = Instrument(
    symbol: 'SENSEX',
    label: 'Sensex',
    basePrice: 80500,
    lotSize: 10,
    volatility: 8.0,
  );

  static const List<Instrument> all = [nifty, bankNifty, finNifty, sensex];
}

class Tick {
  const Tick({required this.symbol, required this.price, required this.ts});
  final String symbol;
  final double price;
  final DateTime ts;
}

abstract class FeedSource {
  Stream<Tick> get stream;
  double priceOf(String symbol);
  Stream<Tick> streamFor(String symbol) =>
      stream.where((t) => t.symbol == symbol);
  void start();
  void dispose();
}

class MarketFeed implements FeedSource {
  MarketFeed({this.tickMs = 300, int? seed})
      : _rng = Random(seed),
        _prices = {for (final i in Instrument.all) i.symbol: i.basePrice};

  final int tickMs;
  final Random _rng;
  final Map<String, double> _prices;
  Timer? _timer;
  final _ctrl = StreamController<Tick>.broadcast();

  @override
  Stream<Tick> get stream => _ctrl.stream;

  @override
  double priceOf(String symbol) => _prices[symbol] ?? 0;

  @override
  Stream<Tick> streamFor(String symbol) =>
      _ctrl.stream.where((t) => t.symbol == symbol);

  @override
  void start() {
    _timer ??= Timer.periodic(Duration(milliseconds: tickMs), (_) {
      final now = DateTime.now();
      for (final inst in Instrument.all) {
        final u1 = 1 - _rng.nextDouble();
        final u2 = 1 - _rng.nextDouble();
        final z = sqrt(-2 * log(u1)) * cos(2 * pi * u2);
        final prev = _prices[inst.symbol]!;
        final drift = -(prev - inst.basePrice) * 0.0005;
        final next = (prev + drift + z * inst.volatility).clamp(
          inst.basePrice * 0.9,
          inst.basePrice * 1.1,
        );
        _prices[inst.symbol] = next;
        _ctrl.add(Tick(symbol: inst.symbol, price: next, ts: now));
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _ctrl.close();
  }
}

class Candle {
  Candle({
    required this.ts,
    required this.open,
    required this.high,
    required this.low,
    required this.close,
  });

  final DateTime ts;
  final double open;
  double high;
  double low;
  double close;

  bool get bullish => close >= open;
}

class CandleAggregator {
  CandleAggregator({this.bucketSeconds = 5, this.maxCandles = 80});

  final int bucketSeconds;
  final int maxCandles;
  final List<Candle> _candles = [];
  Candle? _current;
  final _ctrl = StreamController<List<Candle>>.broadcast();

  Stream<List<Candle>> get stream => _ctrl.stream;
  List<Candle> get candles => List.unmodifiable(_candles);

  DateTime _bucket(DateTime ts) {
    final s = ts.second - (ts.second % bucketSeconds);
    return DateTime(ts.year, ts.month, ts.day, ts.hour, ts.minute, s);
  }

  void add(double price, DateTime ts) {
    final b = _bucket(ts);
    final cur = _current;
    if (cur == null || cur.ts != b) {
      if (cur != null) _candles.add(cur);
      while (_candles.length > maxCandles) {
        _candles.removeAt(0);
      }
      _current = Candle(ts: b, open: price, high: price, low: price, close: price);
    } else {
      if (price > cur.high) cur.high = price;
      if (price < cur.low) cur.low = price;
      cur.close = price;
    }
    _ctrl.add([..._candles, if (_current != null) _current!]);
  }

  void dispose() => _ctrl.close();
}
