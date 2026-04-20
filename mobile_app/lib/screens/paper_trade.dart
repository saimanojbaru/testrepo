import 'dart:async';

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../paper/nifty_feed.dart';
import '../paper/paper_trader.dart';

class PaperTradeScreen extends StatefulWidget {
  const PaperTradeScreen({super.key});

  @override
  State<PaperTradeScreen> createState() => _PaperTradeScreenState();
}

class _PaperTradeScreenState extends State<PaperTradeScreen> {
  final _feed = NiftyFeed();
  final _trader = PaperTrader();
  final List<double> _history = [];
  double _price = 24500;
  double _prevPrice = 24500;
  StreamSubscription<double>? _sub;

  int _lots = 1;
  double _sl = 20;
  double _tp = 30;

  @override
  void initState() {
    super.initState();
    _feed.start();
    _sub = _feed.stream.listen((px) {
      setState(() {
        _prevPrice = _price;
        _price = px;
        _history.add(px);
        if (_history.length > 120) _history.removeAt(0);
      });
      _trader.onTick(px);
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    _feed.dispose();
    _trader.dispose();
    super.dispose();
  }

  void _open(TradeSide side) {
    _trader.openTrade(
      side: side,
      price: _price,
      lots: _lots,
      sl: _sl,
      tp: _tp,
    );
  }

  @override
  Widget build(BuildContext context) {
    final change = _history.isNotEmpty ? _price - _history.first : 0.0;
    final changePct =
        _history.isNotEmpty ? change / _history.first * 100 : 0.0;
    final up = _price >= _prevPrice;
    final tickColor = up ? const Color(0xFF10B981) : const Color(0xFFF43F5E);

    return Scaffold(
      backgroundColor: const Color(0xFF0B1220),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: Text(
          'Paper Trade · NIFTY',
          style: GoogleFonts.jetBrainsMono(fontWeight: FontWeight.w600),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(
              child: Chip(
                label: Text('SIM', style: GoogleFonts.jetBrainsMono(fontSize: 10)),
                backgroundColor: const Color(0xFF1E293B),
                side: BorderSide.none,
                visualDensity: VisualDensity.compact,
              ),
            ),
          ),
        ],
      ),
      body: AnimatedBuilder(
        animation: _trader,
        builder: (_, __) {
          return ListView(
            padding: const EdgeInsets.all(14),
            children: [
              _PriceBlock(
                price: _price,
                change: change,
                changePct: changePct,
                tickColor: tickColor,
              ),
              const SizedBox(height: 12),
              _Sparkline(history: _history, up: change >= 0),
              const SizedBox(height: 14),
              _PnlStrip(
                realized: _trader.realizedPnl(),
                unrealized: _trader.unrealizedPnl(_price),
                trades: _trader.tradesToday,
                open: _trader.openCount,
              ),
              const SizedBox(height: 18),
              _TradeControls(
                lots: _lots,
                sl: _sl,
                tp: _tp,
                onLots: (v) => setState(() => _lots = v),
                onSl: (v) => setState(() => _sl = v),
                onTp: (v) => setState(() => _tp = v),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: _TradeButton(
                      label: 'LONG',
                      sub: 'BUY @ ${_price.toStringAsFixed(1)}',
                      color: const Color(0xFF10B981),
                      icon: Icons.trending_up,
                      onTap: () => _open(TradeSide.long),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _TradeButton(
                      label: 'SHORT',
                      sub: 'SELL @ ${_price.toStringAsFixed(1)}',
                      color: const Color(0xFFF43F5E),
                      icon: Icons.trending_down,
                      onTap: () => _open(TradeSide.short),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 22),
              _SectionHeader('OPEN POSITIONS'),
              if (_trader.open.isEmpty)
                const _EmptyHint('No open positions. Tap LONG / SHORT to enter.'),
              for (final p in _trader.open)
                _OpenTile(
                  pos: p,
                  px: _price,
                  onClose: () => _trader.closeTrade(p.id, _price),
                ),
              const SizedBox(height: 18),
              _SectionHeader('RECENT TRADES'),
              if (_trader.closed.isEmpty)
                const _EmptyHint('No trades yet.'),
              for (final t in _trader.closed.take(8)) _ClosedTile(trade: t),
              const SizedBox(height: 24),
            ],
          );
        },
      ),
    );
  }
}

class _PriceBlock extends StatelessWidget {
  const _PriceBlock({
    required this.price,
    required this.change,
    required this.changePct,
    required this.tickColor,
  });
  final double price;
  final double change;
  final double changePct;
  final Color tickColor;

  @override
  Widget build(BuildContext context) {
    final sign = change >= 0 ? '+' : '';
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            tickColor.withOpacity(0.12),
            const Color(0xFF111827),
          ],
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: tickColor.withOpacity(0.3), width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: tickColor,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                'LIVE · SIMULATED',
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 10,
                  letterSpacing: 2,
                  color: Colors.white54,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            price.toStringAsFixed(2),
            style: GoogleFonts.jetBrainsMono(
              fontSize: 40,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '$sign${change.toStringAsFixed(2)}   $sign${changePct.toStringAsFixed(2)}%',
            style: GoogleFonts.jetBrainsMono(
              fontSize: 14,
              color: tickColor,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _Sparkline extends StatelessWidget {
  const _Sparkline({required this.history, required this.up});
  final List<double> history;
  final bool up;

  @override
  Widget build(BuildContext context) {
    if (history.length < 2) {
      return const SizedBox(height: 100);
    }
    final color = up ? const Color(0xFF10B981) : const Color(0xFFF43F5E);
    final spots = [
      for (int i = 0; i < history.length; i++)
        FlSpot(i.toDouble(), history[i]),
    ];
    final minY = history.reduce((a, b) => a < b ? a : b);
    final maxY = history.reduce((a, b) => a > b ? a : b);
    final pad = (maxY - minY) * 0.1 + 1;

    return SizedBox(
      height: 110,
      child: LineChart(
        LineChartData(
          minY: minY - pad,
          maxY: maxY + pad,
          gridData: const FlGridData(show: false),
          titlesData: const FlTitlesData(show: false),
          borderData: FlBorderData(show: false),
          lineTouchData: const LineTouchData(enabled: false),
          lineBarsData: [
            LineChartBarData(
              spots: spots,
              isCurved: true,
              color: color,
              barWidth: 2.2,
              dotData: const FlDotData(show: false),
              belowBarData: BarAreaData(
                show: true,
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [color.withOpacity(0.25), Colors.transparent],
                ),
              ),
            ),
          ],
        ),
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
    final sessColor =
        session >= 0 ? const Color(0xFF10B981) : const Color(0xFFF43F5E);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
      decoration: BoxDecoration(
        color: const Color(0xFF111827),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF1E293B)),
      ),
      child: Row(
        children: [
          _Metric(label: 'SESSION', value: '₹${session.toStringAsFixed(0)}', color: sessColor),
          _Metric(label: 'REALIZED', value: '₹${realized.toStringAsFixed(0)}'),
          _Metric(label: 'UNREALIZED', value: '₹${unrealized.toStringAsFixed(0)}'),
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
              fontSize: 9,
              letterSpacing: 1.5,
              color: Colors.white54,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: GoogleFonts.jetBrainsMono(
              fontSize: 14,
              fontWeight: FontWeight.bold,
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
                    fontSize: 11, letterSpacing: 2, color: Colors.white54)),
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
        const SizedBox(height: 4),
        _SliderRow(
          label: 'SL',
          value: sl,
          min: 5,
          max: 80,
          suffix: 'pts',
          color: const Color(0xFFF43F5E),
          onChanged: onSl,
        ),
        _SliderRow(
          label: 'TP',
          value: tp,
          min: 5,
          max: 120,
          suffix: 'pts',
          color: const Color(0xFF10B981),
          onChanged: onTp,
        ),
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
    required this.suffix,
    required this.color,
    required this.onChanged,
  });
  final String label;
  final double value;
  final double min;
  final double max;
  final String suffix;
  final Color color;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(
          width: 36,
          child: Text(label,
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 11, letterSpacing: 2, color: Colors.white54)),
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
              value: value,
              min: min,
              max: max,
              onChanged: onChanged,
            ),
          ),
        ),
        SizedBox(
          width: 64,
          child: Text(
            '${value.toStringAsFixed(0)} $suffix',
            textAlign: TextAlign.right,
            style: GoogleFonts.jetBrainsMono(
                fontSize: 12, color: Colors.white70),
          ),
        ),
      ],
    );
  }
}

