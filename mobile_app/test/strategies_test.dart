import 'package:flutter_test/flutter_test.dart';
import 'package:scalping_agent/paper/market.dart';
import 'package:scalping_agent/strategies/base.dart';
import 'package:scalping_agent/strategies/concrete.dart';

Candle _c(DateTime ts, double open, double high, double low, double close) =>
    Candle(ts: ts, open: open, high: high, low: low, close: close);

List<Candle> _series(List<double> closes) {
  final base = DateTime(2025, 1, 1, 9, 15);
  return [
    for (int i = 0; i < closes.length; i++)
      _c(
        base.add(Duration(seconds: i * 5)),
        closes[i] - 0.5,
        closes[i] + 0.5,
        closes[i] - 1,
        closes[i],
      ),
  ];
}

void main() {
  group('ema / rsi / bollinger helpers', () {
    test('ema on flat series equals the constant', () {
      final e = ema(List.filled(30, 100), 9);
      expect(e, closeTo(100, 1e-9));
    });

    test('ema on monotonically rising series is between min and max', () {
      final closes = List.generate(40, (i) => 100.0 + i.toDouble());
      final e = ema(closes, 9);
      expect(e, greaterThan(closes.first));
      expect(e, lessThan(closes.last));
    });

    test('rsi returns 100 on monotonic up, <10 on monotonic down', () {
      final up = List.generate(30, (i) => 100.0 + i);
      final down = List.generate(30, (i) => 200.0 - i);
      expect(rsi(up, 14), equals(100));
      expect(rsi(down, 14), lessThan(10));
    });

    test('bollinger mean/sd on constant series → sd=0', () {
      final b = bollinger(List.filled(25, 50), 20);
      expect(b.mean, equals(50));
      expect(b.sd, equals(0));
    });

    test('bollinger sd nonzero when data varies', () {
      final closes = [for (int i = 0; i < 20; i++) 100.0 + (i % 2 == 0 ? 1 : -1)];
      final b = bollinger(closes, 20);
      expect(b.sd, greaterThan(0));
    });
  });

  group('EmaCross strategy', () {
    test('fires a long signal when fast crosses above slow', () {
      final s = EmaCross();
      // prime with flat prices, then climb
      final primer = _series(List.filled(25, 100.0));
      final rising = _series([...List.filled(25, 100.0), ...List.generate(15, (i) => 100.0 + i * 2)]);
      s.onCandles('NIFTY', primer);
      final sig = s.onCandles('NIFTY', rising);
      expect(sig, isNotNull);
      expect(sig!.side, SignalSide.long);
    });

    test('returns null when there are fewer than 22 candles', () {
      final s = EmaCross();
      final sig = s.onCandles('NIFTY', _series(List.filled(10, 100.0)));
      expect(sig, isNull);
    });
  });

  group('RsiFade strategy', () {
    test('fires a long when RSI < 28 (oversold)', () {
      final s = RsiFade();
      final closes = [
        for (int i = 0; i < 30; i++) 100.0,
        ...List.generate(20, (i) => 100.0 - (i + 1) * 0.8),
      ];
      final sig = s.onCandles('NIFTY', _series(closes));
      expect(sig?.side, SignalSide.long);
    });

    test('fires a short when RSI > 72 (overbought)', () {
      final s = RsiFade();
      final closes = [
        for (int i = 0; i < 30; i++) 100.0,
        ...List.generate(20, (i) => 100.0 + (i + 1) * 0.8),
      ];
      final sig = s.onCandles('NIFTY', _series(closes));
      expect(sig?.side, SignalSide.short);
    });
  });

  group('OpeningRangeBreakout', () {
    test('fires only once per session on break of range high', () {
      final s = OpeningRangeBreakout();
      final base = _series(List.filled(5, 100.0));
      final s1 = s.onCandles('NIFTY', base);
      expect(s1, isNull);

      final withBreak = _series([
        ...List.filled(5, 100.0),
        110.0,
      ]);
      final sig = s.onCandles('NIFTY', withBreak);
      expect(sig?.side, SignalSide.long);

      // second break should not re-fire
      final withBreak2 = _series([
        ...List.filled(5, 100.0),
        110.0,
        115.0,
      ]);
      final sig2 = s.onCandles('NIFTY', withBreak2);
      expect(sig2, isNull);
    });
  });

  group('Strategy base state', () {
    test('recordSignal increments signalsToday', () {
      final s = EmaCross();
      expect(s.signalsToday, 0);
      s.recordSignal();
      s.recordSignal();
      expect(s.signalsToday, 2);
    });

    test('recordResult updates wins/losses/pnl and winRate', () {
      final s = EmaCross();
      s.recordResult(win: true, tradePnl: 500);
      s.recordResult(win: false, tradePnl: -200);
      s.recordResult(win: true, tradePnl: 300);
      expect(s.wins, 2);
      expect(s.losses, 1);
      expect(s.pnl, 600);
      expect(s.winRate, closeTo(2 / 3, 1e-9));
    });

    test('setEnabled flips flag and notifies', () {
      final s = VwapReclaim();
      var notified = 0;
      s.addListener(() => notified++);
      s.setEnabled(true);
      expect(s.enabled, isTrue);
      expect(notified, 1);
    });
  });

  group('StrategyRegistry', () {
    test('exposes exactly 5 strategies covering 3 regimes', () {
      final all = StrategyRegistry.all;
      expect(all, hasLength(5));
      final regimes = all.map((s) => s.regime).toSet();
      expect(regimes, containsAll(['TRENDING', 'RANGING', 'VOLATILE']));
    });

    test('all strategies default to disabled', () {
      for (final s in StrategyRegistry.all) {
        expect(s.enabled, isFalse,
            reason: '${s.id} should default to disabled');
      }
    });
  });
}
