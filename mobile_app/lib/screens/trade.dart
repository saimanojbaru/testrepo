import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../paper/market.dart';
import '../paper/paper_trader.dart';
import '../state/app_mode.dart';
import '../state/market_controller.dart';
import '../strategies/base.dart';
import '../widgets/candle_chart.dart';
import '../widgets/mode_switch.dart';
import '../widgets/watchlist.dart';

class TradeScreen extends ConsumerStatefulWidget {
  const TradeScreen({super.key});

  @override
  ConsumerState<TradeScreen> createState() => _TradeScreenState();
}

class _TradeScreenState extends ConsumerState<TradeScreen> {
  int _lots = 1;
  double _sl = 20;
  double _tp = 30;

  @override
  Widget build(BuildContext context) {
    final mc = ref.watch(marketControllerProvider);
    final mode = ref.watch(appModeProvider);
    final price = mc.price(mc.selectedSymbol);
    final inst = mc.selectedInstrument;

    return Scaffold(
      backgroundColor: const Color(0xFF0B1220),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        titleSpacing: 12,
        title: Row(
          children: [
            Text(
              'Trade',
              style: GoogleFonts.jetBrainsMono(
                  fontWeight: FontWeight.w700, fontSize: 18),
            ),
            const SizedBox(width: 10),
            const ModeSwitch(),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Auto-execute signals',
            icon: Icon(
              mc.autoExecuteSignals
                  ? Icons.autorenew
                  : Icons.pause_circle_outline,
              color: mc.autoExecuteSignals
                  ? const Color(0xFF22D3EE)
                  : Colors.white54,
            ),
            onPressed: () => mc.setAutoExecute(!mc.autoExecuteSignals),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          WatchlistStrip(
            prices: {for (final i in Instrument.all) i.symbol: mc.price(i.symbol)},
            previousPrices: mc.previousPrices,
            selected: mc.selectedSymbol,
            onSelect: mc.selectSymbol,
          ),
          const SizedBox(height: 8),
          _HeaderBlock(
              symbol: mc.selectedSymbol,
              price: price,
              inst: inst,
              isLive: mc.isLiveFeed,
              feedError: mc.feedError),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: CandleChart(candles: mc.selectedAggregator.candles),
          ),
          const SizedBox(height: 10),
          _SignalsStrip(runner: mc.runner),
          const SizedBox(height: 10),
          _PnlStrip(
            realized: mc.trader.realizedPnl(),
            unrealized: mc.trader.unrealizedPnl(price),
            trades: mc.trader.tradesToday,
            open: mc.trader.openCount,
          ),
          const SizedBox(height: 14),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: _TradeControls(
              lots: _lots,
              sl: _sl,
              tp: _tp,
              onLots: (v) => setState(() => _lots = v),
              onSl: (v) => setState(() => _sl = v),
              onTp: (v) => setState(() => _tp = v),
            ),
          ),
          const SizedBox(height: 10),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: _OrderButtons(
              mode: mode,
              price: price,
              onLong: () => _enter(mc, TradeSide.long, price),
              onShort: () => _enter(mc, TradeSide.short, price),
            ),
          ),
          const SizedBox(height: 18),
          const _SectionHeader('OPEN POSITIONS'),
          if (mc.trader.open.isEmpty)
            const _Empty('No open positions.'),
          for (final p in mc.trader.open)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: _OpenTile(
                pos: p,
                px: price,
                onClose: () => mc.trader.closeTrade(p.id, price),
              ),
            ),
          const SizedBox(height: 18),
          const _SectionHeader('RECENT TRADES'),
          if (mc.trader.closed.isEmpty)
            const _Empty('No trades yet.'),
          for (final t in mc.trader.closed.take(10))
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: _ClosedTile(trade: t),
            ),
        ],
      ),
    );
  }

  void _enter(MarketController mc, TradeSide side, double price) {
    mc.trader.openTrade(
      side: side,
      price: price,
      lots: _lots,
      sl: _sl,
      tp: _tp,
    );
  }
}

