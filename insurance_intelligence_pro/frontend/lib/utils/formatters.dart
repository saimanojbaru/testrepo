import 'package:intl/intl.dart';

class Formatters {
  Formatters._();

  static final NumberFormat _percent =
      NumberFormat.decimalPattern('en_US')..maximumFractionDigits = 2;
  static final NumberFormat _signed =
      NumberFormat.decimalPattern('en_US')..maximumFractionDigits = 2;
  static final NumberFormat _compact = NumberFormat.compactSimpleCurrency();

  static String pct(num? value, {int decimals = 1}) {
    if (value == null) return '–';
    final v = value.toDouble();
    final f = NumberFormat.decimalPattern('en_US')
      ..minimumFractionDigits = decimals
      ..maximumFractionDigits = decimals;
    return '${f.format(v)}%';
  }

  static String num1(num? value, {int decimals = 2}) {
    if (value == null) return '–';
    final f = NumberFormat.decimalPattern('en_US')
      ..minimumFractionDigits = decimals
      ..maximumFractionDigits = decimals;
    return f.format(value);
  }

  static String signedPct(num? value) {
    if (value == null) return '–';
    final v = value.toDouble();
    final sign = v > 0 ? '+' : (v < 0 ? '' : '');
    return '$sign${_signed.format(v)}%';
  }

  static String currencyM(num? millions) {
    if (millions == null) return '–';
    final value = millions.toDouble() * 1e6;
    return _compact.format(value);
  }

  static String date(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    try {
      final dt = DateTime.parse(iso).toLocal();
      return DateFormat('MMM d, yyyy').format(dt);
    } catch (_) {
      return iso;
    }
  }

  /// Clamp 0-100 health score to a band label.
  static String scoreBand(double score) {
    if (score >= 80) return 'STRONG';
    if (score >= 65) return 'STABLE';
    if (score >= 45) return 'WATCH';
    return 'WEAK';
  }
}
