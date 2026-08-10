#!/usr/bin/env bash
# Runs the U5b flow twice: once at the device's own width, once forced to a phone width.
#
# `wm density` rather than a second device, and rather than `wm size`. The rule under test is
# about the window's width **in dp**, not in pixels, so raising the density narrows the window in
# exactly the unit the rule reads.
#
# The density is *computed* from the window rather than hardcoded, and that is the whole lesson
# here. Two hardcoded attempts both produced a wide window and a green-looking compact run:
# `wm size 1233x2400` made the larger number the width (1600dp), and `wm density 480` forgot that
# this tablet is held in landscape - so the 1920px edge is the width, and 480dpi over it is
# 640dp. Deriving the number from `wm size` cannot make either mistake, on any device, in either
# orientation.
#
# Runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

FLOW="$ROOT/.maestro/u5b-action-presentation.yaml"
EVIDENCE="$ROOT/evidence/u5b-action-presentation"

# Restored however this exits: leaving a device at 411dp would silently narrow every flow run
# after this one, and that is the kind of thing that is found three steps later.
restore_density() { "$ADB" shell wm density reset >/dev/null 2>&1 || true; }
trap restore_density EXIT

run_at() {
  local name="$1" density="$2"
  echo "=== $name ==="
  if [ "$density" = "native" ]; then "$ADB" shell wm density reset; else "$ADB" shell wm density "$density"; fi
  echo "window width: $("$ADB" shell wm size | tail -1) at $("$ADB" shell wm density | tail -1)"
  seed_notebook "$ROOT/.maestro/fixtures/u5a-cell-actions.ipynb" u5a-cell-actions
  run_flow "$FLOW" "$EVIDENCE/$name"
}

# The window's *widest* edge, which is its width when the device is held in landscape.
window_width_px() {
  "$ADB" shell wm size | tail -1 | sed 's/.*: //' | tr 'x' '\n' | sort -n | tail -1
}

# The density that makes the window COMPACT_TARGET_DP wide. 450dp is an ordinary phone width and
# sits well clear of the 600dp boundary without landing on it.
COMPACT_TARGET_DP=450
compact_density() {
  echo $(( $(window_width_px) * 160 / COMPACT_TARGET_DP ))
}

run_at compact "$(compact_density)"
run_at native native

# Three cells at each width: the action each surface offered actually reshaped the notebook.
# The screenshots show *which* surface appeared; only the file shows that it worked.
echo "--- notebook as saved after the flow ---"
"$ADB" shell run-as com.jupydroid.app cat files/notebooks/u5a-cell-actions.ipynb

echo
echo "screenshots: $EVIDENCE/{compact,native}/"
