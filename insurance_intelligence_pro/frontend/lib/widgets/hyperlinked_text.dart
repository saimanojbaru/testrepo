import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../data/local_data.dart';
import '../models/standard.dart';
import '../screens/standard_detail_screen.dart';
import '../theme/app_colors.dart';

/// Universal linker — wraps any text and rewrites mentions of
/// SSAP / ASC / AS / FAS standard numbers into tappable internal
/// links that push into [StandardDetailScreen].
///
/// Matches (case-insensitive):
///   - SSAP 2R, SSAP No. 51R, SSAP-2R
///   - ASC 944, ASC 944-30, ASC 944-40-30-19A, ASC 230, ASC 326
///   - AS 1001, AS 2201, AS 2501
///   - FAS 60, FAS 97, FAS 113, FAS 120, FAS 133  (mapped → ASC 944)
///
/// Unmatched text is rendered with the supplied [style]. Matched
/// references are rendered in the accent blue with underline.
class HyperlinkedText extends StatelessWidget {
  final String text;
  final TextStyle? style;
  final int? maxLines;
  final TextOverflow overflow;

  const HyperlinkedText(
    this.text, {
    super.key,
    this.style,
    this.maxLines,
    this.overflow = TextOverflow.clip,
  });

  // One regex with three alternations — captured group identifies type.
  static final RegExp _pattern = RegExp(
    r'(SSAP(?:\s*No\.?)?[\s-]*\d+R?)'
    r'|(ASC\s+\d+(?:-\d+){0,3}[A-Z]?)'
    r'|(AS\s+\d{3,4})'
    r'|(FAS\s+\d+)',
    caseSensitive: false,
  );

  @override
  Widget build(BuildContext context) {
    final base = style ??
        const TextStyle(
          color: AppColors.textSecondary,
          fontSize: 13,
          height: 1.55,
        );
    final accent = base.copyWith(
      color: AppColors.accent,
      fontWeight: FontWeight.w700,
      decoration: TextDecoration.underline,
      decorationColor: AppColors.accent.withValues(alpha: 0.6),
    );

    final spans = <InlineSpan>[];
    int idx = 0;
    for (final m in _pattern.allMatches(text)) {
      if (m.start > idx) {
        spans.add(TextSpan(text: text.substring(idx, m.start), style: base));
      }
      final raw = m.group(0)!;
      final target = HyperlinkResolver.resolve(raw);
      if (target == null) {
        // No matching standard in our catalog — keep as plain text.
        spans.add(TextSpan(text: raw, style: base));
      } else {
        spans.add(TextSpan(
          text: raw,
          style: accent,
          recognizer: TapGestureRecognizer()
            ..onTap = () {
              Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => StandardDetailScreen(standard: target)));
            },
        ));
      }
      idx = m.end;
    }
    if (idx < text.length) {
      spans.add(TextSpan(text: text.substring(idx), style: base));
    }

    return Text.rich(
      TextSpan(children: spans),
      maxLines: maxLines,
      overflow: overflow,
    );
  }
}

/// Resolves a raw "SSAP 2R" / "ASC 944-30" / "AS 2201" / "FAS 60"
/// reference to a [Standard] in our local catalog.
class HyperlinkResolver {
  HyperlinkResolver._();

  static Standard? resolve(String raw) {
    final cleaned = raw.replaceAll(RegExp(r'\s+'), ' ').trim().toUpperCase();
    if (cleaned.startsWith('SSAP')) {
      final num = cleaned
          .replaceFirst('SSAP', '')
          .replaceFirst('NO.', '')
          .replaceFirst('NO ', '')
          .replaceAll('-', '')
          .trim();
      return _findInCatalog('SSAP', num);
    }
    if (cleaned.startsWith('ASC ')) {
      var num = cleaned.substring(4).trim();
      // ASC 230 (cash flows), ASC 326 (CECL), ASC 815 (derivatives) etc
      // map to a closely-related ASC 944 sub-topic where applicable.
      if (num.startsWith('944')) {
        return _findInCatalog('ASC944', num);
      }
      // Map common cross-references → our nearest ASC 944 sub-topic
      const crossRef = {
        '230': 'ASC-944-10',
        '321': 'ASC-944-825',
        '320': 'ASC-944-825',
        '326': 'ASC-944-310',
        '805': 'ASC-944-805',
        '815': 'ASC-944-815',
        '820': 'ASC-944-820',
        '825': 'ASC-944-825',
        '740': 'ASC-944-740',
        '710': 'ASC-944-405',
        '842': 'ASC-944-440',
      };
      final root = num.split('-').first;
      if (crossRef.containsKey(root)) {
        return _findById('ASC944', crossRef[root]!);
      }
      return null;
    }
    if (cleaned.startsWith('AS ')) {
      final num = cleaned.substring(3).trim();
      return _findInCatalog('PCAOB', 'AS $num');
    }
    if (cleaned.startsWith('FAS ')) {
      const fasToAsc = {
        '60': 'ASC-944-605',
        '97': 'ASC-944-405',
        '113': 'ASC-944-20',
        '120': 'ASC-944-50',
        '133': 'ASC-944-815',
        '141': 'ASC-944-805',
      };
      final num = cleaned.substring(4).trim();
      final id = fasToAsc[num];
      if (id != null) return _findById('ASC944', id);
    }
    return null;
  }

  static Standard? _findInCatalog(String framework, String number) {
    try {
      final cat = LocalData.instance.catalogFor(framework);
      final upper = number.toUpperCase();
      for (final s in cat.standards) {
        if (s.number.toUpperCase().replaceAll(' ', '') ==
            upper.replaceAll(' ', '')) {
          return s;
        }
      }
    } catch (_) {}
    return null;
  }

  static Standard? _findById(String framework, String id) {
    try {
      final cat = LocalData.instance.catalogFor(framework);
      for (final s in cat.standards) {
        if (s.id == id) return s;
      }
    } catch (_) {}
    return null;
  }
}