class _TradeButton extends StatelessWidget {
  const _TradeButton({
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
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [color.withOpacity(0.9), color.withOpacity(0.6)],
          ),
          borderRadius: BorderRadius.circular(14),
          boxShadow: [
            BoxShadow(
              color: color.withOpacity(0.35),
              blurRadius: 14,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          children: [
            Icon(icon, color: Colors.white, size: 28),
            const SizedBox(height: 4),
            Text(
              label,
              style: GoogleFonts.jetBrainsMono(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                letterSpacing: 3,
                color: Colors.white,
              ),
            ),
            Text(
              sub,
              style: GoogleFonts.jetBrainsMono(
                fontSize: 10,
                color: Colors.white70,
              ),
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
      padding: const EdgeInsets.only(bottom: 6, left: 2),
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

class _EmptyHint extends StatelessWidget {
  const _EmptyHint(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.white10),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        text,
        style: GoogleFonts.jetBrainsMono(fontSize: 11, color: Colors.white38),
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
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
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
                Text(
                  '${pos.lots}L @ ${pos.entry.toStringAsFixed(1)}',
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 13, color: Colors.white),
                ),
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
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: color,
                ),
              ),
              const SizedBox(height: 2),
              InkWell(
                onTap: onClose,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.white12,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    'CLOSE',
                    style: GoogleFonts.jetBrainsMono(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
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
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 44,
            child: Text(
              isLong ? 'LONG' : 'SHORT',
              style: GoogleFonts.jetBrainsMono(
                fontSize: 10,
                color: isLong
                    ? const Color(0xFF10B981)
                    : const Color(0xFFF43F5E),
              ),
            ),
          ),
          Expanded(
            child: Text(
              '${trade.entry.toStringAsFixed(1)} → ${trade.exit.toStringAsFixed(1)}  ·  ${trade.reason}',
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 11, color: Colors.white70),
            ),
          ),
          Text(ts,
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 10, color: Colors.white38)),
          const SizedBox(width: 8),
          SizedBox(
            width: 64,
            child: Text(
              '₹${trade.netPnl.toStringAsFixed(0)}',
              textAlign: TextAlign.right,
              style: GoogleFonts.jetBrainsMono(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
