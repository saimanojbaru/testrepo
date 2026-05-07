import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../models/kpi.dart';
import '../theme/app_colors.dart';

class RiskRadarChart extends StatelessWidget {
  final RiskRadar radar;
  const RiskRadarChart({super.key, required this.radar});

  @override
  Widget build(BuildContext context) {
    if (radar.factors.isEmpty) {
      return SizedBox(
        height: 180,
        child: Center(
          child: Text(
            'Risk radar unavailable',
            style: TextStyle(color: AppColors.textMuted),
          ),
        ),
      );
    }
    return AspectRatio(
      aspectRatio: 1.2,
      child: RadarChart(
        RadarChartData(
          radarBorderData: BorderSide(color: AppColors.border, width: 1),
          radarShape: RadarShape.polygon,
          tickCount: 4,
          ticksTextStyle: const TextStyle(color: Colors.transparent, fontSize: 10),
          gridBorderData: BorderSide(color: AppColors.divider, width: 0.7),
          tickBorderData: BorderSide(color: AppColors.divider, width: 0.7),
          getTitle: (index, _) {
            final f = radar.factors[index];
            return RadarChartTitle(text: f.label, angle: 0);
          },
          titleTextStyle: TextStyle(
            color: AppColors.textSecondary,
            fontSize: 11,
            fontWeight: FontWeight.w600,
          ),
          dataSets: [
            RadarDataSet(
              fillColor: AppColors.accent.withOpacity(0.18),
              borderColor: AppColors.accent,
              borderWidth: 2,
              entryRadius: 4,
              dataEntries: radar.factors
                  .map((f) => RadarEntry(value: f.score))
                  .toList(),
            ),
          ],
          radarBackgroundColor: Colors.transparent,
        ),
        swapAnimationDuration: const Duration(milliseconds: 700),
        swapAnimationCurve: Curves.easeOutCubic,
      ),
    );
  }
}
