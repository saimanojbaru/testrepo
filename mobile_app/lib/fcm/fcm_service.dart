import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

/// Minimal FCM integration. Initialization is best-effort - if Firebase is not
/// configured (e.g. google-services.json missing in dev) the app still runs.
class FcmService {
  FcmService._();
  static final FcmService instance = FcmService._();

  String? _token;
  String? get token => _token;

  Future<void> init() async {
    try {
      await Firebase.initializeApp();
      final messaging = FirebaseMessaging.instance;
      await messaging.requestPermission(alert: true, badge: true, sound: true);
      _token = await messaging.getToken();
      debugPrint('FCM token: $_token');
      FirebaseMessaging.onMessage.listen((msg) {
        debugPrint('FCM foreground: ${msg.notification?.title} ${msg.notification?.body}');
      });
    } catch (e) {
      debugPrint('FCM disabled (ok for dev): $e');
    }
  }
}
