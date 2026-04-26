# Getting the APK

This repo's CI (`.github/workflows/build-apk.yml`) builds a fresh Android
APK on every push to `main` or any `claude/**` branch.

## Where to grab it

1. Open https://github.com/saimanojbaru/testrepo/actions
2. Pick the most recent run with a green check on this branch
   (`claude/scalping-agent-indian-options-bKW6h`)
3. Scroll to **Artifacts** → click `scalping-agent-apk` → downloads a zip
4. Unzip → install `app-release.apk` on your phone (you'll need to allow
   "Install unknown apps" for whichever app you're sideloading from)

## After the first install

The app self-updates from then on:

- On launch it polls `GET /repos/saimanojbaru/testrepo/releases/latest`
- If the release tag `apk-<sha>` differs from the embedded `BUILD_SHA`,
  a bottom sheet pops up offering download + install
- Settings → "APP UPDATE" tile lets you trigger a manual check

⚠️ **GitHub releases only fire on pushes to `main`.** Feature-branch
artifacts are downloadable via the Actions page, but the in-app updater
won't see them until they merge to `main`.

## Why I (the AI) cannot hand you an APK directly

I don't have access to a signing keystore, an Android NDK, or a way to
attach binary files to messages. Every APK comes from the GitHub Actions
runner, signed with the debug keystore that `flutter build apk` provides
out of the box. If you want a production-signed APK for the Play Store,
that's a one-time manual step: generate a release keystore, drop it in
`mobile_app/android/app/`, and add `key.properties` to `.gitignore`.
