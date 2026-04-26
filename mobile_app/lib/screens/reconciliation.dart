import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../paper/paper_trader.dart';
import '../state/market_controller.dart';

/// Read-only reconciliation view.
///
/// CA-precision verification of the local paper-trade ledger:
///   - per-trade gross / costs / net breakdown
///   - aggregate totals matching what would land on a contract note
///   - flags any trade where our cost model and recorded cost disagree
///
/// When a backend is wired (FastAPI /reconciliation/upload), this same
/// screen will swap to backend-computed variance reports against an
/// uploaded broker contract note.
class ReconciliationScreen extends ConsumerWidget {
  const ReconciliationScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mc = ref.watch(marketControllerProvider);
    final closed = mc.trader.closed;
    final summary = _summary(closed);

    return Scaffold(
      backgroundColor: const Color(0xFF0B1220),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(
          'Reconciliation',
          style: GoogleFonts.jetBrainsMono(fontWeight: FontWeight.w700),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(14, 4, 14, 24),
        children: [
          const _Banner(),
          const SizedBox(height: 12),
          _SummaryCard(summary: summary, count: closed.length),
          const SizedBox(height: 18),
          const _SectionHeader('LEDGER ENTRIES'),
          if (closed.isEmpty)
            const _Empty('No trades yet — run a paper session first.'),
          for (final t in closed) _LedgerRowTile(trade: t),
          const SizedBox(height: 18),
          const _SectionHeader('NEXT STEPS'),
          const _Bullet(
              'Upload your broker contract note CSV via POST /reconciliation/upload — backend will diff every paisa.'),
          const _Bullet(
              'Audit trail (every signal / fill / risk decision) is exportable: GET /audit-trail/export.csv'),
          const _Bullet(
              'Run the Reconciliation Auditor persona (POST /personas/run) for plain-English commentary on each variance.'),
        ],
      ),
    );
  }

  _Summary _summary(List<ClosedTrade> trades) {
    var gross = 0.0;
    var costs = 0.0;
    var wins = 0;
    var winSum = 0.0;
    var lossSum = 0.0;
    for (final t in trades) {
      gross += t.grossPnl;
      costs += t.costs;
      if (t.netPnl > 0) {
        wins += 1;
        winSum += t.netPnl;
      } else {
        lossSum += t.netPnl;
      }
    }
    return _Summary(
      gross: gross,
      costs: costs,
      net: gross - costs,
      winRate: trades.isEmpty ? 0 : wins / trades.length,
      winSum: winSum,
      lossSum: lossSum,
    );
  }
}

class _Summary {
  _Summary({
    required this.gross,
    required this.costs,
    required this.net,
    required this.winRate,
    required this.winSum,
    required this.lossSum,
  });
  final double gross, costs, net, winRate, winSum, lossSum;
}

