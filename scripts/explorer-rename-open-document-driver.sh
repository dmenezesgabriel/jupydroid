#!/usr/bin/env bash
# X2f: renaming the document that is open beside the browser, which only two panes can reach.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# What the screen cannot show. After the rename the editor looks right either way; what says
# whether it *followed* its file is the disk. A document holding a stale path writes itself back
# under the old name at the next save, so the gate is: exactly one notebook, at the new path,
# holding both edits - and nothing at the old one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Overridable, so a step that only needs this flow *green again* can capture under its own name
# rather than overwriting frames captured on another device. P7c needed exactly that: it moved
# Clear behind the section's overflow, so this flow's two taps changed and its committed
# tablet frames must not be replaced by an emulator's (`EVIDENCE_ROOT=…` -> P7a-3's precedent).
OUT="${EVIDENCE_ROOT:-$ROOT/evidence/x2f-explorer-rename}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -lt 840 ]; then
  echo "this flow must run on a wide (>= 840dp) window; $WIDTH_DP is not one" >&2
  exit 1
fi

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/switch-alpha.ipynb" "Switch Alpha"

run_flow "$ROOT/.maestro/explorer-rename-open-document.yaml" "$OUT/open-document"

# The last edit is debounced, and leaving is what flushes it. Force-stopping instead would take
# the flush with it and prove nothing about where the write went.
"$ADB" shell input keyevent KEYCODE_BACK
sleep 3

on_disk() { "$ADB" shell run-as "$APP" "test -e \"$NOTEBOOK_DIR/$1\""; }

if on_disk "Switch Alpha.ipynb"; then
  echo "the old path came back: the editor kept writing to Switch Alpha.ipynb after the rename" >&2
  "$ADB" shell run-as "$APP" ls "$NOTEBOOK_DIR" >&2
  exit 1
fi
echo "the old path stayed gone"

on_disk "Renamed From The Browser.ipynb" || {
  echo "nothing at the new path" >&2
  "$ADB" shell run-as "$APP" ls "$NOTEBOOK_DIR" >&2
  exit 1
}

# Quoted inside the command string, not around the argument: `adb shell` joins its arguments and the
# device's shell splits them again, so an unquoted name with spaces arrives as four paths. The same
# bug once landed `Markdown Render.ipynb` in a file called `Markdown` (ADR-0064).
moved="$("$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Renamed From The Browser.ipynb\"" | tr -d '\r')"
for expected in "edited_after_open = 1" "edited_after_rename = 2"; do
  grep -q "$expected" <<<"$moved" || {
    echo "the renamed notebook is missing '$expected'; an edit was written somewhere else" >&2
    printf '%s\n' "$moved" >&2
    exit 1
  }
done
echo "both edits are in the renamed notebook, and there is only one"
