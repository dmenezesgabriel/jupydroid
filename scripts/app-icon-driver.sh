#!/usr/bin/env bash
# The launcher-icon item end to end on the arm64 tablet: a fresh install wears the blue alias,
# Settings' App icon row flips the pair through PackageManager's component state, and the
# launcher drawer, Recents and Settings > Apps follow - across a killed process and an
# `install -r` - then blue is restored so the device is left as a fresh install would find it.
#
#   ANDROID_SERIAL=<tablet> scripts/app-icon-driver.sh
#
# TABLET ONLY by policy rather than mechanism: nothing runs a cell, but the item exists to
# change what the launcher draws and only this device's evidence counts. Installs through
# fresh_install, so the run starts from no app data at all - which is also what makes the blue
# default a measurement rather than a leftover.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/app-icon"

fresh_install "$OUT"
reset_notebooks
record_device "$OUT"
require_unlocked_device
clear_system_anr

mapfile -t DEVICE_ADB < <(device_adb)

# Which alias the platform resolves as the app's launcher entry - `.BlueOrbitIcon` or
# `.BlackOrbitIcon`, the same answer launcher, Recents and Settings > Apps read.
resolved_alias() {
  "${DEVICE_ADB[@]}" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$APP" |
    tr -d '\r' | grep -oE '\.(Blue|Black)OrbitIcon' | sed -n '1p'
}

require_alias() {
  local expected="$1" label="$2" actual
  actual="$(resolved_alias)"
  echo "resolve-activity at $label: ${actual:-<none>}" | tee "$OUT/resolve-$label.txt"
  [ "$actual" = "$expected" ] || {
    echo "expected .$expected at $label, got '${actual:-<none>}'" >&2
    return 1
  }
}

# A screenshot plus the window hierarchy of whatever is on screen, so the frame and the tree
# that produced it land together - the dump is what says which surface the frame actually is.
capture() {
  "${DEVICE_ADB[@]}" exec-out screencap -p >"$1"
  "${DEVICE_ADB[@]}" shell uiautomator dump /sdcard/app-icon.xml >/dev/null 2>&1 || true
  "${DEVICE_ADB[@]}" exec-out cat /sdcard/app-icon.xml >"${1%.png}.xml" 2>/dev/null || true
}

# Home, then a swipe up into the app drawer - retried, because whether the first swipe lands on
# the drawer or a home page depends on where the launcher was left. The dump, not the frame, is
# what says JupyterDroid is listed.
open_app_drawer() {
  "${DEVICE_ADB[@]}" shell input keyevent KEYCODE_HOME
  sleep 1
  for _ in 1 2 3; do
    "${DEVICE_ADB[@]}" shell input swipe 960 900 960 300
    sleep 1
    if "${DEVICE_ADB[@]}" shell uiautomator dump /sdcard/drawer-check.xml >/dev/null 2>&1 &&
      "${DEVICE_ADB[@]}" exec-out cat /sdcard/drawer-check.xml | tr -d '\r' | grep -q "JupyterDroid"; then
      return 0
    fi
  done
  echo "the app drawer never listed JupyterDroid" >&2
  return 1
}

# The three surfaces the item names, in the order a person checks them: the drawer's icon, the
# Recents card, and Settings > Apps. Each lands as <label>-<surface>.png + .xml.
surface_captures() {
  local label="$1"
  open_app_drawer
  capture "$OUT/$label-launcher.png"

  "${DEVICE_ADB[@]}" shell input keyevent KEYCODE_APP_SWITCH
  sleep 2
  capture "$OUT/$label-recents.png"
  "${DEVICE_ADB[@]}" shell input keyevent KEYCODE_BACK
  sleep 1

  "${DEVICE_ADB[@]}" shell am start \
    -a android.settings.APPLICATION_DETAILS_SETTINGS -d "package:$APP" >/dev/null
  sleep 3
  capture "$OUT/$label-appinfo.png"
  "${DEVICE_ADB[@]}" shell input keyevent KEYCODE_BACK
  sleep 1
}

# --- Blue, as a cold install ships it -------------------------------------
require_alias ".BlueOrbitIcon" "blue-cold"
"${DEVICE_ADB[@]}" shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
surface_captures "blue"

# --- Settings flips the pair ----------------------------------------------
run_flow "$ROOT/.maestro/app-icon-black.yaml" "$OUT/to-black" || exit 1
require_alias ".BlackOrbitIcon" "black-selected"
# The swap evicts the task the old alias launched from Recents, so the surfaces are captured
# against a task rooted at the new alias - the same cold-launch path a person takes.
"${DEVICE_ADB[@]}" shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
surface_captures "black"

# --- The choice survives the process dying and an update ------------------
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
sleep 1
"${DEVICE_ADB[@]}" shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
require_alias ".BlackOrbitIcon" "black-after-relaunch"

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "no APK at $APK: build it with ./gradlew :app:assembleDebug" >&2; exit 1; }
"${DEVICE_ADB[@]}" install -r "$APK" >/dev/null
require_alias ".BlackOrbitIcon" "black-after-update"

# --- Blue restored, and the surfaces answer again --------------------------
run_flow "$ROOT/.maestro/app-icon-blue.yaml" "$OUT/to-blue" || exit 1
require_alias ".BlueOrbitIcon" "blue-restored"
"${DEVICE_ADB[@]}" shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
surface_captures "blue-restored"

echo "app-icon demo complete: evidence in $OUT"
