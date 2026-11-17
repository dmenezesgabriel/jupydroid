#!/usr/bin/env bash
# The instrumented suite's verdict, read off the instrument log. Installs the app and test APKs
# `assembleDebug` and `assembleDebugAndroidTest` last built - through adb, the way
# scripts/backup-driver.sh does, and never through `connectedDebugAndroidTest`, which uninstalls
# the app it just proved - then runs `am instrument -w` and answers pass or fail off the log.
#
#   ANDROID_SERIAL=<serial> scripts/instrumented-suite-driver.sh
#   ANDROID_SERIAL=<serial> scripts/instrumented-suite-driver.sh com.jupydroid.app.TwoPaneLayoutTest [class ...]
#
# No arguments runs the whole suite; arguments name the classes one `-e class` list runs.
#
# NOT A SUITE FLOW: a measurement driver over the APKs already built - a sweep drives flows on
# the installed build, it does not instrument. Runs wherever ANDROID_SERIAL points; the window
# is a property of the device chosen, not of this script.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"

OUT="$ROOT/evidence/instrumented-suite/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
mapfile -t DEVICE_ADB < <(device_adb)

[ -f "$APP_APK" ] || { echo "no APK at $APP_APK: build it with ./gradlew :app:assembleDebug" >&2; exit 1; }
[ -f "$TEST_APK" ] || { echo "no test APK at $TEST_APK: build it with ./gradlew :app:assembleDebugAndroidTest" >&2; exit 1; }

echo "--- install ---"
"${DEVICE_ADB[@]}" install -r "$APP_APK" 2>&1 | tee "$OUT/install-app.txt"
"${DEVICE_ADB[@]}" install -r "$TEST_APK" 2>&1 | tee "$OUT/install-test.txt"

instrument_args=(-w)
if [ "$#" -gt 0 ]; then
  class_list="$(printf '%s,' "$@")"
  instrument_args+=(-e class "${class_list%,}")
fi

echo "--- instrument ---"
"${DEVICE_ADB[@]}" shell am instrument "${instrument_args[@]}" "$RUNNER" 2>&1 |
  tee "$OUT/instrumentation.txt"
echo

if grep -q "^OK (" "$OUT/instrumentation.txt"; then
  echo "PASS: $(grep "^OK (" "$OUT/instrumentation.txt" | tail -1)"
  exit 0
fi
grep -E "^[0-9]+\) " "$OUT/instrumentation.txt" | head -30
echo "FAIL: the suite did not pass; the log is at $OUT/instrumentation.txt" >&2
exit 1
