#!/usr/bin/env bash
# Seeds a notebook that exercises every coloured syntax kind, then runs the palette flow
# once per system theme.
#
# Two runs rather than one, and that is the whole point: the palette used to come from
# `MaterialTheme.colorScheme`, so a single-theme screenshot could not tell a working palette from
# one that had quietly collapsed onto the wallpaper's colours. The palette audit found exactly that
# failure - black source that read fine on light and was invisible on dark.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime. Run it on both, and
# give the second one a sub-directory:
#
#   ANDROID_SERIAL=emulator-5554  scripts/palette-driver.sh
#   ANDROID_SERIAL=TABLET_SERIAL    scripts/palette-driver.sh tablet
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/palette.ipynb"
FLOW="$ROOT/.maestro/palette.yaml"
# An optional sub-directory, so a second device does not overwrite the first device's frames -
# which is what happened the first time this ran on the tablet after the emulator. With no argument
# the frames land in the root; `palette-driver.sh tablet` writes `tablet/{dark,light}` beneath it.
# Same shape as app-chrome-driver.sh.
EVIDENCE="$ROOT/evidence/palette${1:+/$1}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

run_in_theme() {
  local theme="$1" night="$2"
  echo "=== $theme (uimode night $night) ==="
  # `cmd uimode night` is the system-wide setting isSystemInDarkTheme() reads. Set before the
  # launch, not after: a mid-flow change recreates the activity and loses the opened notebook.
  "$ADB" shell cmd uimode night "$night"
  reset_notebooks
  seed_notebook "$FIXTURE" "Palette Check"
  run_flow "$FLOW" "$EVIDENCE/$theme"
}

run_in_theme dark yes
run_in_theme light no

# Left on auto rather than forced back to a value this script invented, so a subsequent flow
# inherits the device's own setting.
"$ADB" shell cmd uimode night auto

echo
echo "screenshots: $EVIDENCE/{dark,light}/"
