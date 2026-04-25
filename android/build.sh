#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Spotify Mashup Generator — standalone Android APK build script
#
# No Android Studio, no Gradle, no Google Maven required.
# Depends only on tools available via apt + builder-2.3.0.jar from Maven Central.
#
# Output: SpotifyMashupGenerator.apk  (in this directory)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_SRC="$SCRIPT_DIR/app/src/main"
BUILD="$SCRIPT_DIR/.build_out"
APK_OUT="$SCRIPT_DIR/SpotifyMashupGenerator.apk"

# ── Tool locations ────────────────────────────────────────────────────────────
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
AAPT2="/usr/lib/android-sdk/build-tools/debian/aapt2"
APKSIGNER="/usr/lib/android-sdk/build-tools/debian/apksigner"
ZIPALIGN="/usr/bin/zipalign"
KOTLINC="/usr/bin/kotlinc"
JAVAC="/usr/bin/javac"

# dx is embedded inside builder-2.3.0.jar from Maven Central (stored persistently)
DX_JAR="/tmp/builder-2.3.0.jar"
DX_MAIN="com.android.dx.command.Main"

# Use kotlin-stdlib 1.3.72 from Maven Central (JVM-6 bytecode, compatible with old dx)
# The apt-installed stdlib is JVM-8 bytecode which the embedded dx cannot process
KOTLIN_STDLIB="/tmp/kotlin-stdlib-1.3.72.jar"
KEYSTORE="$BUILD/debug.keystore"

# ── Colours for output ────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
step() { echo -e "\n${GREEN}==> $*${NC}"; }
warn() { echo -e "${YELLOW}WARN: $*${NC}"; }
die()  { echo -e "${RED}ERROR: $*${NC}"; exit 1; }

# ── Pre-flight checks ─────────────────────────────────────────────────────────
step "Pre-flight checks"
[ -f "$ANDROID_JAR" ] || die "android.jar not found: $ANDROID_JAR"
[ -f "$AAPT2" ]       || die "aapt2 not found: $AAPT2"
if [ ! -f "$KOTLIN_STDLIB" ]; then
    echo "  Downloading kotlin-stdlib-1.3.72 (JVM-6 bytecode) from Maven Central…"
    curl -fsSL "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.3.72/kotlin-stdlib-1.3.72.jar" \
        -o "$KOTLIN_STDLIB"
fi
command -v "$KOTLINC"   >/dev/null 2>&1 || die "kotlinc not found"
command -v "$JAVAC"     >/dev/null 2>&1 || die "javac not found"
command -v "$ZIPALIGN"  >/dev/null 2>&1 || die "zipalign not found"
echo "  android.jar : $ANDROID_JAR"
echo "  aapt2       : $AAPT2"
echo "  kotlinc     : $(kotlinc -version 2>&1 | grep 'kotlinc' | head -1)"
echo "  javac       : $(javac -version 2>&1)"

# ── Clean build dir ───────────────────────────────────────────────────────────
step "Preparing build directory"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/compiled_res"

# ── Download dx jar (builder-2.3.0.jar from Maven Central) ───────────────────
step "Fetching dx compiler (builder-2.3.0.jar from Maven Central)"
if [ ! -f "$DX_JAR" ]; then
    curl -fsSL \
        "https://repo1.maven.org/maven2/com/android/tools/build/builder/2.3.0/builder-2.3.0.jar" \
        -o "$DX_JAR"
    echo "  Downloaded: $(du -sh "$DX_JAR" | cut -f1)"
else
    echo "  Using cached: $DX_JAR"
fi

# ── Step 1: Compile resources ─────────────────────────────────────────────────
step "Step 1 / 6 — Compiling resources (aapt2 compile)"
"$AAPT2" compile \
    --dir "$APP_SRC/res" \
    -o "$BUILD/compiled_res.zip" \
    -v 2>&1 | grep -v "^$" || true
echo "  Done: $BUILD/compiled_res.zip"

# ── Step 2: Link resources → base APK + R.java ───────────────────────────────
step "Step 2 / 6 — Linking resources (aapt2 link)"
"$AAPT2" link \
    --manifest "$APP_SRC/AndroidManifest.xml" \
    -I "$ANDROID_JAR" \
    -o "$BUILD/base.apk" \
    --java "$BUILD/gen" \
    --min-sdk-version 21 \
    --target-sdk-version 23 \
    --version-code 1 \
    --version-name "1.0" \
    "$BUILD/compiled_res.zip"
