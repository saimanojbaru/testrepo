import 'package:flutter_test/flutter_test.dart';
import 'package:scalping_agent/paper/market.dart';
import 'package:scalping_agent/strategies/base.dart';
import 'package:scalping_agent/strategies/concrete.dart';
import 'package:scalping_agent/strategies/concrete_extra.dart';

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
    test('exposes 15 strategies covering 3 regimes', () {
      final all = StrategyRegistry.all;
      expect(all, hasLength(15));
      final regimes = all.map((s) => s.regime).toSet();
      expect(regimes, containsAll(['TRENDING', 'RANGING', 'VOLATILE']));
    });

    test('all strategies default to disabled and have unique ids', () {
      final ids = <String>{};
      for (final s in StrategyRegistry.all) {
        expect(s.enabled, isFalse,
            reason: '${s.id} should default to disabled');
        expect(ids.add(s.id), isTrue,
            reason: 'duplicate strategy id: ${s.id}');
      }
    });
  });

  group('indicator helpers', () {
    test('sma returns arithmetic mean', () {
      expect(sma([1, 2, 3, 4, 5], 5), closeTo(3, 1e-9));
    });

    test('atr on flat bars is zero', () {
      final base = DateTime(2025, 1, 1, 9, 15);
      final bars = [
        for (int i = 0; i < 20; i++)
          Candle(
            ts: base.add(Duration(seconds: i * 5)),
            open: 100,
            high: 100,
            low: 100,
            close: 100,
          ),
      ];
      expect(atr(bars, 14), closeTo(0, 1e-9));
    });

    test('donchian picks highest high and lowest low', () {
      final base = DateTime(2025, 1, 1, 9, 15);
      final bars = [
        for (int i = 0; i < 10; i++)
          Candle(
            ts: base.add(Duration(seconds: i * 5)),
            open: 100.0 + i,
            high: 100.5 + i,
            low: 99.5 + i,
            close: 100.0 + i,
          ),
      ];
      final d = donchian(bars, 10);
      expect(d.high, closeTo(109.5, 1e-9));
      expect(d.low, closeTo(99.5, 1e-9));
    });

    test('heikinAshi open is midpoint of prior HA bar', () {
      final base = DateTime(2025, 1, 1, 9, 15);
      final raw1 = Candle(
          ts: base, open: 100, high: 102, low: 99, close: 101);
      final raw2 = Candle(
          ts: base.add(const Duration(seconds: 5)),
          open: 101, high: 103, low: 100, close: 102.5);
      final ha1 = heikinAshi(raw1, null);
      final ha2 = heikinAshi(raw2, ha1);
      expect(ha2.open, closeTo((ha1.open + ha1.close) / 2, 1e-9));
    });
  });

  group('new strategies fire signals', () {
    test('DonchianBreak fires on 20-bar high break', () {
      final s = DonchianBreak();
      // 21 flat bars at 100, then one bar above all prior highs
      final closes = [
        ...List.filled(21, 100.0),
        101.5,
      ];
      final sig = _runSeries(s, closes);
      expect(sig?.side, SignalSide.long);
    });

    test('InsideBarBreak fires on break of mother bar', () {
      final s = InsideBarBreak();
      final base = DateTime(2025, 1, 1, 9, 15);
      // mother range 99-102, inside 100-101, break bar closes 103
      final bars = [
        Candle(ts: base, open: 100, high: 102, low: 99, close: 101),
        Candle(
            ts: base.add(const Duration(seconds: 5)),
            open: 100.5, high: 101, low: 100, close: 100.8),
        Candle(
            ts: base.add(const Duration(seconds: 10)),
            open: 101, high: 103.2, low: 100.9, close: 103),
      ];
      final sig = s.onCandles('NIFTY', bars);
      expect(sig?.side, SignalSide.long);
    });

    test('PinBarReversal fires on long lower wick', () {
      final s = PinBarReversal();
      final base = DateTime(2025, 1, 1, 9, 15);
      // tiny body 100-100.2, long lower wick to 97 → bullish pin
      final bar = Candle(
          ts: base, open: 100, high: 100.3, low: 97, close: 100.2);
      final sig = s.onCandles('NIFTY', [bar]);
      expect(sig?.side, SignalSide.long);
    });

    test('MomentumBurst fires on 5-bar ROC > threshold', () {
      final s = MomentumBurst();
      final closes = [100.0, 100.0, 100.0, 100.0, 100.0, 101.0];
      final sig = _runSeries(s, closes);
      expect(sig?.side, SignalSide.long);
    });
  });
}

Signal? _runSeries(Strategy s, List<double> closes) {
  final base = DateTime(2025, 1, 1, 9, 15);
  Signal? out;
  for (int i = 0; i < closes.length; i++) {
    out = s.onCandles('NIFTY', _prefix(closes, base, i)) ?? out;
  }
  return out;
}

List<Candle> _prefix(List<double> closes, DateTime base, int upto) {
  return [
    for (int j = 0; j <= upto; j++)
      Candle(
        ts: base.add(Duration(seconds: j * 5)),
        open: j == 0 ? closes[j] : closes[j - 1],
        high: [
              if (j == 0) closes[j] else closes[j - 1],
              closes[j]
            ].reduce((a, b) => a > b ? a : b) +
            0.05,
        low: [
              if (j == 0) closes[j] else closes[j - 1],
              closes[j]
            ].reduce((a, b) => a < b ? a : b) -
            0.05,
        close: closes[j],
      ),
  ];
}
