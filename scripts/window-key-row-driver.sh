#!/usr/bin/env bash
# A cell's symbol row spans the window, beside two panes and at one (`WindowKeyRowSlot`). Runs
# window-key-row.yaml in landscape (two panes) and in portrait (one), and after each run reads the
# row's bounds and the window's width out of the window's hierarchy into widths.txt, and the
# notebook as saved into saved.ipynb.
#
#   ANDROID_SERIAL=<tablet> MAESTRO_DEVICE=<tablet> nice -n 10 timeout 1800 scripts/window-key-row-driver.sh [out-dir]
#
# TABLET ONLY (SM-X216B): portrait and landscape give one pane and two on one device. Installs the
# APK that `./gradlew :app:assembleDebug` last built, through adb rather than Gradle, and restores
# auto-rotate on exit. [out-dir] defaults to evidence/window-key-row, and is how a build from
# before the change is measured somewhere else.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"   # rotate_to, restore_rotation

OUT="${1:-$ROOT/evidence/window-key-row}"

# The row's left and right edge, and the window's width, in dp (SM-X216B is 240 dpi).
row_widths() { # row_widths <out-dir>
  "${adb[@]}" shell uiautomator dump /sdcard/window-key-row.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/window-key-row.xml >"$1/window.xml"
  "${adb[@]}" shell rm -f /sdcard/window-key-row.xml
  python3 -I -c '
import re, sys
xml = open(sys.argv[1]).read()
row = re.search(r"resource-id=\"symbol_row\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", xml)
if row is None:
    sys.exit("no node with resource-id symbol_row in " + sys.argv[1])
left, top, right, bottom = (int(edge) * 160 / 240 for edge in row.groups())
print(f"symbol_row x {left:.0f}-{right:.0f}dp ({right - left:.0f}dp wide) y {top:.0f}-{bottom:.0f}dp")' "$1/window.xml"
  echo "window w$(window_width_dp)dp"
}

run_at() { # run_at <0=portrait|1=landscape> <out-dir>
  rotate_to "$1"
  reset_notebooks
  seed_notebook "$ROOT/.maestro/fixtures/u5d-symbol-row.ipynb" "Symbol Row"
  run_flow "$ROOT/.maestro/window-key-row.yaml" "$2" || exit 1
  row_widths "$2" | tee "$2/widths.txt"
  sleep 2   # the cell reaches the model on a debounce, and the save follows it
  "${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Symbol Row.ipynb\"" | tee "$2/saved.ipynb"
  echo
}

# The handwriting IME steals focus from text fields; this flow taps into one.
"${adb[@]}" shell settings put secure stylus_handwriting_enabled 0 || true
"${adb[@]}" shell settings put secure stylus_handwriting 0 || true

fresh_install "$OUT"
trap restore_rotation EXIT
run_at 1 "$OUT/two-pane"
run_at 0 "$OUT/one-pane"
