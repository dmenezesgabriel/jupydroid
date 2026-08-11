#!/usr/bin/env bash
# Runs the U6 IME-layout flow and names the evidence after the width it measured.
#
# **The phone emulator is the device that matters here**, and the tablet is nearly useless for
# it: at 800dp of height the keyboard takes a third of the window and everything still fits. The
# question - does the cell being typed into stay on screen - only has an answer on a 640dp-high
# phone, which is why U1a's "the focused cell is covered" note was written on a tablet and turned
# out to be overstated.
#
#   ANDROID_SERIAL=emulator-5554 scripts/u6-ime-layout-driver.sh   # 320x640dp: the real test
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/u6-ime-layout-driver.sh   # 1280x800dp, for comparison
#
# Runs no cell, so it needs no Space runtime. The other half of U6 - the completion popup, which
# does need a kernel - is U6a and lives in evidence/u6-completion-popup/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook + window_width_dp

WIDTH_DP="$(window_width_dp)"
echo "=== ${WIDTH_DP}dp wide ==="

# The handwriting IME steals focus from text fields, and this flow is about a focused field.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/u6-ime-layout.ipynb" "Ime Layout"

run_flow "$ROOT/.maestro/u6-ime-layout.yaml" "$ROOT/evidence/u6-ime-layout/${WIDTH_DP}dp"
