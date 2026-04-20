import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppMode { paper, live }

class AppModeNotifier extends StateNotifier<AppMode> {
  AppModeNotifier() : super(AppMode.paper) {
    _load();
  }

  static const _key = 'app_mode';

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final v = prefs.getString(_key);
    if (v == 'live') state = AppMode.live;
  }

  Future<void> toggle() async {
    state = state == AppMode.paper ? AppMode.live : AppMode.paper;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, state == AppMode.live ? 'live' : 'paper');
  }

  Future<void> set(AppMode m) async {
    state = m;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, m == AppMode.live ? 'live' : 'paper');
  }
}

final appModeProvider =
    StateNotifierProvider<AppModeNotifier, AppMode>((ref) => AppModeNotifier());

extension AppModeX on AppMode {
  bool get isPaper => this == AppMode.paper;
  bool get isLive => this == AppMode.live;
  String get label => isPaper ? 'PAPER' : 'LIVE';
}
