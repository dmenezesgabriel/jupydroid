#!/usr/bin/env bash
# Seeds a three-cell notebook and runs the add-cell-footer flow at font scale 1.0 and again at 2.0,
# then reads what the larger scale changed and what reached disk.
#
# Emulator or tablet: adding a cell runs no cell, so no Space runtime is needed. Run it once per
# device - the phone-sized `dev36` and the tablet-sized `tab36`, one directory per window width.
#
# Two things a frame cannot prove are read here. The saved notebook says the appended code cell is
# last, empty and survived a kill, and the undone markdown cell did not. `read_font_scale_fit.py`
# says which nodes newly overlap or fall under 48dp at 2.0; whether a label is cut is read off the
# screenshots, as that reader's docstring says.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/add-cell-footer.ipynb"
FLOW="$ROOT/.maestro/add-cell-footer.yaml"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
mapfile -t adb < <(device_adb)
# shellcheck source=scripts/font-scale.sh
. "$ROOT/scripts/font-scale.sh"
trap restore_font_scale EXIT

# The handwriting IME steals focus from text fields; this flow taps cells.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

# Kept per window width, not per ABI: the phone and the tablet emulators are both x86_64.
EVIDENCE="$ROOT/evidence/add-cell-footer/w$(window_width_dp)dp"

# One scale, from a clean workspace: the flow appends cells, so a second run on the first's file
# would start from five.
run_at_scale() { # run_at_scale <scale>
  local scale="$1" out="$EVIDENCE/scale-$1"
  reset_notebooks
  seed_notebook "$FIXTURE" "Add Footer"
  set_font_scale "$scale"
  run_flow "$FLOW" "$out"
  "${adb[@]}" shell uiautomator dump /sdcard/footer.xml >/dev/null
  "${adb[@]}" pull /sdcard/footer.xml "$out/hierarchy.xml" >/dev/null
  "${adb[@]}" shell rm -f /sdcard/footer.xml
  saved_notebook | tee "$out/saved-notebook.json" | read_cells
}

saved_notebook() {
  "$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Add Footer.ipynb\""
}

# The three seeded cells, then one empty code cell: the markdown cell was undone and the code cell
# was not. Exits non-zero, naming the kinds found, when the file says anything else.
read_cells() {
  python3 -c '
import json, sys
cells = json.load(sys.stdin)["cells"]
kinds = [c["cell_type"] for c in cells]
last = "".join(cells[-1]["source"])
if kinds != ["code", "markdown", "code", "code"] or last != "":
    sys.exit(f"saved cells {kinds}, last source {last!r}; expected code, markdown, code, code and an empty last")
print("saved:", kinds, "last source empty")
'
}

run_at_scale 1.0
run_at_scale 2.0

density="$("${adb[@]}" shell wm density | tr -d '\r' |
  PYTHONPATH="$ROOT/scripts" python3 -c 'import sys; from read_touch_target_bounds import effective_density; print(effective_density(sys.stdin.read()))')"
python3 "$ROOT/scripts/read_font_scale_fit.py" \
  "$EVIDENCE/scale-1.0/hierarchy.xml" "$EVIDENCE/scale-2.0/hierarchy.xml" "$density" |
  tee "$EVIDENCE/font-scale-fit.txt"

echo
echo "screenshots: $EVIDENCE/"
