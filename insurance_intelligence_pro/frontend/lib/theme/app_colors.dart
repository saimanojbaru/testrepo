import 'package:flutter/material.dart';

/// **Midnight Sapphire + Electric Cyan** — premium glassmorphic palette.
///
/// Deep navy base, vibrant cyan→blue→purple primary gradient, subtle
/// glow-friendly surface tints. Designed for heavy gradient + glow
/// usage with crisp readability in dark mode.
class AppColors {
  AppColors._();

  // ─── Surfaces ────────────────────────────────────────────────────────────
  static const Color background = Color(0xFF0A1428);       // Midnight sapphire
  static const Color surface = Color(0xFF131F38);          // Glass base
  static const Color surfaceElevated = Color(0xFF1E2937);  // Card layer
  static const Color surfaceHigh = Color(0xFF233151);      // Hover / pill
  static const Color border = Color(0xFF263554);
  static const Color divider = Color(0xFF1B2640);

  // ─── Brand ───────────────────────────────────────────────────────────────
  static const Color primary = Color(0xFF00B4FF);          // Electric blue
  static const Color primarySoft = Color(0xFF38C8FF);
  static const Color accent = Color(0xFF00F5FF);           // Cyan (primary CTA)
  static const Color accentSoft = Color(0xFF7AF7FF);
  static const Color violet = Color(0xFF7C3AED);           // Purple highlight
  static const Color violetSoft = Color(0xFFA678FF);

  // ─── Status ──────────────────────────────────────────────────────────────
  static const Color positive = Color(0xFF14F195);         // Neon teal
  static const Color positiveSoft = Color(0xFF52F0A0);
  static const Color warning = Color(0xFFFFB547);          // Amber
  static const Color negative = Color(0xFFFF4D6D);         // Coral red
  static const Color negativeSoft = Color(0xFFFF6E89);

  // ─── Text ────────────────────────────────────────────────────────────────
  static const Color textPrimary = Color(0xFFF1F5FF);      // Near-white w/ blue tint
  static const Color textSecondary = Color(0xFFA0B0D0);
  static const Color textMuted = Color(0xFF6A7CA0);

  // ─── Series palette (charts) ─────────────────────────────────────────────
  static const List<Color> seriesPalette = <Color>[
    Color(0xFF00F5FF), // cyan
    Color(0xFF00B4FF), // electric blue
    Color(0xFF7C3AED), // purple
    Color(0xFF14F195), // teal
    Color(0xFFFFB547), // amber
    Color(0xFFFF4D6D), // coral
  ];

  // ─── Gradients ───────────────────────────────────────────────────────────
  /// Primary CTA gradient: cyan → electric blue → purple.
  static LinearGradient get primaryGradient => const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF00F5FF), Color(0xFF00B4FF), Color(0xFF7C3AED)],
        stops: [0.0, 0.55, 1.0],
      );

  /// Cool card gradient — used as the default card fill.
  static LinearGradient get cardGradient => const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xCC1E2937), Color(0xCC131F38)],
      );

  /// Hero / spotlight gradient used on hero cards.
  static LinearGradient get heroGradient => const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF1A2A4D), Color(0xFF0F1A33)],
      );

  /// Ambient page-background glow.
  static LinearGradient get ambientGradient => const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0x33007AFF), Color(0x227C3AED)],
      );

  /// Quiz "danger" gradient for wrong answers / timer drain.
  static LinearGradient get dangerGradient => const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFFFF4D6D), Color(0xFFB23A8C)],
      );

  /// Glow halo for premium interactive elements.
  static List<BoxShadow> glow(Color base, {double radius = 18}) => [
        BoxShadow(
          color: base.withValues(alpha: 0.45),
          blurRadius: radius,
          spreadRadius: 1,
        ),
      ];

  /// Maps a status string to a brand color.
  static Color statusColor(String status) {
    switch (status) {
      case 'good':
      case 'positive':
      case 'green':
        return positive;
      case 'warn':
      case 'warning':
      case 'yellow':
        return warning;
      case 'bad':
      case 'negative':
      case 'red':
        return negative;
      default:
        return accent;
    }
  }
}

