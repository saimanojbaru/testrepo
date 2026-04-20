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
