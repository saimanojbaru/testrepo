import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TelegramConfig {
  const TelegramConfig({required this.botToken, required this.chatId});
  final String botToken;
  final String chatId;

  bool get isComplete => botToken.isNotEmpty && chatId.isNotEmpty;
}

enum TelegramPhase { idle, saving, sending, sent, error }

class TelegramController extends ChangeNotifier {
  TelegramController({FlutterSecureStorage? storage, Dio? dio})
      : _storage = storage ?? const FlutterSecureStorage(),
        _dio = dio ?? Dio() {
    _bootstrap();
  }

  final FlutterSecureStorage _storage;
  final Dio _dio;

  static const _kToken = 'telegram_bot_token';
  static const _kChat = 'telegram_chat_id';

  TelegramConfig? config;
  TelegramPhase phase = TelegramPhase.idle;
  String? error;

  bool get configured => config?.isComplete ?? false;

  Future<void> _bootstrap() async {
    final t = await _storage.read(key: _kToken);
    final c = await _storage.read(key: _kChat);
    if (t != null && c != null) {
      config = TelegramConfig(botToken: t, chatId: c);
      notifyListeners();
    }
  }

  Future<void> save(TelegramConfig cfg) async {
    phase = TelegramPhase.saving;
    notifyListeners();
    await _storage.write(key: _kToken, value: cfg.botToken);
    await _storage.write(key: _kChat, value: cfg.chatId);
    config = cfg;
    phase = TelegramPhase.idle;
    error = null;
    notifyListeners();
  }

  Future<void> clear() async {
    await _storage.delete(key: _kToken);
    await _storage.delete(key: _kChat);
    config = null;
    phase = TelegramPhase.idle;
    notifyListeners();
  }

  Future<void> sendTestMessage() async {
    final cfg = config;
    if (cfg == null || !cfg.isComplete) {
      error = 'Save bot token and chat ID first.';
      phase = TelegramPhase.error;
      notifyListeners();
      return;
    }
    phase = TelegramPhase.sending;
    error = null;
    notifyListeners();
    try {
      final resp = await _dio.post(
        'https://api.telegram.org/bot${cfg.botToken}/sendMessage',
        data: {
          'chat_id': cfg.chatId,
          'text': '*Scalping Agent · test message*\n'
              'If you see this, the bot is wired correctly.',
          'parse_mode': 'Markdown',
        },
      );
      final ok = resp.data is Map && (resp.data['ok'] == true);
      if (!ok) {
        final desc = resp.data is Map
            ? (resp.data['description']?.toString() ?? 'unknown error')
            : 'unknown error';
        throw Exception(desc);
      }
      phase = TelegramPhase.sent;
    } catch (e) {
      phase = TelegramPhase.error;
      error = e.toString();
    } finally {
      notifyListeners();
    }
  }
}

final telegramControllerProvider =
    ChangeNotifierProvider<TelegramController>((ref) {
  final c = TelegramController();
  ref.onDispose(c.dispose);
  return c;
});
