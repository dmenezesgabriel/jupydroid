#!/usr/bin/env bash
# JD-070's demo: the bumped zstd-jni .so working inside the app a user installs. The APK is
# installed over the one on the tablet (adb install -r), a Space is created - the ~14 s
# image copy is SpaceImageInstaller -> TarZstdExtractor -> commons-compress's
# ZstdCompressorInputStream -> com.github.luben.zstd, which is the arm64-v8a
# libzstd-jni-1.5.7-24.so itself, the file this item repairs - and after a force-stop the
# Space persists.
#
#   nice -n 10 timeout 2400 scripts/native-libs-relro-demo-driver.sh
#
# Build first: tools/rootfs/stage-app-artifacts.sh arm64-v8a, then ./gradlew :app:assembleDebug.
# Upgrades in place (`adb install -r`), so the app's data, and every sign-in in it, stays.
#
# Exits non-zero if a flow fails, if the lockfile does not resolve zstd-jni 1.5.7-24 on
# releaseRuntimeClasspath, or if the measure reads a .so in the built APK below full RELRO -
# then the build under test is not the bumped one and the run would prove nothing.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs - and the arm64 .so is the one that had
# no RELRO at all. Runs no cell.
#
# NOT A SUITE FLOW: it removes every user Space on the device, so the create lands as
# space-1 on a repeat run.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/native-libs-relro-demo"
FLOWS="$ROOT/.maestro/native-libs-relro-demo"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
LOCKFILE="$ROOT/app/gradle.lockfile"
CAPTURES="$EVIDENCE/captures.txt"

mapfile -t DEVICE_ADB < <(device_adb)

fail() { echo "$1" >&2; exit 1; }

in_app() { "${DEVICE_ADB[@]}" shell "run-as $APP sh -c '$1'" | tr -d '\r'; }

capture() { # capture <label> <command...>
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  local label="$1"
  shift
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

MODEL="$("${DEVICE_ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) fail "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'" ;;
esac
[ -f "$APK" ] || fail "no APK at $APK: build it with ./gradlew :app:assembleDebug"
# The demo cannot tell a full-RELRO .so from one below it on screen - what it can refuse is
# a build whose resolution is not the bumped one. The scanner's own evidence is the lockfile
# line and the measure's table.
grep -q '^com\.github\.luben:zstd-jni:1\.5\.7-24=.*releaseRuntimeClasspath' "$LOCKFILE" ||
  fail "gradle.lockfile does not resolve zstd-jni 1.5.7-24 on releaseRuntimeClasspath: build the bumped tree"

require_installed_app
mkdir -p "$EVIDENCE"
: >"$CAPTURES"
capture "the lockfile's zstd-jni" \
  grep -E '^com\.github\.luben:zstd-jni:.*releaseRuntimeClasspath' "$LOCKFILE"

# The item's claim is the measure's output, kept beside the flows that exercise the .so.
python3 "$ROOT/scripts/read_apk_native_libs.py" "$APK" >"$EVIDENCE/native-libs.tsv"
awk 'NR == 1 { next } { rows++; if ($2 != "full") bad = 1 } END { exit (rows == 0 || bad) }' \
  "$EVIDENCE/native-libs.tsv" ||
  fail "a .so in the APK under test reads below full RELRO: $EVIDENCE/native-libs.tsv"

"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" install -r "$APK" 2>&1 | tr -d '\r' >"$EVIDENCE/install.txt"
# A Space left by an earlier run - this driver's or jd-002's - makes the create land as
# space-2, which the flows do not wait on.
in_app "rm -rf files/spaces/space-*"
capture "files/spaces before the create" in_app "ls files/spaces"

run_flow "$FLOWS-create.yaml" "$EVIDENCE/created" || fail "part 1 failed"
capture "files/spaces after the create" in_app "ls files/spaces"
capture "space-1's config" in_app "cat files/spaces/space-1/config.json"
capture "the image landed" in_app "ls files/spaces/space-1/usr/bin/python3"
capture "no half-extract marker remains" \
  in_app "ls files/spaces/space-1/.jupydroid/image-extracting"

"${DEVICE_ADB[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
capture "files/spaces after the relaunch" in_app "ls files/spaces"

echo "space-1 extracted through libzstd-jni-1.5.7-24 - every APK .so at full RELRO - and persisted a relaunch"
