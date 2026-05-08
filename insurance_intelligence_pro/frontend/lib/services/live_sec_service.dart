import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../models/kpi.dart';

/// Direct SEC EDGAR client — runs **on-device**, no backend.
///
/// SEC requires every request to identify itself with a User-Agent
/// containing a real contact. We send one. Failures are silent.
///
/// Endpoints used:
/// * `https://data.sec.gov/submissions/CIK{cik}.json`
///     — list of recent filings with accession numbers
/// * `https://data.sec.gov/api/xbrl/companyfacts/CIK{cik}.json`
///     — full XBRL-tagged financial history (we use this in a future
///       upgrade; the curated dataset stays as the immediate source)
class LiveSecService {
  LiveSecService._();
  static final LiveSecService instance = LiveSecService._();

  static const String _userAgent =
      'InsuranceIntelligencePro/1.0 (research@example.com)';

  final Map<String, FilingSource> _cache = {};

  /// Fetch the latest 10-K filing metadata for a normalised CIK
  /// (10-digit zero-padded). Returns the cached value if available;
  /// returns null on any failure.
  Future<FilingSource?> latestAnnualReport({
    required String cik,
    required String ticker,
  }) async {
    if (cik.isEmpty) return null;
    final padded = cik.padLeft(10, '0');
    final cached = _cache[padded];
    if (cached != null) return cached;
    try {
      final url = 'https://data.sec.gov/submissions/CIK$padded.json';
      final r = await http.get(Uri.parse(url), headers: {
        'User-Agent': _userAgent,
        'Accept': 'application/json',
      }).timeout(const Duration(seconds: 8));
      if (r.statusCode != 200) return null;
      final body = jsonDecode(r.body) as Map<String, dynamic>;
      final recent = (body['filings'] as Map?)?['recent'] as Map?;
      if (recent == null) return null;
      final forms = (recent['form'] as List?) ?? [];
      final dates = (recent['filingDate'] as List?) ?? [];
      final acc = (recent['accessionNumber'] as List?) ?? [];
      final primary = (recent['primaryDocument'] as List?) ?? [];
      for (var i = 0; i < forms.length; i++) {
        final f = forms[i].toString();
        if (f == '10-K' || f == '20-F') {
          final accession = i < acc.length ? acc[i].toString() : '';
          final filed = i < dates.length ? dates[i].toString() : '';
          final doc = i < primary.length ? primary[i].toString() : '';
          final fyEndIso = filed.isNotEmpty ? filed : '';
          final fy = _fiscalYearOf(fyEndIso);
          final accNoDash = accession.replaceAll('-', '');
          final filingUrl = accNoDash.isEmpty
              ? 'https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=$padded&type=10-K'
              : 'https://www.sec.gov/Archives/edgar/data/${int.parse(padded).toString()}/$accNoDash/${doc.isEmpty ? "" : doc}';
          final src = FilingSource(
            form: f,
            fiscalYear: fy.isEmpty ? 'FY${_yearOf(filed) - 1}' : fy,
            filedDate: _prettyDate(filed),
            url: filingUrl,
          );
          _cache[padded] = src;
          return src;
        }
      }
      return null;
    } catch (e) {
      if (kDebugMode) {
        debugPrint('LiveSec fetch failed for $padded: $e');
      }
      return null;
    }
  }

  /// "FY" + (filing year - 1). 10-Ks are typically filed for the FY
  /// ending the prior calendar year; this is a best-effort label.
  String _fiscalYearOf(String iso) {
    if (iso.isEmpty) return '';
    final dt = DateTime.tryParse(iso);
    if (dt == null) return '';
    return 'FY${dt.year - 1}';
  }

  int _yearOf(String iso) {
    final dt = DateTime.tryParse(iso);
    return dt?.year ?? DateTime.now().year;
  }

  String _prettyDate(String iso) {
    final dt = DateTime.tryParse(iso);
    if (dt == null) return iso;
    const months = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
    ];
    return '${months[dt.month - 1]} ${dt.day}, ${dt.year}';
  }
}
