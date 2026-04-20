import 'package:flutter/material.dart';

import '../paper/market.dart';

class CandleChart extends StatelessWidget {
  const CandleChart({super.key, required this.candles, this.height = 220});

  final List<Candle> candles;
  final double height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: CustomPaint(
        painter: _CandlePainter(candles: candles),
        size: Size.infinite,
      ),
    );
  }
}

class _CandlePainter extends CustomPainter {
  _CandlePainter({required this.candles});
  final List<Candle> candles;

  static const _bull = Color(0xFF10B981);
  static const _bear = Color(0xFFF43F5E);
  static const _grid = Color(0x1AFFFFFF);
  static const _axis = Color(0xFF334155);

  @override
  void paint(Canvas canvas, Size size) {
    if (candles.length < 2) {
      _drawPlaceholder(canvas, size);
      return;
    }

    double minY = double.infinity;
    double maxY = -double.infinity;
    for (final c in candles) {
      if (c.low < minY) minY = c.low;
      if (c.high > maxY) maxY = c.high;
    }
    final pad = (maxY - minY) * 0.12 + 1;
    minY -= pad;
    maxY += pad;
    final range = maxY - minY;

    final w = size.width;
    final h = size.height - 16;
    final slot = w / candles.length;
    final bodyW = slot * 0.65;

    final gridPaint = Paint()
      ..color = _grid
      ..strokeWidth = 0.5;
    for (int i = 0; i <= 4; i++) {
      final y = h * i / 4;
      canvas.drawLine(Offset(0, y), Offset(w, y), gridPaint);
    }

    final axisLabel = TextPainter(textDirection: TextDirection.ltr);
    for (int i = 0; i <= 4; i++) {
      final y = h * i / 4;
      final price = maxY - range * i / 4;
      axisLabel.text = TextSpan(
        text: price.toStringAsFixed(0),
        style: const TextStyle(color: Colors.white38, fontSize: 9),
      );
      axisLabel.layout();
      axisLabel.paint(canvas, Offset(w - axisLabel.width - 2, y + 2));
    }

    for (int i = 0; i < candles.length; i++) {
      final c = candles[i];
      final x = slot * i + slot / 2;
      final openY = h * (maxY - c.open) / range;
      final closeY = h * (maxY - c.close) / range;
      final highY = h * (maxY - c.high) / range;
      final lowY = h * (maxY - c.low) / range;
      final color = c.bullish ? _bull : _bear;

      canvas.drawLine(
        Offset(x, highY),
        Offset(x, lowY),
        Paint()
          ..color = color
          ..strokeWidth = 1.1,
      );

      final bodyTop = openY < closeY ? openY : closeY;
      final bodyH = (openY - closeY).abs();
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x - bodyW / 2, bodyTop, bodyW, bodyH < 1 ? 1 : bodyH),
          const Radius.circular(1.5),
        ),
        Paint()..color = color,
      );
    }

    final last = candles.last;
    final lastY = h * (maxY - last.close) / range;
    final markerColor = last.bullish ? _bull : _bear;
    canvas.drawLine(
      Offset(0, lastY),
      Offset(w - 48, lastY),
      Paint()
        ..color = markerColor.withOpacity(0.35)
        ..strokeWidth = 0.8,
    );
    canvas.drawRect(
      Rect.fromLTWH(w - 46, lastY - 9, 44, 18),
      Paint()..color = markerColor,
    );
    final tp = TextPainter(
      text: TextSpan(
        text: last.close.toStringAsFixed(1),
        style: const TextStyle(
            color: Colors.black, fontWeight: FontWeight.bold, fontSize: 10),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, Offset(w - 44, lastY - tp.height / 2));

    canvas.drawLine(
      Offset(0, h + 0.5),
      Offset(w, h + 0.5),
      Paint()
        ..color = _axis
        ..strokeWidth = 0.5,
    );
  }

  void _drawPlaceholder(Canvas canvas, Size size) {
    final tp = TextPainter(
      text: const TextSpan(
        text: 'warming up feed…',
        style: TextStyle(color: Colors.white38, fontSize: 11),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(
      canvas,
      Offset((size.width - tp.width) / 2, (size.height - tp.height) / 2),
    );
  }

  @override
  bool shouldRepaint(covariant _CandlePainter old) =>
      !identical(old.candles, candles) || old.candles.length != candles.length;
}
