#!/usr/bin/env bash
# Runs the adaptive-panes flow that matches the width the device it is given actually has.
#
# **Two devices, not one device wearing a costume**, for the reason
# `action-presentation-driver.sh` records at length: forcing a width with `wm density` produced
# windows no phone has and assertions had to be weakened to survive them.
#
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/adaptive-panes-driver.sh   # 1280dp -> two panes
#   ANDROID_SERIAL=emulator-5554 scripts/adaptive-panes-driver.sh   #  320dp -> one pane
#
# The flow is chosen from the *measured* width and the evidence directory is named from it, so a
# run cannot assert two panes against a window with room for one, and cannot be mislabelled.
#
# Two notebooks, because one pane and two are indistinguishable with a single notebook: the claim
# is that a notebook the editor never shows is on screen beside the one it does. The fixtures are
# `notebook-switch`'s - disjoint titles and disjoint cell sources, which is exactly what this
# needs and what that flow already established.
#
# Runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook + window_width_dp

WIDTH_DP="$(window_width_dp)"
# The same 840dp boundary `isWindowWideEnoughForTwoPanes` uses.
if [ "$WIDTH_DP" -ge 840 ]; then FLOW=adaptive-two-panes; else FLOW=adaptive-one-pane; fi
echo "=== ${FLOW}, ${WIDTH_DP}dp wide ==="

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/switch-alpha.ipynb" "Switch Alpha"
seed_notebook "$ROOT/.maestro/fixtures/switch-beta.ipynb" "Switch Beta"

run_flow "$ROOT/.maestro/$FLOW.yaml" "$ROOT/evidence/adaptive-panes/${FLOW}-${WIDTH_DP}dp"
