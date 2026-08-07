#!/usr/bin/env bash
# Seeds a notebook that exercises every coloured syntax kind, then runs the U1 palette flow
# once per system theme.
#
# Two runs rather than one, and that is the whole point of the step: the palette used to come
# from `MaterialTheme.colorScheme`, so a single-theme screenshot could not tell a working
# palette from one that had quietly collapsed onto the wallpaper's colours. Audit A4 was
# exactly that failure - black source that read fine on light and was invisible on dark.
#
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook), so it
# would only ever prove the "No cells yet" branch. The seeded file lands in the app's own
# storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/u1-palette.ipynb"
FLOW="$ROOT/.maestro/u1-palette.yaml"
EVIDENCE="$ROOT/evidence/u1-tokens"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/u1-palette.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator
# and a phone attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"

seed_fixture() {
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell run-as "$APP" mkdir -p files/notebooks
  # base64 on the command line rather than piping to `cat`: adb shell can mangle
  # stdin line endings, and the fixture is small enough to pass as an argument.
  local payload
  payload="$(base64 -w0 "$FIXTURE")"
  "$ADB" shell "run-as $APP sh -c 'echo $payload | base64 -d > $REMOTE'"
  "$ADB" shell run-as "$APP" test -s "$REMOTE" ||
    { echo "fixture did not land at $REMOTE"; exit 1; }
}

run_in_theme() {
  local theme="$1" night="$2"
  echo "=== $theme (uimode night $night) ==="
  # `cmd uimode night` is the system-wide setting isSystemInDarkTheme() reads. Set before the
  # launch, not after: a mid-flow change recreates the activity and loses the opened notebook.
  "$ADB" shell cmd uimode night "$night"
  seed_fixture

  local out="$EVIDENCE/$theme"
  mkdir -p "$out"
  if [ -n "$MAESTRO_DEVICE" ]; then
    "$MAESTRO" --device "$MAESTRO_DEVICE" test "$FLOW" 2>&1 | tee "$out/maestro.log"
  else
    "$MAESTRO" test "$FLOW" 2>&1 | tee "$out/maestro.log"
  fi

  # Screenshots do not land in the working directory: maestro writes every artefact under
  # ~/.maestro/tests/<timestamp>/<flow>/ and never says where. Taking the newest run directory
  # is safe here only because the copy happens immediately after the run it belongs to.
  local run_dir
  run_dir="$(ls -1dt "$HOME/.maestro/tests"/*/"$(basename "$FLOW" .yaml)" 2>/dev/null | head -1)"
  [ -n "$run_dir" ] || { echo "no maestro run directory found for $FLOW"; exit 1; }
  cp "$run_dir/takeScreenshot"/*.png "$out/"
  cp "$run_dir/logs/device-logcat.txt" "$out/logcat.txt"
}

run_in_theme dark yes
run_in_theme light no

# Left where it was found rather than forced back to a default, so a subsequent flow inherits
# the device's own setting instead of one this script invented.
"$ADB" shell cmd uimode night auto

echo
echo "screenshots: $EVIDENCE/{dark,light}/"