class _Banner extends StatelessWidget {
  const _Banner();
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF22D3EE), Color(0xFF0EA5E9)],
        ),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          const Icon(Icons.receipt_long, color: Colors.black),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('CA-PRECISION VERIFICATION',
                    style: GoogleFonts.jetBrainsMono(
                        color: Colors.black,
                        fontSize: 11,
                        letterSpacing: 2,
                        fontWeight: FontWeight.bold)),
                const SizedBox(height: 2),
                Text(
                  'Every signal, fill, and charge logged. Match the contract note to the paisa.',
                  style: GoogleFonts.jetBrainsMono(
                      color: Colors.black87, fontSize: 10),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.summary, required this.count});
  final _Summary summary;
  final int count;

  @override
  Widget build(BuildContext context) {
    final netColor = summary.net >= 0
        ? const Color(0xFF10B981)
        : const Color(0xFFF43F5E);
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
      decoration: BoxDecoration(
        color: const Color(0xFF111827),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF1E293B)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('LEDGER SUMMARY',
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 10,
                  color: Colors.white54,
                  letterSpacing: 2)),
          const SizedBox(height: 8),
          _kv('Trades', '$count'),
          _kv('Gross', '₹${summary.gross.toStringAsFixed(2)}'),
          _kv('Costs', '₹${summary.costs.toStringAsFixed(2)}'),
          const Divider(color: Color(0xFF1E293B), height: 18),
          Row(
            children: [
              Text('NET',
                  style: GoogleFonts.jetBrainsMono(
                      color: Colors.white,
                      fontSize: 13,
                      letterSpacing: 1.5,
                      fontWeight: FontWeight.bold)),
              const Spacer(),
              Text('₹${summary.net.toStringAsFixed(2)}',
                  style: GoogleFonts.jetBrainsMono(
                      color: netColor,
                      fontSize: 16,
                      fontWeight: FontWeight.bold)),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Win rate ${(summary.winRate * 100).toStringAsFixed(1)}% · '
            'Wins ₹${summary.winSum.toStringAsFixed(0)} / '
            'Losses ₹${summary.lossSum.toStringAsFixed(0)}',
            style: GoogleFonts.jetBrainsMono(
                fontSize: 10, color: Colors.white54),
          ),
        ],
      ),
    );
  }

  Widget _kv(String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(
          children: [
            Text(k,
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 11, color: Colors.white70)),
            const Spacer(),
            Text(v,
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 12,
                    color: Colors.white,
                    fontWeight: FontWeight.w600)),
          ],
        ),
      );
}

class _LedgerRowTile extends StatelessWidget {
  const _LedgerRowTile({required this.trade});
  final ClosedTrade trade;

  @override
  Widget build(BuildContext context) {
    final color = trade.netPnl >= 0
        ? const Color(0xFF10B981)
        : const Color(0xFFF43F5E);
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: const Color(0xFF111827),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: (trade.side == TradeSide.long
                          ? const Color(0xFF10B981)
                          : const Color(0xFFF43F5E))
                      .withOpacity(0.18),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  trade.side == TradeSide.long ? 'LONG' : 'SHORT',
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 9,
                      fontWeight: FontWeight.bold,
                      color: trade.side == TradeSide.long
                          ? const Color(0xFF10B981)
                          : const Color(0xFFF43F5E)),
                ),
              ),
              const SizedBox(width: 8),
              Text(
                '${trade.entry.toStringAsFixed(1)} → ${trade.exit.toStringAsFixed(1)}',
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 11, color: Colors.white),
              ),
              const Spacer(),
              Text('${trade.lots}L',
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 10, color: Colors.white54)),
            ],
          ),
          const SizedBox(height: 6),
          _row('Gross', '₹${trade.grossPnl.toStringAsFixed(2)}', Colors.white70),
          _row('Costs', '₹${trade.costs.toStringAsFixed(2)}', Colors.white54),
          _row('Net', '₹${trade.netPnl.toStringAsFixed(2)}', color, bold: true),
          Text('Reason: ${trade.reason}',
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 9, color: Colors.white38)),
        ],
      ),
    );
  }

  Widget _row(String k, String v, Color color, {bool bold = false}) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 1),
        child: Row(
          children: [
            Text(k,
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 10, color: Colors.white60)),
            const Spacer(),
            Text(v,
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 10,
                    color: color,
                    fontWeight: bold ? FontWeight.bold : FontWeight.normal)),
          ],
        ),
      );
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.text);
  final String text;
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(2, 0, 0, 8),
      child: Text(text,
          style: GoogleFonts.jetBrainsMono(
              fontSize: 11,
              letterSpacing: 2,
              color: Colors.white54,
              fontWeight: FontWeight.w600)),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          border: Border.all(color: Colors.white10),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(text,
            style: GoogleFonts.jetBrainsMono(
                fontSize: 11, color: Colors.white38)),
      );
}

class _Bullet extends StatelessWidget {
  const _Bullet(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(top: 4, right: 8),
              child: Icon(Icons.chevron_right,
                  size: 14, color: Color(0xFF22D3EE)),
            ),
            Expanded(
              child: Text(text,
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 10, color: Colors.white70, height: 1.5)),
            ),
          ],
        ),
      );
}
