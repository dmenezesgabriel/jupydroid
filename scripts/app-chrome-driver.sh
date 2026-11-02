#!/usr/bin/env bash
# Seeds a workspace and a document that reach every Material role the VS Code ramp re-sources, then runs the
# chrome flow once per system theme.
#
# **The evidence is a pair, because the change is a palette.** Every colour the app draws its
# chrome with moves from IntelliJ's ramp to the resolved VS Code 2026 themes, and no assertion can
# see that - so the deliverable is the same five frames before and after, in both themes. Run once
# on the build that predates the change and once on the build that carries it:
#
#   ANDROID_SERIAL=emulator-5554 scripts/app-chrome-driver.sh before
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/app-chrome-driver.sh before-tablet
#
# **Two themes rather than one**: a single-theme capture cannot tell a working palette from one
# that has collapsed onto a value that happens to read in that theme. An audit found exactly that
# - black source that read on light and was invisible on dark.
#
# **Both devices, because the step's gate asks for both** and they disagree about width: the
# emulator is 320dp, the tablet 1280dp, and the two-pane shell only exists on the second. A palette
# judged on one of them is a palette judged on half the surfaces that draw it.
#
# No cell is run - the markdown cell renders from the file - so this needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLOW="$ROOT/.maestro/app-chrome.yaml"
# The half of the pair this run is capturing. `after` unless the caller says otherwise, because
# `before` can only be captured deliberately - from a checkout that predates the change.
EVIDENCE="$ROOT/evidence/app-chrome/${1:-after}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)

run_in_theme() {
  local theme="$1" night="$2"
  echo "=== $theme (uimode night $night) ==="
  # `cmd uimode night` is the system-wide setting isSystemInDarkTheme() reads. Set before the
  # launch, not after: a mid-flow change recreates the activity and loses the opened notebook.
  "${adb[@]}" shell cmd uimode night "$night"
  reset_notebooks
  # A folder, a notebook inside it and a file that is neither: the three row kinds the explorer's
  # chrome has to tell apart, reusing `explorer-browse-driver.sh`'s own fixtures rather than
  # minting near-copies.
  seed_notebook "$ROOT/.maestro/fixtures/app-chrome.ipynb" "Chrome Check"
  seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
  seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
  run_flow "$FLOW" "$EVIDENCE/$theme"
}

run_in_theme dark yes
run_in_theme light no

# Left on auto rather than forced back to a value this script invented, so a subsequent flow
# inherits the device's own setting.
"${adb[@]}" shell cmd uimode night auto

echo
echo "screenshots: $EVIDENCE/{dark,light}/"
