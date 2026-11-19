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

# A `wm size` override survives the session that set it, and the suite reads the window it is
# handed: under a stale 360x640 the tab-strip's sibling menu and the console row's width each
# "passed" a run they fail at the physical 320x640. The override is reset so the window the log
# claims is the window the device has.
"${DEVICE_ADB[@]}" shell wm size reset > /dev/null

# SystemUI's "isn't responding" dialog took window focus for a whole run on dev36 (it was up from
# before the first test to the last), and every Espresso call that needs the root's focus - pressBack,
# closeSoftKeyboard - failed with `has-window-focus=false` while Compose-only assertions kept passing.
# The tests are not at fault, so the run does not start with the dialog up.
clear_system_anr || { echo "a system dialog holds window focus and could not be cleared" >&2; exit 1; }

# A run that fails at `has-window-focus=false` says nothing about when focus went. The sampler stamps
# the focused window every two seconds, and logcat's `TestRunner: started:` lines say which class ran
# at each stamp; `logcat -c` first, so the log is this run's and not the device's last hour.
# Kept on every exit, a pass included: a dialog that never cost a test is still a finding.
sampler_pid=""
# shellcheck disable=SC2329 # invoked by the EXIT trap below
keep_the_run_evidence() {
  [ -z "$sampler_pid" ] || kill "$sampler_pid" 2> /dev/null || true
  [ -z "$sampler_pid" ] || wait "$sampler_pid" 2> /dev/null || true
  "${DEVICE_ADB[@]}" logcat -d > "$OUT/logcat.txt" 2>&1 || true
  echo "focus: $(python3 "$ROOT/scripts/focus_samples.py" summary "$OUT/focus-samples.jsonl" 2>&1)"
}
trap keep_the_run_evidence EXIT
"${DEVICE_ADB[@]}" logcat -c
python3 "$ROOT/scripts/focus_samples.py" record --serial "$MAESTRO_DEVICE" --adb "$ADB" \
  --out "$OUT/focus-samples.jsonl" &
sampler_pid=$!

echo "--- instrument ---"
"${DEVICE_ADB[@]}" shell am instrument "${instrument_args[@]}" "$RUNNER" 2>&1 |
  tee "$OUT/instrumentation.txt"
echo

if grep -q "^OK (" "$OUT/instrumentation.txt"; then
  echo "PASS: $(grep "^OK (" "$OUT/instrumentation.txt" | tail -1)"
  exit 0
fi
grep -E "^[0-9]+\) " "$OUT/instrumentation.txt" | head -30
# Named so a `has-window-focus=false` failure says whose window had it.
echo "focus at the end: $("${DEVICE_ADB[@]}" shell dumpsys window | grep -m1 mCurrentFocus)" >&2
echo "FAIL: the suite did not pass; the log is at $OUT/instrumentation.txt" >&2
exit 1
