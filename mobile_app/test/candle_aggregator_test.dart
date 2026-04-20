import 'package:flutter_test/flutter_test.dart';
import 'package:scalping_agent/paper/market.dart';

void main() {
  group('CandleAggregator', () {
    test('groups ticks into 5-second buckets', () async {
      final agg = CandleAggregator(bucketSeconds: 5);
      final base = DateTime(2025, 1, 1, 9, 15, 0);

      agg.add(100, base);
      agg.add(102, base.add(const Duration(seconds: 1)));
      agg.add(98, base.add(const Duration(seconds: 3)));
      // new bucket starts at +5s
      agg.add(99, base.add(const Duration(seconds: 5)));
      agg.add(105, base.add(const Duration(seconds: 9)));

      final candles = agg.candles;
      // one closed candle from 09:15:00 bucket
      expect(candles, hasLength(1));
      final c0 = candles[0];
      expect(c0.open, equals(100));
      expect(c0.high, equals(102));
      expect(c0.low, equals(98));
      expect(c0.close, equals(98));
      expect(c0.bullish, isFalse);

      agg.dispose();
    });

    test('open/high/low/close computed correctly within bucket', () {
      final agg = CandleAggregator(bucketSeconds: 5);
      final base = DateTime(2025, 1, 1, 9, 15, 0);
      for (final (off, px) in [
        (0, 100.0),
        (1, 99.0),
        (2, 103.0),
        (3, 101.5),
      ]) {
        agg.add(px, base.add(Duration(seconds: off)));
      }
      agg.add(90, base.add(const Duration(seconds: 5))); // close prev bucket

      final c = agg.candles.first;
      expect(c.open, 100);
      expect(c.high, 103);
      expect(c.low, 99);
      expect(c.close, 101.5);
      expect(c.bullish, isTrue);
      agg.dispose();
    });

    test('respects maxCandles by dropping oldest', () {
      final agg = CandleAggregator(bucketSeconds: 5, maxCandles: 3);
      final base = DateTime(2025, 1, 1, 9, 15, 0);
      for (int i = 0; i < 10; i++) {
        agg.add(100.0 + i, base.add(Duration(seconds: i * 5)));
      }
      expect(agg.candles.length, lessThanOrEqualTo(3));
      agg.dispose();
    });

    test('emits snapshot via stream on each add', () async {
      final agg = CandleAggregator(bucketSeconds: 5);
      final base = DateTime(2025, 1, 1, 9, 15, 0);
      final emissions = <int>[];
      final sub = agg.stream.listen((list) => emissions.add(list.length));

      agg.add(100, base);
      agg.add(101, base.add(const Duration(seconds: 1)));
      agg.add(102, base.add(const Duration(seconds: 5)));

      await Future.delayed(const Duration(milliseconds: 20));
      expect(emissions.length, greaterThanOrEqualTo(3));
      await sub.cancel();
      agg.dispose();
    });
  });

  group('MarketFeed', () {
    test('tracks latest price per instrument', () async {
      final feed = MarketFeed(tickMs: 20, seed: 42);
      final ticks = <Tick>[];
      final sub = feed.stream.listen(ticks.add);
      feed.start();
      await Future.delayed(const Duration(milliseconds: 80));
      await sub.cancel();
      feed.dispose();
      expect(ticks.length, greaterThan(0));
      for (final t in ticks) {
        expect(
          Instrument.all.map((i) => i.symbol),
          contains(t.symbol),
        );
      }
    });

    test('priceOf returns base price before start()', () {
      final feed = MarketFeed();
      expect(feed.priceOf('NIFTY'), equals(Instrument.nifty.basePrice));
      expect(feed.priceOf('UNKNOWN'), equals(0));
      feed.dispose();
    });
  });
}
