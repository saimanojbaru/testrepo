import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'update_service.dart';

enum UpdatePhase { idle, checking, available, downloading, installing, error }

class UpdateController extends ChangeNotifier {
  UpdateController({UpdateService? service})
      : _service = service ?? UpdateService();

  final UpdateService _service;

  UpdatePhase phase = UpdatePhase.idle;
  UpdateInfo? info;
  double progress = 0;
  String? error;

  Future<void> check({bool silent = false}) async {
    if (phase == UpdatePhase.checking ||
        phase == UpdatePhase.downloading) {
      return;
    }
    phase = UpdatePhase.checking;
    error = null;
    if (!silent) notifyListeners();
    final res = await _service.check();
    if (res == null) {
      phase = UpdatePhase.idle;
      info = null;
      notifyListeners();
      return;
    }
    info = res;
    phase = UpdatePhase.available;
    notifyListeners();
  }

  Future<void> downloadAndInstall() async {
    final i = info;
    if (i == null) return;
    phase = UpdatePhase.downloading;
    progress = 0;
    notifyListeners();
    try {
      final file = await _service.download(i, onProgress: (p) {
        progress = p;
        notifyListeners();
      });
      phase = UpdatePhase.installing;
      notifyListeners();
      await _service.install(file);
      phase = UpdatePhase.idle;
      info = null;
      notifyListeners();
    } catch (e) {
      phase = UpdatePhase.error;
      error = e.toString();
      notifyListeners();
    }
  }

  void dismiss() {
    if (phase == UpdatePhase.available || phase == UpdatePhase.error) {
      phase = UpdatePhase.idle;
      error = null;
      notifyListeners();
    }
  }
}

final updateControllerProvider =
    ChangeNotifierProvider<UpdateController>((ref) {
  final c = UpdateController();
  ref.onDispose(c.dispose);
  return c;
});
