import 'package:flutter_test/flutter_test.dart';
import 'package:scalping_agent/update/update_service.dart';

void main() {
  group('UpdateInfo formatting', () {
    test('sizeLabel converts bytes to MB with 1 decimal', () {
      const info = UpdateInfo(
        currentVersion: '1.0.0+1',
        currentSha: 'abcdef1234',
        latestTag: 'apk-1234567890',
        latestName: 'APK build',
        latestSha: '1234567890',
        releaseNotes: '',
        downloadUrl: 'https://example.com/app.apk',
        sizeBytes: 25 * 1024 * 1024,
      );
      expect(info.sizeLabel, '25.0 MB');
      expect(info.shortCurrent, 'abcdef1');
      expect(info.shortLatest, '1234567');
    });

    test('sizeLabel handles unknown size', () {
      const info = UpdateInfo(
        currentVersion: '1.0.0+1',
        currentSha: 'local',
        latestTag: 'apk-x',
        latestName: 'n',
        latestSha: 'x',
        releaseNotes: '',
        downloadUrl: 'u',
        sizeBytes: 0,
      );
      expect(info.sizeLabel, 'unknown size');
      expect(info.shortCurrent, 'local');
      expect(info.shortLatest, 'x');
    });
  });
}
