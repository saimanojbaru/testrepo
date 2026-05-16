#!/usr/bin/env bash
# Local APK build script for Corporate Dragon: Middle-Class Odyssey.
#
# Run this on your own machine (where dl.google.com is reachable) to produce
# a debug-signed APK without needing the Godot editor GUI.
#
# Requires:
#   - Godot 4.3+ binary on PATH (downloadable from https://godotengine.org/download)
#   - JDK 17+ (or 21+ — the build is tolerant)
#   - apksigner + zipalign (from Android Build Tools, available via your OS
#     package manager; e.g. `sudo apt install android-sdk-build-tools` on
#     Ubuntu, `brew install --cask android-platform-tools` on macOS).
#
# Output: build/android/dragon-debug.apk

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

GODOT_BIN="${GODOT_BIN:-godot}"
KEYSTORE_DIR="$HOME/.local/share/godot/keystores"
KEYSTORE="$KEYSTORE_DIR/debug.keystore"

# 1. Make a debug keystore if one doesn't already exist.
mkdir -p "$KEYSTORE_DIR"
if [[ ! -f "$KEYSTORE" ]]; then
  echo "Generating debug keystore..."
  keytool -keyalg RSA -genkeypair -alias androiddebugkey -keypass android \
    -keystore "$KEYSTORE" -storepass android \
    -dname "CN=Android Debug,O=Android,C=US" -validity 9999
fi

# 2. Point Godot at the keystore + your local Android SDK via editor settings.
GODOT_CFG_DIR="$HOME/.config/godot"
mkdir -p "$GODOT_CFG_DIR"

ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/lib/android-sdk}}"

cat > "$GODOT_CFG_DIR/editor_settings-4.3.tres" <<EOF
[gd_resource type="EditorSettings" format=3]

[resource]
export/android/android_sdk_path = "$ANDROID_SDK"
export/android/debug_keystore = "$KEYSTORE"
export/android/debug_keystore_user = "androiddebugkey"
export/android/debug_keystore_pass = "android"
EOF

# 3. Import + export.
mkdir -p build/android
echo "Importing project..."
"$GODOT_BIN" --headless --import

echo "Exporting Android APK..."
"$GODOT_BIN" --headless --export-debug "Android" build/android/dragon-debug.apk

echo
echo "Done. APK at: build/android/dragon-debug.apk"
ls -lh build/android/dragon-debug.apk
