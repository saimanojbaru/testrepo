import 'dart:math' as math;

import '../paper/market.dart';
import 'base.dart';

/// MACD histogram flip — trending.
class MacdFlip extends Strategy {
  MacdFlip()
      : super(
          id: 'macd_flip',
          name: 'MACD Histogram Flip',
          tagline: 'Enters on histogram crossing zero',
          regime: 'TRENDING',
        );

  double? _prevHist;

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 40) return null;
    final closes = candles.map((c) => c.close).toList();
    final m = macd(closes);
    Signal? out;
    if (_prevHist != null) {
      final last = candles.last;
      if (_prevHist! <= 0 && m.hist > 0) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.long,
          price: last.close,
          ts: last.ts,
          sl: 22,
          tp: 36,
          note: 'MACD histogram flipped positive',
        );
      } else if (_prevHist! >= 0 && m.hist < 0) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.short,
          price: last.close,
          ts: last.ts,
          sl: 22,
          tp: 36,
          note: 'MACD histogram flipped negative',
        );
      }
    }
    _prevHist = m.hist;
    return out;
  }
}

/// Supertrend flip using ATR channel — trending.
class SupertrendFlip extends Strategy {
  SupertrendFlip()
      : super(
          id: 'supertrend',
          name: 'Supertrend Flip',
          tagline: 'ATR channel mid-line crossover',
          regime: 'TRENDING',
        );

  bool? _lastAboveBand;

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 15) return null;
    final a = atr(candles, 14);
    if (a == 0) return null;
    final last = candles.last;
    final mid = (last.high + last.low) / 2;
    final upper = mid + 2.5 * a;
    final lower = mid - 2.5 * a;
    final above = last.close > mid;
    Signal? out;
    if (_lastAboveBand != null && _lastAboveBand != above) {
      if (above) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.long,
          price: last.close,
          ts: last.ts,
          sl: (last.close - lower).abs(),
          tp: (upper - last.close).abs(),
          note: 'Trend flipped bullish (ATR ${a.toStringAsFixed(1)})',
        );
      } else {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.short,
          price: last.close,
          ts: last.ts,
          sl: (upper - last.close).abs(),
          tp: (last.close - lower).abs(),
          note: 'Trend flipped bearish',
        );
      }
    }
    _lastAboveBand = above;
    return out;
  }
}

/// Keltner channel breakout — volatile expansion.
class KeltnerBreakout extends Strategy {
  KeltnerBreakout()
      : super(
          id: 'keltner',
          name: 'Keltner Channel Break',
          tagline: 'Break of EMA ± ATR envelope',
          regime: 'VOLATILE',
        );

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 25) return null;
    final closes = candles.map((c) => c.close).toList();
    final mid = ema(closes, 20);
    final a = atr(candles, 14);
    if (a == 0) return null;
    final upper = mid + 1.5 * a;
    final lower = mid - 1.5 * a;
    final last = candles.last;
    final prev = candles[candles.length - 2];
    if (prev.close <= upper && last.close > upper) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: last.close,
        ts: last.ts,
        sl: a,
        tp: a * 1.8,
        note: 'Broke upper Keltner band',
      );
    }
    if (prev.close >= lower && last.close < lower) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: last.close,
        ts: last.ts,
        sl: a,
        tp: a * 1.8,
        note: 'Broke lower Keltner band',
      );
    }
    return null;
  }
}

/// Stochastic extremes with momentum confirm — ranging.
class StochasticReversal extends Strategy {
  StochasticReversal()
      : super(
          id: 'stoch_reversal',
          name: 'Stochastic Reversal',
          tagline: 'Oversold/overbought + price confirm',
          regime: 'RANGING',
        );

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 16) return null;
    final k = stochK(candles, 14);
    final last = candles.last;
    final prev = candles[candles.length - 2];
    if (k < 15 && last.close > prev.close) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: last.close,
        ts: last.ts,
        sl: 18,
        tp: 24,
        note: 'Stoch ${k.toStringAsFixed(1)} oversold + bullish bar',
      );
    }
    if (k > 85 && last.close < prev.close) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: last.close,
        ts: last.ts,
        sl: 18,
        tp: 24,
        note: 'Stoch ${k.toStringAsFixed(1)} overbought + bearish bar',
      );
    }
    return null;
  }
}

/// Donchian channel breakout — trending.
class DonchianBreak extends Strategy {
  DonchianBreak()
      : super(
          id: 'donchian',
          name: 'Donchian Break',
          tagline: '20-bar high/low breakout',
          regime: 'TRENDING',
        );

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 21) return null;
    final prior = candles.sublist(candles.length - 21, candles.length - 1);
    final d = donchian(prior, 20);
    final last = candles.last;
    if (last.close > d.high) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: last.close,
        ts: last.ts,
        sl: 20,
        tp: 40,
        note: '20-bar high break',
      );
    }
    if (last.close < d.low) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: last.close,
        ts: last.ts,
        sl: 20,
        tp: 40,
        note: '20-bar low break',
      );
    }
    return null;
  }
}

/// Heikin-Ashi trend follower — trending.
class HeikinAshiTrend extends Strategy {
  HeikinAshiTrend()
      : super(
          id: 'heikin_ashi',
          name: 'Heikin-Ashi Trend',
          tagline: 'Three same-colour HA bars',
          regime: 'TRENDING',
        );

