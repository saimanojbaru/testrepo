import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../paper/market.dart';

class WatchlistStrip extends StatelessWidget {
  const WatchlistStrip({
    super.key,
    required this.prices,
    required this.previousPrices,
    required this.selected,
    required this.onSelect,
  });

  final Map<String, double> prices;
  final Map<String, double> previousPrices;
  final String selected;
  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 72,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        itemCount: Instrument.all.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (ctx, i) {
          final inst = Instrument.all[i];
          final px = prices[inst.symbol] ?? inst.basePrice;
          final prev = previousPrices[inst.symbol] ?? inst.basePrice;
          final change = px - inst.basePrice;
          final pct = change / inst.basePrice * 100;
          final tick = px - prev;
          final up = change >= 0;
          final sel = inst.symbol == selected;
          final color = up ? const Color(0xFF10B981) : const Color(0xFFF43F5E);
          return GestureDetector(
            onTap: () => onSelect(inst.symbol),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 130,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: sel ? const Color(0xFF1E293B) : const Color(0xFF111827),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: sel ? color : Colors.white10,
                  width: sel ? 1.4 : 1,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Row(
                    children: [
                      Text(
                        inst.symbol,
                        style: GoogleFonts.jetBrainsMono(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                          letterSpacing: 1,
                        ),
                      ),
                      const Spacer(),
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 180),
                        width: 6,
                        height: 6,
                        decoration: BoxDecoration(
                          color: tick.abs() < 0.001
                              ? Colors.white24
                              : (tick > 0
                                  ? const Color(0xFF10B981)
                                  : const Color(0xFFF43F5E)),
                          shape: BoxShape.circle,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    px.toStringAsFixed(2),
                    style: GoogleFonts.jetBrainsMono(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: Colors.white,
                    ),
                  ),
                  Text(
                    '${up ? '+' : ''}${change.toStringAsFixed(1)}  ${pct.toStringAsFixed(2)}%',
                    style: GoogleFonts.jetBrainsMono(
                      fontSize: 10,
                      color: color,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
