import 'package:flex_color_scheme/flex_color_scheme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import 'screens/login.dart';
import 'screens/shell.dart';
import 'state/auth.dart';
import 'fcm/fcm_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FcmService.instance.init();
  runApp(const ProviderScope(child: ScalpingAgentApp()));
}

class ScalpingAgentApp extends ConsumerWidget {
  const ScalpingAgentApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authed = ref.watch(authProvider).isAuthenticated;

    return MaterialApp(
      title: 'Scalping Agent',
      debugShowCheckedModeBanner: false,
      theme: FlexThemeData.light(
        scheme: FlexScheme.deepBlue,
        useMaterial3: true,
        textTheme: GoogleFonts.interTextTheme(),
      ),
      darkTheme: FlexThemeData.dark(
        scheme: FlexScheme.deepBlue,
        useMaterial3: true,
        textTheme: GoogleFonts.interTextTheme(ThemeData.dark().textTheme),
        surfaceMode: FlexSurfaceMode.highScaffoldLowSurface,
        blendLevel: 20,
      ),
      themeMode: ThemeMode.dark,
      home: authed ? const HomeShell() : const LoginScreen(),
    );
  }
}
