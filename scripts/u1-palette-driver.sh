#!/usr/bin/env bash
# Seeds a notebook that exercises every coloured syntax kind, then runs the U1 palette flow
# once per system theme.
#
# Two runs rather than one, and that is the whole point of the step: the palette used to come
# from `MaterialTheme.colorScheme`, so a single-theme screenshot could not tell a working
# palette from one that had quietly collapsed onto the wallpaper's colours. Audit A4 was
# exactly that failure - black source that read fine on light and was invisible on dark.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/u1-palette.ipynb"
FLOW="$ROOT/.maestro/u1-palette.yaml"
EVIDENCE="$ROOT/evidence/u1-tokens"

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
  seed_notebook "$FIXTURE" u1-palette
  run_flow "$FLOW" "$EVIDENCE/$theme"
}

run_in_theme dark yes
run_in_theme light no

# Left on auto rather than forced back to a value this script invented, so a subsequent flow
# inherits the device's own setting.
"$ADB" shell cmd uimode night auto

echo
echo "screenshots: $EVIDENCE/{dark,light}/"