  final List<Candle> _ha = [];

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 5) return null;
    // rebuild HA from tail on every call (cheap on small windows)
    _ha.clear();
    Candle? prev;
    for (final c in candles) {
      final ha = heikinAshi(c, prev);
      _ha.add(ha);
      prev = ha;
    }
    if (_ha.length < 4) return null;
    final last3 = _ha.sublist(_ha.length - 3);
    final allBull = last3.every((h) => h.bullish);
    final allBear = last3.every((h) => !h.bullish);
    if (!allBull && !allBear) return null;
    final rawLast = candles.last;
    if (allBull) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: rawLast.close,
        ts: rawLast.ts,
        sl: 20,
        tp: 30,
        note: 'Three bullish HA bars',
      );
    }
    return Signal(
      strategy: id,
      symbol: symbol,
      side: SignalSide.short,
      price: rawLast.close,
      ts: rawLast.ts,
      sl: 20,
      tp: 30,
      note: 'Three bearish HA bars',
    );
  }
}

/// Triple-EMA momentum — trending.
class TemaMomentum extends Strategy {
  TemaMomentum()
      : super(
          id: 'tema_momentum',
          name: 'TEMA Momentum',
          tagline: 'Triple EMA rate of change',
          regime: 'TRENDING',
        );

  double? _prev;

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 35) return null;
    final closes = candles.map((c) => c.close).toList();
    final t = tema(closes, 9);
    Signal? out;
    if (_prev != null) {
      final slope = t - _prev!;
      final last = candles.last;
      if (slope > 2 && last.close > t) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.long,
          price: last.close,
          ts: last.ts,
          sl: 18,
          tp: 32,
          note: 'TEMA slope +${slope.toStringAsFixed(2)}',
        );
      } else if (slope < -2 && last.close < t) {
        out = Signal(
          strategy: id,
          symbol: symbol,
          side: SignalSide.short,
          price: last.close,
          ts: last.ts,
          sl: 18,
          tp: 32,
          note: 'TEMA slope ${slope.toStringAsFixed(2)}',
        );
      }
    }
    _prev = t;
    return out;
  }
}

/// Momentum burst — N-bar price change above threshold.
class MomentumBurst extends Strategy {
  MomentumBurst()
      : super(
          id: 'momentum_burst',
          name: 'Momentum Burst',
          tagline: '5-bar ROC > 0.4%',
          regime: 'VOLATILE',
        );

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 6) return null;
    final anchor = candles[candles.length - 6];
    final last = candles.last;
    if (anchor.close == 0) return null;
    final roc = (last.close - anchor.close) / anchor.close;
    if (roc > 0.004) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: last.close,
        ts: last.ts,
        sl: 20,
        tp: 35,
        note: '5-bar ROC +${(roc * 100).toStringAsFixed(2)}%',
      );
    }
    if (roc < -0.004) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: last.close,
        ts: last.ts,
        sl: 20,
        tp: 35,
        note: '5-bar ROC ${(roc * 100).toStringAsFixed(2)}%',
      );
    }
    return null;
  }
}

/// Inside bar break — volatile; a bar fully inside the prior bar, then break.
class InsideBarBreak extends Strategy {
  InsideBarBreak()
      : super(
          id: 'inside_bar',
          name: 'Inside Bar Break',
          tagline: 'Compression inside prior bar, then break',
          regime: 'VOLATILE',
        );

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.length < 3) return null;
    final mother = candles[candles.length - 3];
    final inside = candles[candles.length - 2];
    final last = candles.last;
    final isInside =
        inside.high <= mother.high && inside.low >= mother.low;
    if (!isInside) return null;
    if (last.close > mother.high) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: last.close,
        ts: last.ts,
        sl: 18,
        tp: 28,
        note: 'Inside bar upside break',
      );
    }
    if (last.close < mother.low) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: last.close,
        ts: last.ts,
        sl: 18,
        tp: 28,
        note: 'Inside bar downside break',
      );
    }
    return null;
  }
}

/// Pin bar reversal — ranging; rejection wick indicates rejection of level.
class PinBarReversal extends Strategy {
  PinBarReversal()
      : super(
          id: 'pin_bar',
          name: 'Pin Bar Reversal',
          tagline: 'Long-wick rejection candles',
          regime: 'RANGING',
        );

  @override
  Signal? onCandles(String symbol, List<Candle> candles) {
    if (candles.isEmpty) return null;
    final c = candles.last;
    final body = (c.close - c.open).abs();
    final range = c.high - c.low;
    if (range <= 0) return null;
    final upperWick = c.high - math.max(c.open, c.close);
    final lowerWick = math.min(c.open, c.close) - c.low;
    if (body / range > 0.35) return null; // not pin-ish
    if (lowerWick > 2 * body && lowerWick / range > 0.6) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.long,
        price: c.close,
        ts: c.ts,
        sl: 16,
        tp: 24,
        note: 'Bullish pin bar (long lower wick)',
      );
    }
    if (upperWick > 2 * body && upperWick / range > 0.6) {
      return Signal(
        strategy: id,
        symbol: symbol,
        side: SignalSide.short,
        price: c.close,
        ts: c.ts,
        sl: 16,
        tp: 24,
        note: 'Bearish pin bar (long upper wick)',
      );
    }
    return null;
  }
}