echo "  R.java generated:"
find "$BUILD/gen" -name "*.java"

# ── Step 3: Compile R.java ────────────────────────────────────────────────────
step "Step 3 / 6 — Compiling R.java (javac)"
find "$BUILD/gen" -name "*.java" | xargs "$JAVAC" \
    -source 1.8 -target 1.8 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD/classes" \
    2>&1 || die "javac (R.java) failed"
echo "  R.class files in $BUILD/classes"

# ── Step 4: Compile Kotlin sources ───────────────────────────────────────────
step "Step 4 / 6 — Compiling Kotlin sources (kotlinc)"
KOTLIN_SRC=$(find "$APP_SRC/java" -name "*.kt" | tr '\n' ' ')
echo "  Compiling: $KOTLIN_SRC"

# -no-stdlib: don't embed stdlib — we pass it separately to dx for JVM-6 compatibility
# -jvm-target 1.6: produce JVM-6 bytecode that the embedded dx can process
"$KOTLINC" \
    -no-stdlib \
    -cp "$ANDROID_JAR:$KOTLIN_STDLIB:$BUILD/classes" \
    -jvm-target 1.6 \
    $KOTLIN_SRC \
    -d "$BUILD/app-no-runtime.jar" \
    2>&1 | grep -v "^w:" | grep -v "noverify" | grep -v "deprecated" || true

[ -f "$BUILD/app-no-runtime.jar" ] || die "kotlinc produced no app-no-runtime.jar"
echo "  app-no-runtime.jar: $(du -sh "$BUILD/app-no-runtime.jar" | cut -f1)"

# Merge app classes + R.class + kotlin-stdlib into one jar for dx
step "Step 5a — Merging all classes for dx"
mkdir -p "$BUILD/merged_classes"
cd "$BUILD/merged_classes"
unzip -oq "$BUILD/app-no-runtime.jar" 2>/dev/null
unzip -oq "$KOTLIN_STDLIB" 2>/dev/null
# R.class is already baked into app-no-runtime.jar via classpath
cd "$SCRIPT_DIR"
jar cf "$BUILD/app.jar" -C "$BUILD/merged_classes" .
echo "  merged app.jar: $(du -sh "$BUILD/app.jar" | cut -f1)"

# ── Step 5: Merge R.class into app.jar ───────────────────────────────────────
# (R.class merging handled above in the merged_classes step)

# ── Step 5b: Dex ─────────────────────────────────────────────────────────────
step "Step 5b / 6 — Converting to Dalvik bytecode (dx)"
# builder-2.3.0's dx depends on Guava internally
GUAVA_JAR="/tmp/guava-18.0.jar"
if [ ! -f "$GUAVA_JAR" ]; then
    curl -fsSL "https://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar" -o "$GUAVA_JAR"
fi

java -cp "$DX_JAR:$GUAVA_JAR" "$DX_MAIN" \
    --dex \
    --output="$BUILD/classes.dex" \
    "$BUILD/app.jar" \
    2>&1 | grep -v "^$" || true

[ -f "$BUILD/classes.dex" ] || die "dx produced no classes.dex"
echo "  classes.dex: $(du -sh "$BUILD/classes.dex" | cut -f1)"

# ── Step 6: Package dex into APK ─────────────────────────────────────────────
step "Step 6 / 6 — Packaging, aligning, and signing"

# Add classes.dex to the base APK
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
cd "$BUILD"
zip -j unsigned.apk classes.dex >/dev/null
cd "$SCRIPT_DIR"

# zipalign
"$ZIPALIGN" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

# Generate debug signing key if needed
if [ ! -f "$KEYSTORE" ]; then
    echo "  Generating debug keystore…"
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias androidkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Mashup,C=US" \
        -storepass android \
        -keypass android \
        -noprompt 2>/dev/null
fi

# Sign
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androidkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$APK_OUT" \
    "$BUILD/aligned.apk"

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  APK built successfully!                         ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  $(ls -lh "$APK_OUT" | awk '{printf "%-48s", $5" "$NF}')║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Install:                                        ║${NC}"
echo -e "${GREEN}║   adb install SpotifyMashupGenerator.apk         ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════╝${NC}"
