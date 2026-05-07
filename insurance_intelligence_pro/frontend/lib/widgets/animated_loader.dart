import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';

import '../theme/app_colors.dart';

class AnimatedLoader extends StatelessWidget {
  final String? label;
  const AnimatedLoader({super.key, this.label});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 60,
            height: 60,
            child: Stack(
              alignment: Alignment.center,
              children: [
                CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor:
                      AlwaysStoppedAnimation(AppColors.accent.withOpacity(0.9)),
                ),
                Container(
                  width: 14,
                  height: 14,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: AppColors.primaryGradient,
                  ),
                ),
              ],
            ),
          ),
          if (label != null) ...[
            const SizedBox(height: 12),
            Text(
              label!,
              style: TextStyle(
                color: AppColors.textSecondary,
                letterSpacing: 0.4,
                fontSize: 13,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class ShimmerCard extends StatelessWidget {
  final double height;
  final double radius;
  const ShimmerCard({super.key, this.height = 96, this.radius = 22});

  @override
  Widget build(BuildContext context) {
    return Shimmer.fromColors(
      baseColor: AppColors.surface,
      highlightColor: AppColors.surfaceElevated,
      period: const Duration(milliseconds: 1400),
      child: Container(
        height: height,
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(radius),
        ),
      ),
    );
  }
}

class ShimmerList extends StatelessWidget {
  final int itemCount;
  final double itemHeight;
  const ShimmerList({super.key, this.itemCount = 4, this.itemHeight = 84});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: List.generate(
        itemCount,
        (i) => Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: ShimmerCard(height: itemHeight),
        ),
      ),
    );
  }
}
