class UpstoxConfig {
  UpstoxConfig._();

  static const authBase = 'https://api.upstox.com/v2/login/authorization/dialog';
  static const tokenUrl = 'https://api.upstox.com/v2/login/authorization/token';
  static const apiBase = 'https://api.upstox.com/v2';

  /// Maps our internal symbols to Upstox instrument keys.
  /// Reference: https://upstox.com/developer/api-documentation/instruments
  static const instrumentKeys = <String, String>{
    'NIFTY': 'NSE_INDEX|Nifty 50',
    'BANKNIFTY': 'NSE_INDEX|Nifty Bank',
    'FINNIFTY': 'NSE_INDEX|Nifty Fin Service',
    'SENSEX': 'BSE_INDEX|SENSEX',
  };

  /// Reverse lookup: Upstox key → our symbol.
  static final Map<String, String> symbolOf = {
    for (final e in instrumentKeys.entries) e.value: e.key,
  };

  static String? keyFor(String symbol) => instrumentKeys[symbol];
}
