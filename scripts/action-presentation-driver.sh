#!/usr/bin/env bash
# Runs the action-presentation flow at the width the device it is given actually has.
#
# **Two devices, not one device wearing a costume.** Cell actions branch on window width, so the
# evidence has to come from a genuinely narrow window and a genuinely wide one:
#
#   ANDROID_SERIAL=emulator-5554 scripts/action-presentation-driver.sh   # 320dp -> sheet
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/action-presentation-driver.sh   # 800dp -> menu
#
# An earlier version forced the tablet narrow with `wm density` instead, and that was a mistake
# worth recording rather than quietly deleting. Two hardcoded overrides silently produced *wide*
# windows - `wm size 1233x2400` made the larger number the width on a landscape device, and
# `wm density 480` over the 1920px landscape edge is 640dp - so both "compact" runs got the
# dropdown and looked green. Computing the density fixed the width but not the shape: the result
# was a 450x281dp letterbox, and assertions had to be weakened to survive a window no phone has.
# The width is now read from `am get-config`, not computed - see `window_width_dp`.
#
# The evidence directory is named from the *measured* width, so a run cannot be mislabelled.
#
# Runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"   # from maestro-run.sh; asked of the framework, never derived
# The same 600dp boundary `cellActionPresentationFor` uses.
if [ "$WIDTH_DP" -ge 600 ]; then CLASS=expanded; else CLASS=compact; fi
echo "=== ${CLASS}, ${WIDTH_DP}dp wide ==="

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/cell-actions.ipynb" "Cell Actions"
run_flow "$ROOT/.maestro/action-presentation.yaml" \
  "$ROOT/evidence/action-presentation/${CLASS}-${WIDTH_DP}dp"

# Three cells: the action the surface offered actually reshaped the notebook. The screenshots
# show *which* surface appeared; only the file shows that it worked.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Cell Actions.ipynb\""
