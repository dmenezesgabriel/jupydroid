#!/usr/bin/env bash
# Runs the U5b flow at the width the device it is given actually has.
#
# **Two devices, not one device wearing a costume.** Cell actions branch on window width, so the
# evidence has to come from a genuinely narrow window and a genuinely wide one:
#
#   ANDROID_SERIAL=emulator-5554 scripts/u5b-action-presentation-driver.sh   # 320dp -> sheet
#   ANDROID_SERIAL=TABLET_SERIAL   scripts/u5b-action-presentation-driver.sh   # 800dp -> menu
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

# Asked of the framework rather than computed, and that is the point.
#
# `am get-config` reports the configuration the app actually runs under - `w1280dp` on the
# landscape tablet, `w320dp` on the portrait emulator - which is the same number
# `WindowSizeClass` reads. Every attempt to derive it instead got it wrong in a different way:
# `wm size` reports the *natural* size, so "the larger edge" is the width on a landscape device
# and the height on a portrait one, and a run in the wrong class still looks green.
window_width_dp() {
  "$ADB" shell am get-config | tr '-' '\n' | sed -n 's/^w\([0-9]\+\)dp$/\1/p' | head -1
}

WIDTH_DP="$(window_width_dp)"
# The same 600dp boundary `cellActionPresentationFor` uses.
if [ "$WIDTH_DP" -ge 600 ]; then CLASS=expanded; else CLASS=compact; fi
echo "=== ${CLASS}, ${WIDTH_DP}dp wide ==="

seed_notebook "$ROOT/.maestro/fixtures/u5a-cell-actions.ipynb" u5a-cell-actions
run_flow "$ROOT/.maestro/u5b-action-presentation.yaml" \
  "$ROOT/evidence/u5b-action-presentation/${CLASS}-${WIDTH_DP}dp"

# Three cells: the action the surface offered actually reshaped the notebook. The screenshots
# show *which* surface appeared; only the file shows that it worked.
echo "--- notebook as saved after the flow ---"
"$ADB" shell run-as com.jupydroid.app cat files/notebooks/u5a-cell-actions.ipynb
