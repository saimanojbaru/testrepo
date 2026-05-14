import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:shared_preferences/shared_preferences.dart';

/// Core gamification state — XP, levels, badges, streaks, and a
/// "Fun Mode" feature flag.
///
/// Singleton ChangeNotifier so any widget can `AnimatedBuilder` against
/// `XpService.instance` and re-render when state updates.
/// All state is mirrored to SharedPreferences for cross-session
/// persistence.
class XpService extends ChangeNotifier {
  XpService._();
  static final XpService instance = XpService._();

  // ---- Prefs keys ----------------------------------------------------------
  static const _kXp = 'iip_xp';
  static const _kStreak = 'iip_streak_days';
  static const _kStreakLast = 'iip_streak_last_iso';
  static const _kEarnedBadges = 'iip_badges_earned';
  static const _kCounters = 'iip_event_counters';
  static const _kFunMode = 'iip_fun_mode';

  // ---- In-memory state -----------------------------------------------------
  int _xp = 0;
  int _streak = 0;
  String _streakLastIso = '';
  Set<String> _earnedBadges = {};
  Map<String, int> _eventCounters = {};
  bool _funMode = true;
  bool _loaded = false;

  // ---- Catalog -------------------------------------------------------------
  List<BadgeDef> _badgeCatalog = const [];
  List<LevelDef> _levelCatalog = const [];

  // ---- Public state --------------------------------------------------------
  int get xp => _xp;
  int get streakDays => _streak;
  Set<String> get earnedBadgeIds => _earnedBadges;
  bool get funMode => _funMode;
  bool get loaded => _loaded;
  List<BadgeDef> get badges => _badgeCatalog;
  List<LevelDef> get levels => _levelCatalog;

  LevelDef get currentLevel {
    var current = _levelCatalog.first;
    for (final l in _levelCatalog) {
      if (_xp >= l.minXp) current = l;
    }
    return current;
  }

  LevelDef? get nextLevel {
    for (final l in _levelCatalog) {
      if (l.minXp > _xp) return l;
    }
    return null;
  }

  /// 0.0 → 1.0 progress within the current level band.
  double get progressToNextLevel {
    final next = nextLevel;
    if (next == null) return 1.0;
    final cur = currentLevel;
    final span = (next.minXp - cur.minXp).clamp(1, 1 << 31);
    return ((_xp - cur.minXp) / span).clamp(0.0, 1.0);
  }

  /// Load persisted state + badge catalog once at app startup.
  Future<void> load() async {
    if (_loaded) return;
    final prefs = await SharedPreferences.getInstance();
    _xp = prefs.getInt(_kXp) ?? 0;
    _streak = prefs.getInt(_kStreak) ?? 0;
    _streakLastIso = prefs.getString(_kStreakLast) ?? '';
    _earnedBadges = (prefs.getStringList(_kEarnedBadges) ?? []).toSet();
    _funMode = prefs.getBool(_kFunMode) ?? true;
    final raw = prefs.getString(_kCounters);
    if (raw != null) {
      try {
        final map = jsonDecode(raw) as Map<String, dynamic>;
        _eventCounters = map.map((k, v) => MapEntry(k, (v as num).toInt()));
      } catch (_) {}
    }
    // Catalog from asset bundle.
    final text = await rootBundle.loadString('assets/data/badges.json');
    final body = jsonDecode(text) as Map<String, dynamic>;
    _badgeCatalog = ((body['badges'] as List?) ?? [])
        .map((e) => BadgeDef.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
    _levelCatalog = ((body['levels'] as List?) ?? [])
        .map((e) => LevelDef.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
    _bumpDailyStreak();
    _loaded = true;
    notifyListeners();
  }

  // ---- Events --------------------------------------------------------------

  /// Record an event (e.g. "checklist_item_completed", "quiz_won").
  /// Returns the list of badge IDs newly unlocked by this event so the
  /// caller can show celebratory UI.
  Future<List<BadgeDef>> recordEvent(String event,
      {int xpDelta = 0, int countDelta = 1}) async {
    _eventCounters.update(event, (v) => v + countDelta,
        ifAbsent: () => countDelta);
    if (xpDelta > 0) _xp += xpDelta;
    final newly = _checkUnlocks();
    for (final b in newly) {
      _earnedBadges.add(b.id);
      _xp += b.xpReward;
    }
    await _persist();
    notifyListeners();
    return newly;
  }

  Future<void> setFunMode(bool value) async {
    _funMode = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_kFunMode, value);
    notifyListeners();
  }

  /// Returns the unmet badges of the right rarity for "next up" hints.
  List<BadgeDef> upcomingBadges({int limit = 3}) {
    return _badgeCatalog
        .where((b) => !_earnedBadges.contains(b.id))
        .take(limit)
        .toList();
  }

  // ---- Internal ------------------------------------------------------------

  List<BadgeDef> _checkUnlocks() {
    final newly = <BadgeDef>[];
    for (final b in _badgeCatalog) {
      if (_earnedBadges.contains(b.id)) continue;
      final count = _eventCounters[b.unlockEvent] ?? 0;
      if (count >= b.unlockCount) newly.add(b);
    }
    return newly;
  }

  void _bumpDailyStreak() {
    final today = DateTime.now().toIso8601String().substring(0, 10);
    if (_streakLastIso == today) return;
    final yesterday =
        DateTime.now().subtract(const Duration(days: 1)).toIso8601String().substring(0, 10);
    if (_streakLastIso == yesterday) {
      _streak += 1;
    } else {
      _streak = 1;
    }
    _streakLastIso = today;
    // Trigger streak-based badges.
    _eventCounters['daily_streak'] = _streak;
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_kXp, _xp);
    await prefs.setInt(_kStreak, _streak);
    await prefs.setString(_kStreakLast, _streakLastIso);
    await prefs.setStringList(_kEarnedBadges, _earnedBadges.toList());
    await prefs.setString(_kCounters, jsonEncode(_eventCounters));
  }
}

/// Badge catalog entry — loaded from `assets/data/badges.json`.
class BadgeDef {
  final String id;
  final String name;
  final String icon;
  final String rarity; // common / rare / epic / legendary
  final String description;
  final int xpReward;
  final String unlockEvent;
  final int unlockCount;

  const BadgeDef({
    required this.id,
    required this.name,
    required this.icon,
    required this.rarity,
    required this.description,
    required this.xpReward,
    required this.unlockEvent,
    required this.unlockCount,
  });

  factory BadgeDef.fromJson(Map<String, dynamic> j) {
    final unlock = Map<String, dynamic>.from(j['unlock'] as Map);
    return BadgeDef(
      id: j['id'] as String,
      name: j['name'] as String,
      icon: j['icon'] as String,
      rarity: j['rarity'] as String,
      description: j['description'] as String,
      xpReward: (j['xp_reward'] as num).toInt(),
      unlockEvent: unlock['event'] as String,
      unlockCount: (unlock['count'] as num).toInt(),
    );
  }
}

class LevelDef {
  final int level;
  final String title;
  final int minXp;
  const LevelDef({required this.level, required this.title, required this.minXp});
  factory LevelDef.fromJson(Map<String, dynamic> j) => LevelDef(
        level: (j['level'] as num).toInt(),
        title: j['title'] as String,
        minXp: (j['min_xp'] as num).toInt(),
      );
}
