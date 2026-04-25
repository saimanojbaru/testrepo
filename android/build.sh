#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Mashup Studio — modern standalone Android APK build script
#
#   • Compiles against android.jar API 34
#   • Dexes with d8 (R8 8.x release jar from Google's r8-releases bucket)
#   • Compiles Kotlin with kotlinc 2.0.21 (downloaded from JetBrains releases)
#   • Targets minSdk 26 / targetSdk 34 — installs on Android 8.0 through 14+
#   • No Gradle, no AndroidX, no Google Maven required
#
# Output: SpotifyMashupGenerator.apk (in this directory)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_SRC="$SCRIPT_DIR/app/src/main"
BUILD="$SCRIPT_DIR/.build_out"
APK_OUT="$SCRIPT_DIR/SpotifyMashupGenerator.apk"

# ── External toolchain (cached under /tmp) ───────────────────────────────────
TOOL_DIR="${MASHUP_TOOL_DIR:-/tmp/mashup-toolchain}"
mkdir -p "$TOOL_DIR"

ANDROID_JAR="$TOOL_DIR/android-34.jar"
R8_JAR="$TOOL_DIR/r8.jar"
KOTLINC_DIR="$TOOL_DIR/kotlinc-2.0.21"
KOTLINC="$KOTLINC_DIR/bin/kotlinc"
KOTLIN_STDLIB="$KOTLINC_DIR/lib/kotlin-stdlib.jar"

# ── apt-installed tools ──────────────────────────────────────────────────────
AAPT2="/usr/bin/aapt2"
APKSIGNER="/usr/bin/apksigner"
ZIPALIGN="/usr/bin/zipalign"
JAVAC="/usr/bin/javac"
KEYSTORE="$BUILD/debug.keystore"

# ── Output styling ───────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
step() { echo -e "\n${CYAN}==> $*${NC}"; }
info() { echo -e "    $*"; }
warn() { echo -e "${YELLOW}WARN:${NC} $*"; }
die()  { echo -e "${RED}ERROR:${NC} $*"; exit 1; }

# ── Toolchain bootstrap ──────────────────────────────────────────────────────
fetch_toolchain() {
    step "Fetching modern toolchain (cached at $TOOL_DIR)"

    if [ ! -f "$ANDROID_JAR" ]; then
        info "Downloading android.jar (API 34)…"
        curl -fsSL "https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar" \
            -o "$ANDROID_JAR" || die "android.jar download failed"
    fi
    info "android.jar : $(du -sh "$ANDROID_JAR" | cut -f1)"

    if [ ! -f "$R8_JAR" ]; then
        info "Downloading r8/d8 8.5.35 from Google storage…"
        curl -fsSL "https://storage.googleapis.com/r8-releases/raw/8.5.35/r8.jar" \
            -o "$R8_JAR" || die "r8.jar download failed"
    fi
    info "r8.jar      : $(du -sh "$R8_JAR" | cut -f1)"

    if [ ! -x "$KOTLINC" ]; then
        info "Downloading Kotlin 2.0.21 compiler from JetBrains releases…"
        local zip="$TOOL_DIR/kotlinc-2.0.21.zip"
        curl -fsSL "https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip" \
            -o "$zip"
        ( cd "$TOOL_DIR" && unzip -q -o "$zip" && [ -d kotlinc ] && mv kotlinc kotlinc-2.0.21 || true )
        rm -f "$zip"
    fi
    info "kotlinc     : $($KOTLINC -version 2>&1 | head -1)"
}

preflight() {
    step "Pre-flight checks"
    [ -f "$AAPT2" ]     || die "aapt2 not found: $AAPT2"
    [ -f "$APKSIGNER" ] || die "apksigner not found: $APKSIGNER"
    [ -f "$ZIPALIGN" ]  || die "zipalign not found: $ZIPALIGN"
    [ -f "$JAVAC" ]     || die "javac not found: $JAVAC"
    info "javac       : $($JAVAC -version 2>&1)"
}

clean_build() {
    step "Preparing build directory"
    rm -rf "$BUILD"
    mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"
}

compile_resources() {
    step "Step 1/6 — Compiling resources (aapt2 compile)"
    "$AAPT2" compile --dir "$APP_SRC/res" -o "$BUILD/compiled_res.zip" \
        2>&1 | grep -v "^$" || true
    info "compiled_res.zip: $(du -sh "$BUILD/compiled_res.zip" | cut -f1)"
}

