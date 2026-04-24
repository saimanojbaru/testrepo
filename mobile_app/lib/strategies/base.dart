import 'dart:math' as math;

import 'package:flutter/foundation.dart';

import '../paper/market.dart';

enum SignalSide { long, short, exit }

class Signal {
  Signal({
    required this.strategy,
    required this.symbol,
    required this.side,
    required this.price,
    required this.ts,
    this.sl,
    this.tp,
    this.note,
  });

  final String strategy;
  final String symbol;
  final SignalSide side;
  final double price;
  final DateTime ts;
  final double? sl;
  final double? tp;
  final String? note;
}

abstract class Strategy extends ChangeNotifier {
  Strategy({
    required this.id,
    required this.name,
    required this.tagline,
    required this.regime,
    this.enabled = false,
  });

  final String id;
  final String name;
  final String tagline;
  final String regime;
  bool enabled;

  int signalsToday = 0;
  int wins = 0;
  int losses = 0;
  double pnl = 0;

  double get winRate {
    final n = wins + losses;
    return n == 0 ? 0 : wins / n;
  }

  void setEnabled(bool v) {
    enabled = v;
    notifyListeners();
  }

  Signal? onCandles(String symbol, List<Candle> candles);

  void recordSignal() {
    signalsToday += 1;
    notifyListeners();
  }

  void recordResult({required bool win, required double tradePnl}) {
    if (win) {
      wins += 1;
    } else {
      losses += 1;
    }
    pnl += tradePnl;
    notifyListeners();
  }
}

double ema(List<double> series, int period) {
  if (series.length < period) return series.last;
  final k = 2 / (period + 1);
  double e = series.sublist(0, period).reduce((a, b) => a + b) / period;
  for (int i = period; i < series.length; i++) {
    e = series[i] * k + e * (1 - k);
  }
  return e;
}

double rsi(List<double> closes, int period) {
  if (closes.length <= period) return 50;
  double gains = 0;
  double losses = 0;
  for (int i = closes.length - period; i < closes.length; i++) {
    final d = closes[i] - closes[i - 1];
    if (d > 0) {
      gains += d;
    } else {
      losses -= d;
    }
  }
  if (losses == 0) return 100;
  final rs = gains / losses;
  return 100 - (100 / (1 + rs));
}

({double mean, double sd}) bollinger(List<double> closes, int period) {
  if (closes.length < period) {
    return (mean: closes.last, sd: 0);
  }
  final tail = closes.sublist(closes.length - period);
  final mean = tail.reduce((a, b) => a + b) / period;
  final variance =
      tail.map((x) => (x - mean) * (x - mean)).reduce((a, b) => a + b) /
          period;
  return (mean: mean, sd: variance <= 0 ? 0 : math.sqrt(variance));
}

/// Simple moving average over the last [period] values.
double sma(List<double> series, int period) {
  if (series.length < period) return series.last;
  final tail = series.sublist(series.length - period);
  return tail.reduce((a, b) => a + b) / period;
}

/// Average True Range — smoothed volatility measure.
double atr(List<Candle> candles, int period) {
  if (candles.length < period + 1) return 0;
  double sum = 0;
  for (int i = candles.length - period; i < candles.length; i++) {
    final prev = candles[i - 1].close;
    final c = candles[i];
    final tr = math.max(
      c.high - c.low,
      math.max((c.high - prev).abs(), (c.low - prev).abs()),
    );
    sum += tr;
  }
  return sum / period;
}

/// MACD line (fast EMA - slow EMA) + signal EMA of that line.
({double macd, double signal, double hist}) macd(
    List<double> closes, {
    int fast = 12,
    int slow = 26,
    int signalPeriod = 9,
  }) {
  if (closes.length < slow + signalPeriod) {
    return (macd: 0, signal: 0, hist: 0);
  }
  final macdSeries = <double>[];
  for (int i = slow; i <= closes.length; i++) {
    final window = closes.sublist(0, i);
    macdSeries.add(ema(window, fast) - ema(window, slow));
  }
  final line = macdSeries.last;
  final sig = ema(macdSeries, signalPeriod);
  return (macd: line, signal: sig, hist: line - sig);
}

/// Donchian channel (highest-high, lowest-low) over N bars.
({double high, double low}) donchian(List<Candle> candles, int period) {
  if (candles.length < period) {
    return (high: candles.last.high, low: candles.last.low);
  }
  final tail = candles.sublist(candles.length - period);
  final hi = tail.map((c) => c.high).reduce(math.max);
  final lo = tail.map((c) => c.low).reduce(math.min);
  return (high: hi, low: lo);
}

/// Triple EMA: smoother EMA that reduces lag.
double tema(List<double> series, int period) {
  if (series.length < period * 3) return ema(series, period);
  final e1 = <double>[];
  final e2 = <double>[];
  final e3 = <double>[];
  for (int i = period; i <= series.length; i++) {
    e1.add(ema(series.sublist(0, i), period));
  }
  for (int i = period; i <= e1.length; i++) {
    e2.add(ema(e1.sublist(0, i), period));
  }
  for (int i = period; i <= e2.length; i++) {
    e3.add(ema(e2.sublist(0, i), period));
  }
  if (e1.isEmpty || e2.isEmpty || e3.isEmpty) return series.last;
  return 3 * e1.last - 3 * e2.last + e3.last;
}

/// Stochastic %K over the last [period] bars.
double stochK(List<Candle> candles, int period) {
  if (candles.length < period) return 50;
  final tail = candles.sublist(candles.length - period);
  final lo = tail.map((c) => c.low).reduce(math.min);
  final hi = tail.map((c) => c.high).reduce(math.max);
  if (hi == lo) return 50;
  return (candles.last.close - lo) / (hi - lo) * 100;
}

/// Heikin-Ashi transform of a raw candle using the previous HA bar.
Candle heikinAshi(Candle raw, Candle? haPrev) {
  final haClose = (raw.open + raw.high + raw.low + raw.close) / 4;
  final haOpen = haPrev == null
      ? (raw.open + raw.close) / 2
      : (haPrev.open + haPrev.close) / 2;
  final haHigh = [raw.high, haOpen, haClose].reduce(math.max);
  final haLow = [raw.low, haOpen, haClose].reduce(math.min);
  return Candle(
    ts: raw.ts,
    open: haOpen,
    high: haHigh,
    low: haLow,
    close: haClose,
  );
}