class _HeaderBlock extends StatelessWidget {
  const _HeaderBlock({
    required this.symbol,
    required this.price,
    required this.inst,
    required this.isLive,
    this.feedError,
  });
  final String symbol;
  final double price;
  final Instrument inst;
  final bool isLive;
  final String? feedError;

  @override
  Widget build(BuildContext context) {
    final change = price - inst.basePrice;
    final pct = change / inst.basePrice * 100;
    final up = change >= 0;
    final color = up ? const Color(0xFF10B981) : const Color(0xFFF43F5E);
    final badgeColor = feedError != null
        ? const Color(0xFFF43F5E)
        : (isLive ? const Color(0xFF10B981) : const Color(0xFF64748B));
    final badgeText = feedError != null
        ? 'FEED ERROR'
        : (isLive ? 'UPSTOX LIVE' : 'SIMULATED');
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 12),
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      decoration: BoxDecoration(
        gradient: LinearGradient(colors: [
          color.withOpacity(0.12),
          const Color(0xFF0F172A),
        ]),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Row(
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 7, height: 7,
                    decoration: BoxDecoration(
                        color: badgeColor, shape: BoxShape.circle),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    badgeText,
                    style: GoogleFonts.jetBrainsMono(
                      fontSize: 9, letterSpacing: 2, color: badgeColor,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 2),
              Text(
                inst.label,
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 11, color: Colors.white70,
                ),
              ),
              Text(
                symbol,
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white,
                ),
              ),
            ],
          ),
          const Spacer(),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                price.toStringAsFixed(2),
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 26, fontWeight: FontWeight.bold, color: Colors.white,
                ),
              ),
              Text(
                '${up ? '+' : ''}${change.toStringAsFixed(2)}   ${up ? '+' : ''}${pct.toStringAsFixed(2)}%',
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 12, color: color, fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SignalsStrip extends StatelessWidget {
  const _SignalsStrip({required this.runner});
  final dynamic runner;

  @override
  Widget build(BuildContext context) {
    final List<Signal> feed = runner.feed;
    if (feed.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            border: Border.all(color: Colors.white10),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(
            runner.activeCount == 0
                ? 'No strategies active — enable in Strategies tab.'
                : 'Strategies armed · waiting for signal…',
            style: GoogleFonts.jetBrainsMono(
                fontSize: 11, color: Colors.white54),
          ),
        ),
      );
    }
    return SizedBox(
      height: 48,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        itemCount: feed.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (_, i) {
          final s = feed[i];
          final up = s.side == SignalSide.long;
          final color =
              up ? const Color(0xFF10B981) : const Color(0xFFF43F5E);
          return Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: color.withOpacity(0.12),
              border: Border.all(color: color.withOpacity(0.5)),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              children: [
                Icon(up ? Icons.arrow_upward : Icons.arrow_downward,
                    color: color, size: 14),
                const SizedBox(width: 4),
                Text(
                  '${s.strategy.toUpperCase()} · ${s.symbol}',
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 10, color: Colors.white),
                ),
                const SizedBox(width: 6),
                Text(
                  s.price.toStringAsFixed(1),
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 10, color: color, fontWeight: FontWeight.bold),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _PnlStrip extends StatelessWidget {
  const _PnlStrip({
    required this.realized,
    required this.unrealized,
    required this.trades,
    required this.open,
  });
  final double realized;
  final double unrealized;
  final int trades;
  final int open;

  @override
  Widget build(BuildContext context) {
    final session = realized + unrealized;
    final color = session >= 0
        ? const Color(0xFF10B981)
        : const Color(0xFFF43F5E);
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 12),
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 6),
      decoration: BoxDecoration(
        color: const Color(0xFF111827),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF1E293B)),
      ),
      child: Row(
        children: [
          _Metric(label: 'SESSION', value: '₹${session.toStringAsFixed(0)}', color: color),
          _Metric(label: 'REALIZED', value: '₹${realized.toStringAsFixed(0)}'),
          _Metric(label: 'UNREAL', value: '₹${unrealized.toStringAsFixed(0)}'),
          _Metric(label: 'TRADES', value: '$trades · ${open}O'),
        ],
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value, this.color});
  final String label;
  final String value;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(
            label,
            style: GoogleFonts.jetBrainsMono(
              fontSize: 9, letterSpacing: 1.4, color: Colors.white54,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            value,
            style: GoogleFonts.jetBrainsMono(
              fontSize: 13, fontWeight: FontWeight.bold,
              color: color ?? Colors.white,
            ),
          ),
        ],
      ),
    );
  }
}

