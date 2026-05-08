import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Premium "frosted-glass" card used as the primary surface throughout
/// the app. Soft gradient background, hairline border, and a subtle
/// inner highlight for that Bloomberg-terminal feel.
class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final double radius;
  final Gradient? gradient;
  final Color? borderColor;
  final double borderWidth;
  final List<BoxShadow>? shadows;
  final VoidCallback? onTap;

  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
    this.margin = EdgeInsets.zero,
    this.radius = 22,
    this.gradient,
    this.borderColor,
    this.borderWidth = 1,
    this.shadows,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final card = Container(
      padding: padding,
      decoration: BoxDecoration(
        gradient: gradient ?? AppColors.cardGradient,
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(
          color: borderColor ?? AppColors.border,
          width: borderWidth,
        ),
        boxShadow: shadows ??
            [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.35),
                blurRadius: 24,
                offset: const Offset(0, 12),
              ),
            ],
      ),
      child: child,
    );

    return Padding(
      padding: margin,
      child: onTap == null
          ? card
          : Material(
              color: Colors.transparent,
              borderRadius: BorderRadius.circular(radius),
              child: InkWell(
                borderRadius: BorderRadius.circular(radius),
                onTap: onTap,
                splashColor: AppColors.accent.withValues(alpha: 0.07),
                highlightColor: AppColors.accent.withValues(alpha: 0.05),
                child: card,
              ),
            ),
    );
  }
}
