#!/usr/bin/env bash
# Does an existing Space gain the gnu interpreter additively?
#
# T6b's evidence: the payload ships as its own asset (spaces/gnu/), SpaceGnuImageInstaller
# extracts it over a Space that already has its musl image, and the payload's interpreter
# then runs as glibc under the patched tracer. This driver builds the payload when missing
# (tools/rootfs/build-gnu.sh - docker + qemu, cached once built), stages it into the APK's
# assets and the patched proot pair into the test APK's jniLibs, installs both APKs, runs
# GnuOverlayProbeDeviceTest in the app process, and pulls its report.
#
#   scripts/gnu-overlay-driver.sh
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
#
# The APKs are installed through adb, never through `connectedDebugAndroidTest`, which
# uninstalls the app.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-096-t6b}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
GNU_TARBALL="$ROOT/build/rootfs/gnu-arm64-v8a.tzst"
GNU_ASSET_DIR="$ROOT/app/src/main/assets/spaces/gnu"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.jupydroid.app.data.space.GnuOverlayProbeDeviceTest"
DEVICE_PROBE="/sdcard/Android/data/com.jupydroid.app/files/probe"

mkdir -p "$EVIDENCE"
mapfile -t DEVICE_ADB < <(device_adb)

echo "=== the gnu payload over an existing Space's image, on $MODEL ==="

# 1. The payload, built when missing - the same pinned inputs every run reproduces.
if [ ! -f "$GNU_TARBALL" ]; then
  echo "--- build-gnu (docker + qemu; cached once built) ---"
  "$ROOT/tools/rootfs/build-gnu.sh"
fi

# 2. Staged where the APK reads it, beside base's rootfs.
echo "--- stage gnu asset ---"
mkdir -p "$GNU_ASSET_DIR"
cp "$GNU_TARBALL" "$GNU_ASSET_DIR/gnu-arm64-v8a.tzst"

# 3. The patched proot, staged into the test APK's jniLibs before the assemble below.
if [ ! -f "$ROOT/build/proot/proot.static" ]; then
  "$ROOT/tools/proot/build.sh"
fi
python3 "$ROOT/scripts/stage_proot_patched.py" \
  "$ROOT/build/proot/proot.static" \
  "$ROOT/app/src/androidTest/jniLibs/arm64-v8a"

echo "--- assemble ---"
"$ROOT/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest

# 4. Install the built APKs. Not connectedDebugAndroidTest: it uninstalls the app.
echo "--- install ---"
"${DEVICE_ADB[@]}" install -r "$APP_APK" 2>&1 | tee "$EVIDENCE/install-app.txt"
"${DEVICE_ADB[@]}" install -r "$TEST_APK" 2>&1 | tee "$EVIDENCE/install-test.txt"

# 5. The probe, in the app process where the seccomp filter applies.
echo "--- instrument ---"
"${DEVICE_ADB[@]}" shell logcat -c
"${DEVICE_ADB[@]}" shell am instrument -w -e class "$TEST_CLASS" "$RUNNER" 2>&1 |
  tee "$EVIDENCE/instrumentation.txt" || true

# 6. What it measured, pulled where it can be read.
"${DEVICE_ADB[@]}" pull "$DEVICE_PROBE/gnu-overlay-report.txt" "$EVIDENCE/gnu-overlay-report.txt" >/dev/null 2>&1 || true
"${DEVICE_ADB[@]}" shell logcat -d -s JupydroidGnuOverlay >"$EVIDENCE/logcat.txt" 2>&1 || true

if grep -q "OK (" "$EVIDENCE/instrumentation.txt"; then
  echo "PASS: the probe ran to its end; the measurement is in $EVIDENCE/gnu-overlay-report.txt"
else
  echo "FAIL: the probe did not finish; read $EVIDENCE/instrumentation.txt and gnu-overlay-report.txt" >&2
  exit 1
fi
