import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'data/local_data.dart';
import 'screens/home_screen.dart';
import 'services/api_service.dart';
import 'theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarColor: Color(0xFF050B18),
    systemNavigationBarIconBrightness: Brightness.light,
  ));
  await SystemChrome.setPreferredOrientations(<DeviceOrientation>[
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  // Load all bundled datasets before first frame so screens have data ready.
  await LocalData.instance.load();
  // Load any persisted backend URL so online mode starts up automatically.
  await ApiService.instance.load();
  runApp(const InsuranceIntelligenceApp());
}

class InsuranceIntelligenceApp extends StatelessWidget {
  const InsuranceIntelligenceApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Insurance Intelligence Pro',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark,
      home: const HomeScreen(),
    );
  }
}