link_resources() {
    step "Step 2/6 — Linking resources (aapt2 link)"
    "$AAPT2" link \
        --manifest "$APP_SRC/AndroidManifest.xml" \
        -I "$ANDROID_JAR" \
        -o "$BUILD/base.apk" \
        --java "$BUILD/gen" \
        --min-sdk-version 26 \
        --target-sdk-version 34 \
        --version-code 2 \
        --version-name "2.0" \
        --no-version-vectors \
        "$BUILD/compiled_res.zip" \
        2>&1 | grep -v "^$" || true
    info "Generated R.java tree:"
    find "$BUILD/gen" -name "*.java" | sed 's|^|      |'
}

compile_r_java() {
    step "Step 3/6 — Compiling R.java (javac)"
    find "$BUILD/gen" -name "*.java" -print0 | xargs -0 "$JAVAC" \
        -source 17 -target 17 \
        -cp "$ANDROID_JAR" \
        -d "$BUILD/classes" \
        2>&1 || die "javac (R.java) failed"
}

compile_kotlin() {
    step "Step 4/6 — Compiling Kotlin sources (kotlinc 2.0.21)"
    local sources
    sources=$(find "$APP_SRC/java" -name "*.kt" | tr '\n' ' ')
    info "Sources: $(echo "$sources" | wc -w) files"
    "$KOTLINC" \
        -classpath "$ANDROID_JAR:$BUILD/classes" \
        -jvm-target 17 \
        -d "$BUILD/app-classes.jar" \
        $sources \
        2>&1 | grep -v "^warning:" | grep -v "^w:" | grep -v "^$" || true
    [ -f "$BUILD/app-classes.jar" ] || die "kotlinc produced no app-classes.jar"
    info "app-classes.jar: $(du -sh "$BUILD/app-classes.jar" | cut -f1)"
}

dex_classes() {
    step "Step 5/6 — Dexing with d8"
    # Bundle javac-compiled R.class files into a jar (d8 accepts jars and single .class files, not class trees)
    ( cd "$BUILD/classes" && jar cf "$BUILD/r-classes.jar" . )
    java -cp "$R8_JAR" com.android.tools.r8.D8 \
        --release \
        --min-api 26 \
        --lib "$ANDROID_JAR" \
        --output "$BUILD/dex" \
        "$BUILD/app-classes.jar" \
        "$BUILD/r-classes.jar" \
        "$KOTLIN_STDLIB" \
        2>&1 | grep -v "^$" || true
    [ -f "$BUILD/dex/classes.dex" ] || die "d8 produced no classes.dex"
    info "classes.dex: $(du -sh "$BUILD/dex/classes.dex" | cut -f1)"
}

package_apk() {
    step "Step 6/6 — Packaging, aligning, signing"
    cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
    ( cd "$BUILD/dex" && zip -j "$BUILD/unsigned.apk" classes.dex >/dev/null )
    for extra in "$BUILD/dex"/classes*.dex; do
        [ -f "$extra" ] && [ "$(basename "$extra")" != "classes.dex" ] && \
            ( cd "$BUILD/dex" && zip -j "$BUILD/unsigned.apk" "$(basename "$extra")" >/dev/null )
    done

    "$ZIPALIGN" -p -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

    if [ ! -f "$KEYSTORE" ]; then
        info "Generating debug keystore…"
        keytool -genkeypair \
            -keystore "$KEYSTORE" \
            -alias androidkey \
            -keyalg RSA -keysize 2048 \
            -validity 10000 \
            -dname "CN=Mashup Studio Debug,O=Mashup Studio,C=US" \
            -storepass android -keypass android \
            -noprompt 2>/dev/null
    fi

    "$APKSIGNER" sign \
        --ks "$KEYSTORE" \
        --ks-key-alias androidkey \
        --ks-pass pass:android \
        --key-pass pass:android \
        --min-sdk-version 26 \
        --v1-signing-enabled true \
        --v2-signing-enabled true \
        --v3-signing-enabled true \
        --out "$APK_OUT" \
        "$BUILD/aligned.apk"

    "$APKSIGNER" verify --verbose "$APK_OUT" 2>&1 | head -8
}

fetch_toolchain
preflight
clean_build
compile_resources
link_resources
compile_r_java
compile_kotlin
dex_classes
package_apk

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Mashup Studio APK built successfully!           ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════╣${NC}"
printf  "${GREEN}║  %-48s${GREEN}║${NC}\n" "$(ls -lh "$APK_OUT" | awk '{printf "%s %s", $5, $NF}')"
echo -e "${GREEN}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Install on a connected device:                  ║${NC}"
echo -e "${GREEN}║    adb install -r SpotifyMashupGenerator.apk     ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════╝${NC}"
