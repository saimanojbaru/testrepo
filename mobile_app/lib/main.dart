import 'package:flex_color_scheme/flex_color_scheme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'fcm/fcm_service.dart';
import 'screens/dashboard.dart';
import 'screens/paper_trade.dart';
import 'screens/positions.dart';
import 'screens/settings.dart';
import 'screens/splash.dart';
import 'screens/trade_feed.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FcmService.init();
  runApp(const ProviderScope(child: ScalpingAgentApp()));
}

class ScalpingAgentApp extends StatefulWidget {
  const ScalpingAgentApp({super.key});

  @override
  State<ScalpingAgentApp> createState() => _ScalpingAgentAppState();
}

class _ScalpingAgentAppState extends State<ScalpingAgentApp> {
  bool _booted = false;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Scalping Agent',
      debugShowCheckedModeBanner: false,
      theme: FlexThemeData.dark(
        scheme: FlexScheme.deepBlue,
        surfaceMode: FlexSurfaceMode.levelSurfacesLowScaffold,
        blendLevel: 13,
        scaffoldBackground: const Color(0xFF0B1220),
        subThemesData: const FlexSubThemesData(
          blendOnLevel: 20,
          useM2StyleDividerInM3: true,
        ),
        visualDensity: FlexColorScheme.comfortablePlatformDensity,
        useMaterial3: true,
      ),
      home: _booted
          ? const _MainNav()
          : SplashScreen(onContinue: () => setState(() => _booted = true)),
    );
  }
}

class _MainNav extends StatefulWidget {
  const _MainNav();

  @override
  State<_MainNav> createState() => _MainNavState();
}

class _MainNavState extends State<_MainNav> {
  int _idx = 0;

  static const _screens = [
    PaperTradeScreen(),
    DashboardScreen(),
    PositionsScreen(),
    TradeFeedScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _screens[_idx],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _idx,
        onDestinationSelected: (i) => setState(() => _idx = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.candlestick_chart), label: 'Paper'),
          NavigationDestination(icon: Icon(Icons.dashboard), label: 'Dashboard'),
          NavigationDestination(icon: Icon(Icons.account_balance_wallet), label: 'Positions'),
          NavigationDestination(icon: Icon(Icons.timeline), label: 'Feed'),
          NavigationDestination(icon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }
}
