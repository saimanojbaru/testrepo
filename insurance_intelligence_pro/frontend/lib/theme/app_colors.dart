import 'package:flutter/material.dart';

/// Bloomberg-lite dark palette. Deep navy base, cyan accent,
/// directional reds and greens with high contrast on the surface.
class AppColors {
  AppColors._();

  // Surfaces
  static const Color background = Color(0xFF050B18);
  static const Color surface = Color(0xFF0B1426);
  static const Color surfaceElevated = Color(0xFF111E36);
  static const Color surfaceHigh = Color(0xFF1A2A48);
  static const Color border = Color(0xFF1F2D49);
  static const Color divider = Color(0xFF182338);

  // Brand
  static const Color primary = Color(0xFF1F4FFF);   // deep blue
  static const Color primarySoft = Color(0xFF334BFF);
  static const Color accent = Color(0xFF00E0C7);    // cyan / teal
  static const Color accentSoft = Color(0xFF50F0DE);

  // Status
  static const Color positive = Color(0xFF26E07F);
  static const Color positiveSoft = Color(0xFF52F0A0);
  static const Color warning = Color(0xFFFFB547);
  static const Color negative = Color(0xFFFF4D6D);
  static const Color negativeSoft = Color(0xFFFF6E89);

  // Text
  static const Color textPrimary = Color(0xFFE8EEFC);
  static const Color textSecondary = Color(0xFF8B9BBE);
  static const Color textMuted = Color(0xFF566688);

  // Charts
  static const List<Color> seriesPalette = <Color>[
    Color(0xFF00E0C7),
    Color(0xFF1F4FFF),
    Color(0xFFFFB547),
    Color(0xFF26E07F),
    Color(0xFFFF4D6D),
    Color(0xFFB877FF),
  ];

  static LinearGradient get primaryGradient => const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF1F4FFF), Color(0xFF00E0C7)],
      );

  static LinearGradient get cardGradient => const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF111E36), Color(0xFF0B1426)],
      );

  static LinearGradient get glowGradient => const LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [Color(0x441F4FFF), Color(0x0000E0C7)],
      );

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
