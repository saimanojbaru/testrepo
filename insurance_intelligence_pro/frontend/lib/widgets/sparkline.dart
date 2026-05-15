import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

class Sparkline extends StatelessWidget {
  final List<double> data;
  final Color? color;
  final double height;
  final bool fill;

  const Sparkline({
    super.key,
    required this.data,
    this.color,
    this.height = 36,
    this.fill = true,
  });

  @override
  Widget build(BuildContext context) {
    if (data.isEmpty) {
      return SizedBox(height: height);
    }
    final lineColor = color ?? AppColors.accent;
    final spots = <FlSpot>[
      for (var i = 0; i < data.length; i++) FlSpot(i.toDouble(), data[i])
    ];
    final minY = data.reduce((a, b) => a < b ? a : b);
    final maxY = data.reduce((a, b) => a > b ? a : b);
    final pad = (maxY - minY).abs() * 0.2 + 0.001;

    return SizedBox(
      height: height,
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
              curveSmoothness: 0.32,
              color: lineColor,
              barWidth: 2.4,
              dotData: const FlDotData(show: false),
              belowBarData: fill
                  ? BarAreaData(
                      show: true,
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          lineColor.withValues(alpha: 0.32),
                          lineColor.withValues(alpha: 0.0),
                        ],
                      ),
                    )
                  : BarAreaData(show: false),
            ),
          ],
        ),
        duration: const Duration(milliseconds: 600),
        curve: Curves.easeOutCubic,
      ),
    );
  }
}
