#!/usr/bin/env bash
# JD-069's demo: the two bumped jars working inside the app a user installs. The APK is
# installed over the one on the tablet (adb install -r), a Space is created - the ~14 s
# image copy is SpaceImageInstaller -> TarZstdExtractor, commons-compress's
# TarArchiveInputStream and ZstdCompressorInputStream, which is the jar that carries
# commons-lang3 - a cell in base emits `display(HTML("..."))`, whose `text/html` renders
# through HtmlDocument (jsoup), and after a force-stop the Space persists and the cell
# renders the same.
#
#   nice -n 10 timeout 2400 scripts/apk-advisories-demo-driver.sh
#
# Build first: tools/rootfs/stage-app-artifacts.sh arm64-v8a, then ./gradlew :app:assembleDebug.
# Upgrades in place (`adb install -r`), so the app's data, and every sign-in in it, stays.
#
# Exits non-zero if a flow fails, and if the lockfile does not resolve the two fixed
# versions on releaseRuntimeClasspath - then the build under test is not the bumped one
# and the run would prove nothing.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and the cell runs on a real kernel.
#
# NOT A SUITE FLOW: it removes every user Space on the device, so the create lands as
# space-1 on a repeat run.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/apk-advisories-demo"
FLOWS="$ROOT/.maestro/apk-advisories-demo"
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
# The demo cannot tell 1.23.1 from 1.23.2 on screen - what it can refuse is a build whose
# resolution is not the bumped one. The scanner's own evidence is the lockfile line.
grep -q '^org\.jsoup:jsoup:1\.23\.2=.*releaseRuntimeClasspath' "$LOCKFILE" ||
  fail "gradle.lockfile does not resolve jsoup 1.23.2 on releaseRuntimeClasspath: build the bumped tree"
grep -q '^org\.apache\.commons:commons-lang3:3\.18\.0=.*releaseRuntimeClasspath' "$LOCKFILE" ||
  fail "gradle.lockfile does not resolve commons-lang3 3.18.0 on releaseRuntimeClasspath: build the bumped tree"

require_installed_app
mkdir -p "$EVIDENCE"
: >"$CAPTURES"
capture "the lockfile's two fixed versions" grep -E '^(org\.jsoup:jsoup|org\.apache\.commons:commons-lang3):.*releaseRuntimeClasspath' "$LOCKFILE"

"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" install -r "$APK" 2>&1 | tr -d '\r' >"$EVIDENCE/install.txt"
# A Space left by an earlier run - this driver's or jd-002's - makes the create land as
# space-2, which the flows do not wait on.
in_app "rm -rf files/spaces/space-*"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/apk-advisories.ipynb" "Apk Advisories"
capture "files/spaces before the create" in_app "ls files/spaces"

run_flow "$FLOWS-create-and-html.yaml" "$EVIDENCE/created-and-html" || fail "part 1 failed"
capture "files/spaces after the create" in_app "ls files/spaces"
capture "space-1's config" in_app "cat files/spaces/space-1/config.json"
capture "the image landed: the interpreter is entry 466 of 22,609" \
  in_app "ls files/spaces/space-1/usr/bin/python3"
capture "no half-extract marker remains" \
  in_app "ls files/spaces/space-1/.jupydroid/image-extracting"

# Re-seeded so the relaunch starts with no output saved by the first part.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
seed_notebook "$ROOT/.maestro/fixtures/apk-advisories.ipynb" "Apk Advisories"
run_flow "$FLOWS-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
capture "files/spaces after the relaunch" in_app "ls files/spaces"

echo "space-1 was extracted through commons-compress, text/html rendered through jsoup, and both survived a relaunch"
