import '../paper/market.dart';
import 'base.dart';

class EmaCross extends Strategy {
  EmaCross()
      : super(
          id: 'ema_cross',
          name: 'EMA 9 / 21 Cross',
          tagline: 'Fast trend follower on momentum flips',
          regime: 'TRENDING',
        );

  double? _prevFast;
  double? _prevSlow;

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 22) return null;
    final closes = candles.map((c) => c.close).toList();
    final fast = ema(closes, 9);
    final slow = ema(closes, 21);
    final last = candles.last;
    Signal? out;
    if (_prevFast != null && _prevSlow != null) {
      final crossUp = _prevFast! <= _prevSlow! && fast > slow;
      final crossDn = _prevFast! >= _prevSlow! && fast < slow;
      if (crossUp) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.long,
          price: last.close,
          ts: last.ts,
          sl: 20,
          tp: 35,
          note: 'Fast EMA crossed above slow',
        );
      } else if (crossDn) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.short,
          price: last.close,
          ts: last.ts,
          sl: 20,
          tp: 35,
          note: 'Fast EMA crossed below slow',
        );
      }
    }
    _prevFast = fast;
    _prevSlow = slow;
    return out;
  }
}

class VwapReclaim extends Strategy {
  VwapReclaim()
      : super(
          id: 'vwap_reclaim',
          name: 'VWAP Reclaim',
          tagline: 'Fades rejection at session VWAP',
          regime: 'RANGING',
        );

  double? _prevClose;

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 10) return null;
    double total = 0;
    double weight = 0;
    for (final c in candles) {
      final tp = (c.high + c.low + c.close) / 3;
      final vol = (c.high - c.low).abs() + 1;
      total += tp * vol;
      weight += vol;
    }
    final vwap = total / weight;
    final last = candles.last;
    Signal? out;
    if (_prevClose != null) {
      final reclaimUp = _prevClose! < vwap && last.close > vwap;
      final reclaimDn = _prevClose! > vwap && last.close < vwap;
      if (reclaimUp) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.long,
          price: last.close,
          ts: last.ts,
          sl: 15,
          tp: 25,
          note: 'Reclaimed VWAP from below',
        );
      } else if (reclaimDn) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.short,
          price: last.close,
          ts: last.ts,
          sl: 15,
          tp: 25,
          note: 'Lost VWAP from above',
        );
      }
    }
    _prevClose = last.close;
    return out;
  }
}

class OpeningRangeBreakout extends Strategy {
  OpeningRangeBreakout()
      : super(
          id: 'orb',
          name: 'Opening Range Breakout',
          tagline: 'First 5 candles define the range',
          regime: 'TRENDING',
        );

  double? _orHigh;
  double? _orLow;
  bool _fired = false;

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 5) return null;
    if (_orHigh == null) {
      final first = candles.take(5);
      _orHigh = first.map((c) => c.high).reduce((a, b) => a > b ? a : b);
      _orLow = first.map((c) => c.low).reduce((a, b) => a < b ? a : b);
    }
    if (_fired) return null;
    final last = candles.last;
    if (last.close > _orHigh!) {
      _fired = true;
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: last.close,
        ts: last.ts,
        sl: 25,
        tp: 50,
        note: 'Broke opening range high',
      );
    }
    if (last.close < _orLow!) {
      _fired = true;
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: last.close,
        ts: last.ts,
        sl: 25,
        tp: 50,
        note: 'Broke opening range low',
      );
    }
    return null;
  }
}

class RsiFade extends Strategy {
  RsiFade()
      : super(
          id: 'rsi_fade',
          name: 'RSI Extremes Fade',
          tagline: 'Mean-reverts overbought / oversold',
          regime: 'RANGING',
        );

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 15) return null;
    final closes = candles.map((c) => c.close).toList();
    final r = rsi(closes, 14);
    final last = candles.last;
    if (r < 28) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: last.close,
        ts: last.ts,
        sl: 18,
        tp: 22,
        note: 'RSI ${r.toStringAsFixed(1)} oversold',
      );
    }
    if (r > 72) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: last.close,
        ts: last.ts,
        sl: 18,
        tp: 22,
        note: 'RSI ${r.toStringAsFixed(1)} overbought',
      );
    }
    return null;
  }
}

class BollingerSqueeze extends Strategy {
  BollingerSqueeze()
      : super(
          id: 'bb_squeeze',
          name: 'Bollinger Squeeze',
          tagline: 'Breakout from compressed bands',
          regime: 'VOLATILE',
        );

  final List<double> _widthHistory = [];

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 22) return null;
    final closes = candles.map((c) => c.close).toList();
    final bb = bollinger(closes, 20);
    final width = bb.sd * 2;
    _widthHistory.add(width);
    if (_widthHistory.length > 40) _widthHistory.removeAt(0);
    if (_widthHistory.length < 20) return null;
    final avg = _widthHistory.reduce((a, b) => a + b) / _widthHistory.length;
    final squeezed = width < avg * 0.6;
    final last = candles.last;
    if (!squeezed && _widthHistory[_widthHistory.length - 2] < avg * 0.6) {
      if (last.close > bb.mean + bb.sd) {
        return Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.long,
          price: last.close,
          ts: last.ts,
          sl: 22,
          tp: 40,
          note: 'Squeeze → upside expansion',
        );
      }
      if (last.close < bb.mean - bb.sd) {
        return Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.short,
          price: last.close,
          ts: last.ts,
          sl: 22,
          tp: 40,
          note: 'Squeeze → downside expansion',
        );
      }
    }
    return null;
  }
}

class StrategyRegistry {
  StrategyRegistry._();

  static final List<Strategy> all = [
    EmaCross(),
    VwapReclaim(),
    OpeningRangeBreakout(),
    RsiFade(),
    BollingerSqueeze(),
  ];
}
