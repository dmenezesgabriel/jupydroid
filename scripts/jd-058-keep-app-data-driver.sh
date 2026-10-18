#!/usr/bin/env bash
# What the user made survives an uninstall that keeps the app's data: base's documents, the theme,
# and opencode installed into base's rootfs, after Settings → Apps → Uninstall with Keep app data
# ticked and an `adb install` of the same APK.
#
#   nice -n 10 timeout 2400 scripts/jd-058-keep-app-data-driver.sh [evidence subdirectory, t1 by default]
#
# Exits non-zero if a flow fails, if the uninstall did not keep the data (`pm list packages -u`
# still lists a package whose data was kept, and `pm list packages` does not), if a file listed
# before is missing after, or if base's terminal cannot run opencode after the reinstall.
#
# TABLET ONLY (SM-X216B): base is entered and its terminal runs opencode, and only arm64 ships a
# rootfs. Needs the tablet online, because opencode is fetched from the npm registry. Installs the
# APK that `./gradlew :app:assembleDebug` last built, through adb rather than Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-058-keep-app-data/${1:-t1}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
OPENCODE="files/spaces/base/usr/local/bin/opencode"

mapfile -t DEVICE_ADB < <(device_adb)

fail() { echo "$1" >&2; exit 1; }

# What survives is read off the filesystem as well as the screen: a listing of the workspace, the
# theme store and opencode's binary, the same commands before and after.
record_state() {
  local out="$1"
  {
    echo "\$ ls -la $NOTEBOOK_DIR"
    "${DEVICE_ADB[@]}" shell run-as "$APP" ls -la "$NOTEBOOK_DIR"
    echo "\$ cat $THEME_SETTINGS_STORE"
    "${DEVICE_ADB[@]}" shell run-as "$APP" cat "$THEME_SETTINGS_STORE"
    echo
    echo "\$ ls -l $OPENCODE"
    "${DEVICE_ADB[@]}" shell run-as "$APP" ls -l "$OPENCODE"
  } | tr -d '\r' >"$out"
}

# The uninstall flow ends on OK; the package goes once the installer has done its work.
wait_for_uninstall() {
  local tries=0
  while "${DEVICE_ADB[@]}" shell pm list packages "$APP" | grep -qx "package:$APP"; do
    [ "$tries" -lt 30 ] || fail "$APP is still installed 30 s after the uninstall's OK"
    sleep 1
    tries=$((tries + 1))
  done
}

[ -f "$APK" ] || fail "no APK at $APK: build it with ./gradlew :app:assembleDebug"
require_installed_app
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-058-survives.ipynb" "survives.ipynb"
seed_file "$ROOT/.maestro/fixtures/jd-058-notes.md" "notes.md"
seed_file "$ROOT/.maestro/fixtures/jd-058-talk.chat" "talk.chat"
seed_file "$ROOT/.maestro/fixtures/jd-046-opencode-probe.sh" "jd046-probe.sh"
mkdir -p "$EVIDENCE"

# opencode goes into base's rootfs exactly as jd-046-opencode-in-a-space-driver.sh installs it.
run_flow "$ROOT/.maestro/jd-046-opencode-in-a-space.yaml" "$EVIDENCE" || exit 1
wait_for_workspace_text jd046-probe.txt probe-done 900 || exit 1
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/jd046-probe.sh\" \"$NOTEBOOK_DIR/jd046-probe.txt\""

run_flow "$ROOT/.maestro/jd-058-keep-app-data-part1-before.yaml" "$EVIDENCE" || exit 1
record_state "$EVIDENCE/state-before.txt"

"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d "package:$APP" >/dev/null
run_flow "$ROOT/.maestro/jd-058-keep-app-data-part2-uninstall.yaml" "$EVIDENCE" || exit 1
wait_for_uninstall
# `-u` lists a package uninstalled with DELETE_KEEP_DATA as well as installed ones; plain does not.
kept="$("${DEVICE_ADB[@]}" shell pm list packages -u "$APP" | tr -d '\r')"
{
  echo "\$ pm list packages $APP"
  "${DEVICE_ADB[@]}" shell pm list packages "$APP"
  echo "\$ pm list packages -u $APP"
  echo "$kept"
} | tr -d '\r' >"$EVIDENCE/reinstall.txt"
grep -qx "package:$APP" <<<"$kept" || fail "the uninstall kept no data: pm list packages -u does not list $APP"
{
  echo "\$ adb install app-debug.apk"
  "${DEVICE_ADB[@]}" install "$APK"
} 2>&1 | tr -d '\r' | tee -a "$EVIDENCE/reinstall.txt"

record_state "$EVIDENCE/state-after.txt"
diff <(grep -v '^\$' "$EVIDENCE/state-before.txt" | awk '{print $NF}') \
  <(grep -v '^\$' "$EVIDENCE/state-after.txt" | awk '{print $NF}') ||
  fail "what was there before the uninstall is not all there after it"

run_flow "$ROOT/.maestro/jd-058-keep-app-data-part3-after.yaml" "$EVIDENCE" || exit 1
wait_for_workspace_text jd058-opencode-version.txt version-rc=0 120 || exit 1
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/jd058-opencode-version.txt\"" | tr -d '\r' \
  >"$EVIDENCE/opencode-version-after.txt"
