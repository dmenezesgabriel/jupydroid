#!/usr/bin/env bash
# An HTML <img> draws: in a markdown cell at its width attribute, inside a line of prose, inside an HTML
# paragraph and in a code cell's text/html output, while one naming no file draws its alt
# text. The hierarchy is kept for the bounds each image draws at, and the sized one's width is
# measured against the 80 px its attribute asks for, in dp.
#
#   scripts/jd-024-markdown-images-t4-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-024-markdown-images/t4-html-images"
NOTEBOOK="HTML Images"
SIZED_WIDTH_DP=80
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-024-html-images.ipynb" "$NOTEBOOK"
seed_file "$ROOT/.maestro/fixtures/jd-024-plot.png" "img/plot.png"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"

# The image nodes in a fresh dump into [out], as `content-desc` / `bounds` pairs. `uiautomator dump`
# writes to the device, so it is read back rather than piped.
image_bounds() {
  local out="$1"
  "${DEVICE[@]}" shell uiautomator dump /sdcard/jd-024-hierarchy.xml >/dev/null
  "${DEVICE[@]}" shell cat /sdcard/jd-024-hierarchy.xml >"$out/hierarchy.xml"
  "${DEVICE[@]}" shell rm -f /sdcard/jd-024-hierarchy.xml
  { grep -oE 'content-desc="[a-z ]+ (plot|img)"[^>]*bounds="[^"]*"' "$out/hierarchy.xml" || true; } |
    grep -oE '(content-desc|bounds)="[^"]*"' | paste - - | tee "$out/image-bounds.txt"
}

# A flow's `text:` matches a node's text or its description, and an unfilled slot's text is the
# alt, so the flow alone cannot tell a drawn image from its fallback. The image nodes can.
require_drawn() {
  local out="$1" name
  for name in "${@:2}"; do
    grep -q "content-desc=\"$name\"" "$out/image-bounds.txt" || { echo "no image drawn for $name"; exit 1; }
  done
}

# The sized image's drawn width in dp, against the density the app draws at: an override when one
# is set, else the physical one. A layout rounds to whole pixels, which at any density is under a dp.
require_sized_width() {
  local out="$1" density left right width_dp
  density="$("${DEVICE[@]}" shell wm density | tr -d '\r' | sed -n 's/.*density: //p' | sed -n '$p')"
  read -r left right < <(grep 'content-desc="sized plot"' "$out/image-bounds.txt" |
    sed -E 's/.*bounds="\[([0-9]+),[0-9]+\]\[([0-9]+),[0-9]+\]".*/\1 \2/')
  width_dp=$(((right - left) * 160 / density))
  echo "sized plot: $((right - left)) px at density $density = $width_dp dp, asked $SIZED_WIDTH_DP" |
    tee "$out/sized-width.txt"
  ((width_dp >= SIZED_WIDTH_DP - 1 && width_dp <= SIZED_WIDTH_DP + 1)) || { echo "sized plot is not $SIZED_WIDTH_DP dp"; exit 1; }
}

run_flow "$ROOT/.maestro/jd-024-markdown-images-t4-part1-run.yaml" "$EVIDENCE/run"
image_bounds "$EVIDENCE/run"
require_drawn "$EVIDENCE/run" "sized plot" "inline plot" "output plot"
require_sized_width "$EVIDENCE/run"

run_flow "$ROOT/.maestro/jd-024-markdown-images-t4-part2-paragraph.yaml" "$EVIDENCE/paragraph"
image_bounds "$EVIDENCE/paragraph"
require_drawn "$EVIDENCE/paragraph" "paragraph plot"
if grep -q 'content-desc="no such img"' "$EVIDENCE/paragraph/image-bounds.txt"; then
  echo "an image drew for a file that is not there"
  exit 1
fi
