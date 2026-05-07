import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../models/company.dart';
import '../theme/app_colors.dart';

class TrendChart extends StatelessWidget {
  final List<FinancialSeries> series;
  final List<int> visibleIndices;
  final String unitLabel;

  const TrendChart({
    super.key,
    required this.series,
    required this.visibleIndices,
    this.unitLabel = 'USD M',
  });

  @override
  Widget build(BuildContext context) {
    if (series.isEmpty) {
      return SizedBox(
        height: 220,
        child: Center(
          child: Text('No history available',
              style: TextStyle(color: AppColors.textMuted)),
        ),
      );
    }
    final visible = visibleIndices
        .where((i) => i < series.length)
        .map((i) => series[i])
        .toList();
    final allValues = visible.expand((s) => s.points.map((p) => p.value));
    if (allValues.isEmpty) {
      return const SizedBox(height: 220);
    }
    final minY = allValues.reduce((a, b) => a < b ? a : b);
    final maxY = allValues.reduce((a, b) => a > b ? a : b);
    final pad = (maxY - minY).abs() * 0.15 + 1;
    final fys = visible.first.points.map((p) => p.fy.toInt()).toList();

    return SizedBox(
      height: 230,
      child: LineChart(
        LineChartData(
          minY: (minY - pad).clamp(double.negativeInfinity, double.infinity),
          maxY: maxY + pad,
          gridData: FlGridData(
            show: true,
            drawVerticalLine: false,
            getDrawingHorizontalLine: (_) => FlLine(
              color: AppColors.divider.withOpacity(0.7),
              strokeWidth: 0.6,
              dashArray: [4, 6],
            ),
          ),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            leftTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 42,
                getTitlesWidget: (value, _) {
                  return Text(
                    _abbr(value),
                    style: TextStyle(color: AppColors.textMuted, fontSize: 10),
                  );
                },
              ),
            ),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                interval: 1,
                getTitlesWidget: (value, _) {
                  if (!fys.contains(value.toInt())) return const SizedBox.shrink();
                  return Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      'FY${value.toInt() % 100}',
                      style: TextStyle(color: AppColors.textMuted, fontSize: 10),
                    ),
                  );
                },
              ),
            ),
          ),
          lineTouchData: LineTouchData(
            handleBuiltInTouches: true,
            touchTooltipData: LineTouchTooltipData(
              getTooltipColor: (_) => AppColors.surfaceHigh,
              tooltipBorder: BorderSide(color: AppColors.border),
              getTooltipItems: (spots) => spots.map((s) {
                final s2 = visible[s.barIndex];
                return LineTooltipItem(
                  '${s2.label}\n${_abbr(s.y)}',
                  TextStyle(
                    color: AppColors.seriesPalette[
                        s.barIndex % AppColors.seriesPalette.length],
                    fontWeight: FontWeight.w600,
                    fontSize: 12,
                  ),
                );
              }).toList(),
            ),
          ),
          lineBarsData: [
            for (var i = 0; i < visible.length; i++)
              _line(visible[i], i),
          ],
        ),
        duration: const Duration(milliseconds: 700),
        curve: Curves.easeOutCubic,
      ),
    );
  }

  LineChartBarData _line(FinancialSeries s, int i) {
    final color = AppColors.seriesPalette[i % AppColors.seriesPalette.length];
    return LineChartBarData(
      spots: s.points
          .map((p) => FlSpot(p.fy, p.value))
          .toList(),
      isCurved: true,
      curveSmoothness: 0.32,
      color: color,
      barWidth: 2.4,
      dotData: FlDotData(
        show: true,
        getDotPainter: (spot, _, __, ___) => FlDotCirclePainter(
          color: color,
          strokeColor: AppColors.background,
          strokeWidth: 2,
          radius: 3.5,
        ),
      ),
      belowBarData: BarAreaData(
        show: i == 0,
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [color.withOpacity(0.22), color.withOpacity(0.0)],
        ),
      ),
    );
  }

  String _abbr(double v) {
    final abs = v.abs();
    if (abs >= 1e6) return '${(v / 1e6).toStringAsFixed(1)}T';
    if (abs >= 1e3) return '${(v / 1e3).toStringAsFixed(1)}B';
    return '${v.toStringAsFixed(0)}M';
  }
}
