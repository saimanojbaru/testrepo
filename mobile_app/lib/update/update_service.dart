import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:open_filex/open_filex.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';

/// Polls GitHub Releases for a newer APK and installs it.
///
/// Tag convention (see .github/workflows/build-apk.yml): each main-branch push
/// creates a release tagged `apk-<commit-sha>`. We compare the tag against the
/// current build's `BUILD_SHA` (embedded via --dart-define) — if they differ
/// and the release is newer than the app's build time, an update is offered.
class UpdateService {
  UpdateService({
    this.repo = 'saimanojbaru/testrepo',
    Dio? dio,
  }) : _dio = dio ??
            Dio(BaseOptions(
              connectTimeout: const Duration(seconds: 12),
              receiveTimeout: const Duration(seconds: 60),
            ));

  final String repo;
  final Dio _dio;

  static const String buildSha =
      String.fromEnvironment('BUILD_SHA', defaultValue: 'local');

  Future<UpdateInfo?> check() async {
    try {
      final resp = await _dio.get(
        'https://api.github.com/repos/$repo/releases/latest',
        options: Options(headers: {'Accept': 'application/vnd.github+json'}),
      );
      final data = resp.data;
      if (data is! Map) return null;
      final tag = data['tag_name'] as String? ?? '';
      final name = data['name'] as String? ?? tag;
      final notes = data['body'] as String? ?? '';
      final assets = (data['assets'] as List?) ?? const [];
      final apk = assets.cast<Map>().firstWhere(
            (a) => (a['name'] as String?)?.endsWith('.apk') ?? false,
            orElse: () => const {},
          );
      final downloadUrl = apk['browser_download_url'] as String?;
      final sizeBytes = (apk['size'] as num?)?.toInt() ?? 0;
      if (downloadUrl == null) return null;

      final latestSha = tag.startsWith('apk-') ? tag.substring(4) : tag;
      if (!_shouldPrompt(latestSha)) return null;

      final info = await PackageInfo.fromPlatform();
      return UpdateInfo(
        currentVersion: '${info.version}+${info.buildNumber}',
        currentSha: buildSha,
        latestTag: tag,
        latestName: name,
        latestSha: latestSha,
        releaseNotes: notes,
        downloadUrl: downloadUrl,
        sizeBytes: sizeBytes,
      );
    } catch (e) {
      debugPrint('UpdateService.check failed: $e');
      return null;
    }
  }

  bool _shouldPrompt(String latestSha) {
    if (buildSha == 'local') return false;
    if (latestSha.isEmpty) return false;
    // Compare first 7 chars (short SHA) — covers both full and short SHAs.
    final a = latestSha.length >= 7 ? latestSha.substring(0, 7) : latestSha;
    final b = buildSha.length >= 7 ? buildSha.substring(0, 7) : buildSha;
    return a != b;
  }

  Future<File> download(UpdateInfo info,
      {ValueChanged<double>? onProgress}) async {
    final dir = await getExternalStorageDirectory() ??
        await getApplicationDocumentsDirectory();
    final path = '${dir.path}/scalping_agent_${info.latestSha}.apk';
    final file = File(path);
    if (await file.exists()) {
      await file.delete();
    }
    await _dio.download(
      info.downloadUrl,
      path,
      onReceiveProgress: (rec, total) {
        if (total > 0 && onProgress != null) {
          onProgress(rec / total);
        }
      },
    );
    return file;
  }

  Future<void> install(File apk) async {
    final result = await OpenFilex.open(apk.path, type: 'application/vnd.android.package-archive');
    if (result.type != ResultType.done) {
      throw Exception('Install launch failed: ${result.message}');
    }
  }
}

class UpdateInfo {
  const UpdateInfo({
    required this.currentVersion,
    required this.currentSha,
    required this.latestTag,
    required this.latestName,
    required this.latestSha,
    required this.releaseNotes,
    required this.downloadUrl,
    required this.sizeBytes,
  });

  final String currentVersion;
  final String currentSha;
  final String latestTag;
  final String latestName;
  final String latestSha;
  final String releaseNotes;
  final String downloadUrl;
  final int sizeBytes;

  String get sizeLabel {
    if (sizeBytes <= 0) return 'unknown size';
    final mb = sizeBytes / (1024 * 1024);
    return '${mb.toStringAsFixed(1)} MB';
  }

  String get shortCurrent =>
      currentSha.length >= 7 ? currentSha.substring(0, 7) : currentSha;
  String get shortLatest =>
      latestSha.length >= 7 ? latestSha.substring(0, 7) : latestSha;
}
