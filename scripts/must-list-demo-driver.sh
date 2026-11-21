#!/usr/bin/env bash
# Does every package on the must-list install from PyPI's wheels and run its quickstart?
#
# The must-list demo's evidence: MustListDemoDeviceTest seeds a private root the way a Space's start
# leaves it (musl image + gnu overlay + kernelspec + pip config), runs the server through the
# app's own composition on the shipped patched proot, and for each of the thirty-six packages
# installs it with %pip install and runs the snippet from its official docs in a fresh kernel -
# a package whose docs example cannot work in a Space (no JVM, no system library) must match its
# measured limitation signature instead. After the first group the server is killed and
# restarted and the installs must still import. The report lands in the probe directory.
#
#   scripts/must-list-demo-driver.sh [method ...]
#
# With no arguments every group runs; with arguments only the named @Test methods run
# (g1_foundations, g1b_relaunchKeepsTheInstalls, g2_visualiseAndModel, g3_deepLearning,
# g3b_deepLearningFrameworks, g4_languageAndVision, g5_platformAndServing). Run one group at a
# time when the tablet is short on storage or a group is being re-measured.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and pip needs the tablet online.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-096-t7}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.jupydroid.app.data.space.MustListDemoDeviceTest"
DEVICE_PROBE="/sdcard/Android/data/com.jupydroid.app/files/probe"

mkdir -p "$EVIDENCE"
mapfile -t DEVICE_ADB < <(device_adb)

# The staged artifacts every run reads: the patched proot pair in jniLibs and the base+gnu
# payloads in assets. stage-app-artifacts.sh refuses without build/proot/proot.static, so the
# build runs first when it is missing.
if [ ! -f "$ROOT/build/proot/proot.static" ]; then
  echo "--- build proot (docker + arm64 binfmt; cached once built) ---"
  "$ROOT/tools/proot/build.sh"
fi
"$ROOT/tools/rootfs/stage-app-artifacts.sh" arm64-v8a | tee "$EVIDENCE/stage-artifacts.txt"

echo "=== the must-list demo, on $MODEL ==="

# The APKs, with the payload assets and the patched pair inside them.
echo "--- assemble ---"
"$ROOT/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest

echo "--- install ---"
"${DEVICE_ADB[@]}" install -r "$APP_APK" 2>&1 | tee "$EVIDENCE/install-app.txt"
"${DEVICE_ADB[@]}" install -r "$TEST_APK" 2>&1 | tee "$EVIDENCE/install-test.txt"

# Which tests to run: every @Test method when no method is named, else the named ones.
# am instrument's class argument is a comma-separated list of Class#method selectors.
SELECTOR="$TEST_CLASS"
if [ "$#" -gt 0 ]; then
  SELECTOR=""
  for group in "$@"; do
    SELECTOR="${SELECTOR:+$SELECTOR,}$TEST_CLASS#$group"
  done
fi

echo "--- instrument ($SELECTOR) ---"
"${DEVICE_ADB[@]}" shell logcat -c
"${DEVICE_ADB[@]}" shell am instrument -w -e class "$SELECTOR" "$RUNNER" 2>&1 |
  tee "$EVIDENCE/instrumentation.txt" || true

# What it measured, pulled where it can be read and appended so group runs accumulate one
# report rather than overwriting each other.
PULLED="$(mktemp)"
if "${DEVICE_ADB[@]}" pull "$DEVICE_PROBE/must-list-report.txt" "$PULLED" >/dev/null 2>&1; then
  cat "$PULLED" >>"$EVIDENCE/must-list-report.txt"
fi
rm -f "$PULLED"
"${DEVICE_ADB[@]}" shell logcat -d -s JupydroidMustList >"$EVIDENCE/logcat.txt" 2>&1 || true

if grep -q "OK (" "$EVIDENCE/instrumentation.txt"; then
  echo "PASS: the probe ran to its end; the report is in $EVIDENCE/must-list-report.txt"
else
  echo "FAIL: the probe did not finish; read $EVIDENCE/instrumentation.txt and must-list-report.txt" >&2
  exit 1
fi