class _TradeControls extends StatelessWidget {
  const _TradeControls({
    required this.lots,
    required this.sl,
    required this.tp,
    required this.onLots,
    required this.onSl,
    required this.onTp,
  });
  final int lots;
  final double sl;
  final double tp;
  final ValueChanged<int> onLots;
  final ValueChanged<double> onSl;
  final ValueChanged<double> onTp;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('LOTS',
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 10, letterSpacing: 2, color: Colors.white54)),
            const Spacer(),
            for (final n in [1, 2, 3, 5])
              Padding(
                padding: const EdgeInsets.only(left: 6),
                child: ChoiceChip(
                  label: Text('${n}L',
                      style: GoogleFonts.jetBrainsMono(fontSize: 11)),
                  selected: lots == n,
                  onSelected: (_) => onLots(n),
                  backgroundColor: const Color(0xFF1E293B),
                  selectedColor: const Color(0xFF0EA5E9),
                  labelStyle: TextStyle(
                      color: lots == n ? Colors.black : Colors.white70),
                  side: BorderSide.none,
                ),
              ),
          ],
        ),
        _SliderRow(
            label: 'SL',
            value: sl,
            min: 5,
            max: 80,
            color: const Color(0xFFF43F5E),
            onChanged: onSl),
        _SliderRow(
            label: 'TP',
            value: tp,
            min: 5,
            max: 120,
            color: const Color(0xFF10B981),
            onChanged: onTp),
      ],
    );
  }
}

class _SliderRow extends StatelessWidget {
  const _SliderRow({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.color,
    required this.onChanged,
  });
  final String label;
  final double value;
  final double min;
  final double max;
  final Color color;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(
          width: 32,
          child: Text(label,
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 10, letterSpacing: 2, color: Colors.white54)),
        ),
        Expanded(
          child: SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: color,
              thumbColor: color,
              inactiveTrackColor: Colors.white12,
              overlayColor: color.withOpacity(0.2),
              trackHeight: 3,
            ),
            child: Slider(
                value: value, min: min, max: max, onChanged: onChanged),
          ),
        ),
        SizedBox(
          width: 60,
          child: Text(
            '${value.toStringAsFixed(0)} pts',
            textAlign: TextAlign.right,
            style: GoogleFonts.jetBrainsMono(
                fontSize: 11, color: Colors.white70),
          ),
        ),
      ],
    );
  }
}

class _OrderButtons extends StatelessWidget {
  const _OrderButtons({
    required this.mode,
    required this.price,
    required this.onLong,
    required this.onShort,
  });
  final AppMode mode;
  final double price;
  final VoidCallback onLong;
  final VoidCallback onShort;

  @override
  Widget build(BuildContext context) {
    if (mode.isLive) {
      return Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          border: Border.all(color: const Color(0xFFF43F5E)),
          borderRadius: BorderRadius.circular(14),
          color: const Color(0xFFF43F5E).withOpacity(0.08),
        ),
        child: Row(
          children: [
            const Icon(Icons.link_off, color: Color(0xFFF43F5E)),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'LIVE mode — connect broker in Settings before placing real orders.',
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 11, color: Colors.white),
              ),
            ),
          ],
        ),
      );
    }
    return Row(
      children: [
        Expanded(
          child: _OrderBtn(
            label: 'LONG',
            sub: 'BUY @ ${price.toStringAsFixed(1)}',
            color: const Color(0xFF10B981),
            icon: Icons.trending_up,
            onTap: onLong,
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _OrderBtn(
            label: 'SHORT',
            sub: 'SELL @ ${price.toStringAsFixed(1)}',
            color: const Color(0xFFF43F5E),
            icon: Icons.trending_down,
            onTap: onShort,
          ),
        ),
      ],
    );
  }
}

