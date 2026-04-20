import 'dart:math';
import 'package:flutter/material.dart';

class AppLogo extends StatelessWidget {
  const AppLogo({super.key, this.size = 120, this.glow = true});
  final double size;
  final bool glow;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _LogoPainter(glow: glow),
      ),
    );
  }
}

class _LogoPainter extends CustomPainter {
  _LogoPainter({required this.glow});
  final bool glow;

  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width / 2, size.height / 2);
    final r = size.width / 2;

    if (glow) {
      final glowPaint = Paint()
        ..shader = RadialGradient(
          colors: [
            const Color(0xFF38BDF8).withOpacity(0.35),
            Colors.transparent,
          ],
        ).createShader(Rect.fromCircle(center: c, radius: r));
      canvas.drawCircle(c, r, glowPaint);
    }

    final ringRect = Rect.fromCircle(center: c, radius: r * 0.82);
    final ringPaint = Paint()
      ..shader = const SweepGradient(
        colors: [
          Color(0xFF0EA5E9),
          Color(0xFF22D3EE),
          Color(0xFF10B981),
          Color(0xFF0EA5E9),
        ],
      ).createShader(ringRect)
      ..style = PaintingStyle.stroke
      ..strokeWidth = r * 0.08
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(ringRect, -pi / 2, 2 * pi * 0.92, false, ringPaint);

    final innerR = r * 0.62;
    final innerPaint = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
      ).createShader(Rect.fromCircle(center: c, radius: innerR));
    canvas.drawCircle(c, innerR, innerPaint);

    _drawCandles(canvas, c, innerR);

    final arrowPaint = Paint()
      ..color = const Color(0xFF22D3EE)
      ..style = PaintingStyle.stroke
      ..strokeWidth = r * 0.06
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final path = Path()
      ..moveTo(c.dx - innerR * 0.55, c.dy + innerR * 0.35)
      ..lineTo(c.dx - innerR * 0.15, c.dy - innerR * 0.05)
      ..lineTo(c.dx + innerR * 0.25, c.dy + innerR * 0.15)
      ..lineTo(c.dx + innerR * 0.7, c.dy - innerR * 0.45);
    canvas.drawPath(path, arrowPaint);

    final headSize = r * 0.11;
    final head = Path()
      ..moveTo(c.dx + innerR * 0.7, c.dy - innerR * 0.45)
      ..lineTo(c.dx + innerR * 0.7 - headSize, c.dy - innerR * 0.45 + headSize * 0.2)
      ..lineTo(c.dx + innerR * 0.7 - headSize * 0.2, c.dy - innerR * 0.45 + headSize)
      ..close();
    canvas.drawPath(
      head,
      Paint()..color = const Color(0xFF22D3EE),
    );
  }

  void _drawCandles(Canvas canvas, Offset c, double innerR) {
    final bars = [
      (-0.55, 0.30, 0.55, const Color(0xFF10B981)),
      (-0.18, 0.18, 0.80, const Color(0xFFF43F5E)),
      (0.20, 0.22, 0.95, const Color(0xFF10B981)),
      (0.58, 0.14, 0.42, const Color(0xFF10B981)),
    ];
    for (final b in bars) {
      final x = c.dx + innerR * b.$1;
      final h = innerR * b.$2;
      final yOffset = innerR * (b.$3 - 0.5) * 0.6;
      final paint = Paint()
        ..color = b.$4.withOpacity(0.18)
        ..style = PaintingStyle.fill;
      final rect = Rect.fromCenter(
        center: Offset(x, c.dy + yOffset),
        width: innerR * 0.18,
        height: h,
      );
      canvas.drawRRect(
        RRect.fromRectAndRadius(rect, const Radius.circular(2)),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _LogoPainter old) => old.glow != glow;
}
