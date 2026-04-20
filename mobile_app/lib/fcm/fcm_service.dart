import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';

@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  // Background messages are handled by the OS notification tray automatically.
}

class FcmService {
  FcmService._();

  static Future<void> init() async {
    try {
      await Firebase.initializeApp();
    } catch (_) {
      // Firebase not configured — skip (no google-services.json yet)
      return;
    }
    FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);

    final messaging = FirebaseMessaging.instance;
    await messaging.requestPermission(alert: true, badge: true, sound: true);

    FirebaseMessaging.onMessage.listen((RemoteMessage msg) {
      final notif = msg.notification;
      if (notif == null) return;
      debugPrint('[FCM] foreground: ${notif.title} – ${notif.body}');
      // In production wire to a local notification plugin to show a banner.
    });
  }

  static Future<String?> getToken() async {
    try {
      return await FirebaseMessaging.instance.getToken();
    } catch (_) {
      return null;
    }
  }
}
