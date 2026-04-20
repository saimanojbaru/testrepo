import 'package:flutter_test/flutter_test.dart';
import 'package:scalping_agent/paper/paper_trader.dart';

void main() {
  group('PaperTrader', () {
    test('long position MTM tracks price correctly', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.long, price: 24500, lots: 1);
      expect(t.unrealizedPnl(24520), 500.0);
      expect(t.unrealizedPnl(24480), -500.0);
    });

    test('short position MTM is inverted', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.short, price: 24500, lots: 2);
      expect(t.unrealizedPnl(24480), 1000.0);
      expect(t.unrealizedPnl(24520), -1000.0);
    });

    test('SL fires for long when price drops >= sl points', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.long, price: 24500, lots: 1, sl: 20);
      t.onTick(24490);
      expect(t.openCount, 1);
      t.onTick(24479);
      expect(t.openCount, 0);
      expect(t.closed.first.reason, 'SL');
    });

    test('TP fires for long when price rises >= tp points', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.long, price: 24500, lots: 1, tp: 30);
      t.onTick(24520);
      expect(t.openCount, 1);
      t.onTick(24531);
      expect(t.openCount, 0);
      expect(t.closed.first.reason, 'TP');
    });

    test('SL fires for short when price rises', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.short, price: 24500, lots: 1, sl: 25);
      t.onTick(24524);
      expect(t.openCount, 1);
      t.onTick(24526);
      expect(t.openCount, 0);
      expect(t.closed.first.reason, 'SL');
    });

    test('manual close records round-trip cost', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.long, price: 24500, lots: 2);
      t.closeTrade(1, 24520);
      expect(t.closed.length, 1);
      expect(t.closed.first.grossPnl, 1000.0);
      expect(t.closed.first.costs, 80.0);
      expect(t.closed.first.netPnl, 920.0);
    });

    test('session P&L combines realized and unrealized', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.long, price: 24500, lots: 1);
      t.closeTrade(1, 24600);
      t.openTrade(side: TradeSide.short, price: 24600, lots: 1);
      expect(t.realizedPnl(), 2500.0 - 40.0);
      expect(t.unrealizedPnl(24580), 500.0);
      expect(t.sessionPnl(24580), closeTo(2960.0, 0.01));
    });

    test('multiple positions close independently on SL/TP hits', () {
      final t = PaperTrader();
      t.openTrade(side: TradeSide.long, price: 24500, lots: 1, sl: 15);
      t.openTrade(side: TradeSide.long, price: 24500, lots: 1, tp: 20);
      t.onTick(24484);
      expect(t.openCount, 1);
      expect(t.closed.first.reason, 'SL');
      t.onTick(24521);
      expect(t.openCount, 0);
      expect(t.closed.first.reason, 'TP');
    });
  });
}