class _OrderBtn extends StatelessWidget {
  const _OrderBtn({
    required this.label,
    required this.sub,
    required this.color,
    required this.icon,
    required this.onTap,
  });
  final String label;
  final String sub;
  final Color color;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [color.withOpacity(0.9), color.withOpacity(0.6)],
          ),
          borderRadius: BorderRadius.circular(14),
          boxShadow: [
            BoxShadow(
                color: color.withOpacity(0.3),
                blurRadius: 12,
                offset: const Offset(0, 4)),
          ],
        ),
        child: Column(
          children: [
            Icon(icon, color: Colors.white, size: 24),
            const SizedBox(height: 2),
            Text(
              label,
              style: GoogleFonts.jetBrainsMono(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                letterSpacing: 3,
                color: Colors.white,
              ),
            ),
            Text(
              sub,
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 9, color: Colors.white70),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 6),
      child: Text(
        text,
        style: GoogleFonts.jetBrainsMono(
          fontSize: 11,
          letterSpacing: 2,
          color: Colors.white54,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          border: Border.all(color: Colors.white10),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(text,
            style: GoogleFonts.jetBrainsMono(
                fontSize: 11, color: Colors.white38)),
      ),
    );
  }
}

class _OpenTile extends StatelessWidget {
  const _OpenTile({required this.pos, required this.px, required this.onClose});
  final PaperPosition pos;
  final double px;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final mtm = pos.mtm(px);
    final color =
        mtm >= 0 ? const Color(0xFF10B981) : const Color(0xFFF43F5E);
    final isLong = pos.side == TradeSide.long;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF111827),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: (isLong ? const Color(0xFF10B981) : const Color(0xFFF43F5E))
                  .withOpacity(0.2),
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              isLong ? 'LONG' : 'SHORT',
              style: GoogleFonts.jetBrainsMono(
                fontSize: 10,
                fontWeight: FontWeight.bold,
                color: isLong
                    ? const Color(0xFF10B981)
                    : const Color(0xFFF43F5E),
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${pos.lots}L @ ${pos.entry.toStringAsFixed(1)}',
                    style: GoogleFonts.jetBrainsMono(
                        fontSize: 12, color: Colors.white)),
                Text(
                  'SL ${pos.slPoints?.toStringAsFixed(0) ?? '-'} · TP ${pos.tpPoints?.toStringAsFixed(0) ?? '-'}',
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 10, color: Colors.white54),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                '₹${mtm.toStringAsFixed(0)}',
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: color,
                ),
              ),
              InkWell(
                onTap: onClose,
                child: Container(
                  margin: const EdgeInsets.only(top: 3),
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.white12,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text('CLOSE',
                      style: GoogleFonts.jetBrainsMono(
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                          color: Colors.white)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ClosedTile extends StatelessWidget {
  const _ClosedTile({required this.trade});
  final ClosedTrade trade;

  @override
  Widget build(BuildContext context) {
    final color = trade.netPnl >= 0
        ? const Color(0xFF10B981)
        : const Color(0xFFF43F5E);
    final isLong = trade.side == TradeSide.long;
    final ts = DateFormat('HH:mm:ss').format(trade.closedAt);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          SizedBox(
            width: 42,
            child: Text(
              isLong ? 'LONG' : 'SHORT',
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 9,
                  color: isLong
                      ? const Color(0xFF10B981)
                      : const Color(0xFFF43F5E)),
            ),
          ),
          Expanded(
            child: Text(
              '${trade.entry.toStringAsFixed(1)} → ${trade.exit.toStringAsFixed(1)}  ·  ${trade.reason}',
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 10, color: Colors.white70),
            ),
          ),
          Text(ts,
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 9, color: Colors.white38)),
          const SizedBox(width: 8),
          SizedBox(
            width: 56,
            child: Text(
              '₹${trade.netPnl.toStringAsFixed(0)}',
              textAlign: TextAlign.right,
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 11, fontWeight: FontWeight.bold, color: color),
            ),
          ),
        ],
      ),
    );
  }
}
