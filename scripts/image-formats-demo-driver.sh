#!/usr/bin/env bash
# The demo, typed into a new notebook: JPEG, GIF, WebP and BMP draw; a PNG with width=100 draws
# 100 dp and one with no width 96 dp, at the column's start; a Console on the same kernel draws the
# second one the same width; and after a kill and a relaunch the widths are the same, read back from
# the saved file. Each part's hierarchy is kept for the bounds its images draw at (ADR-0086).
#
#   scripts/image-formats-demo-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/image-formats/demo"
FLOWS="$ROOT/.maestro/image-formats-demo"
mapfile -t DEVICE < <(device_adb)

# Every `Cell image output` on screen, top to bottom, into <dir>/image-bounds.txt. `uiautomator dump`
# writes to the device, so it is read back rather than piped.
dump_image_bounds() {
  local dir="$1"
  "${DEVICE[@]}" shell uiautomator dump /sdcard/image-formats-hierarchy.xml >/dev/null
  "${DEVICE[@]}" shell cat /sdcard/image-formats-hierarchy.xml >"$dir/hierarchy.xml"
  "${DEVICE[@]}" shell rm -f /sdcard/image-formats-hierarchy.xml
  grep -oE 'content-desc="Cell image output"[^>]*bounds="[^"]*"' "$dir/hierarchy.xml" |
    grep -oE 'bounds="[^"]*"' | tee "$dir/image-bounds.txt"
}

# No seed: the demo makes its own notebook from the explorer's New menu.
reset_notebooks

run_flow "$FLOWS-cells.yaml" "$OUT/part1-cells" || exit 1
dump_image_bounds "$OUT/part1-cells"
run_flow "$FLOWS-console.yaml" "$OUT/part2-console" || exit 1
dump_image_bounds "$OUT/part2-console"
# The save is asynchronous; a force-stop before it lands would reopen a notebook without cell 3's
# output. `"execution_count":3` is written only by the save after cell 3, the last cell run; the file
# is compact JSON, with no space after the colon.
wait_for_workspace_text "Untitled.ipynb" '"execution_count":3' 60
"${DEVICE[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-relaunch.yaml" "$OUT/part3-relaunch" || exit 1
dump_image_bounds "$OUT/part3-relaunch"

"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$OUT/saved.ipynb"
python3 - "$OUT/saved.ipynb" <<'PY' | tee "$OUT/saved-notebook-check.txt"
import json, sys
formats, sized, own = (c["outputs"] for c in json.load(open(sys.argv[1]))["cells"])
print("cell 1", [sorted(o.get("data", {})) or o["output_type"] for o in formats])
print("cell 2", [(sorted(o["data"]), o["metadata"]) for o in sized])
print("cell 3", [(sorted(o["data"]), o["metadata"]) for o in own])
assert [sorted(o["data"]) for o in formats[:4]] == [["image/jpeg"], ["image/gif"], ["image/webp"], ["image/bmp"]], formats
assert formats[4]["output_type"] == "stream" and "".join(formats[4]["text"]) == "42\n", formats[4]
assert [o["metadata"] for o in sized] == [{"image/png": {"width": 100}}], sized
assert len(own) == 1 and "image/png" in own[0]["data"] and "width" not in json.dumps(own[0]["metadata"]), own
print("four formats, the width cell 2 asked for, and none for cell 3 are in the file")
PY
