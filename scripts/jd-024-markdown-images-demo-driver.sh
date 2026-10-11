#!/usr/bin/env bash
# The PRD's demo, seeded: one notebook whose markdown cells hold an attachment, a file beside the
# notebook, a web image, an HTML <img> and an image naming no file. The four draw and the fifth
# draws its alt; a markdown cell edited and run renders again with its image; a code cell's
# HTML('<img …>') draws the file in its output beside the kernel's 42; and after a kill and a
# relaunch, with No Kernel, all of it draws again. Each part's hierarchy is kept for the bounds
# each image draws at.
#
#   scripts/jd-024-markdown-images-demo-driver.sh
#
# Seeded rather than typed: an attachment is bytes in the cell's JSON, which no keyboard writes.
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/image-nodes.sh
. "$ROOT/scripts/image-nodes.sh"

OUT="$ROOT/evidence/jd-024-markdown-images/demo"
FLOWS="$ROOT/.maestro/jd-024-markdown-images-demo"
NOTEBOOK="Markdown Images"
SIZED_WIDTH_DP=120
mapfile -t DEVICE < <(device_adb)

# One part of the demo into evidence [dir]: its flow, then the image nodes it left on screen. Each
# alt after [dir] must get a node, waited for because a file or a fetch is read after its cell
# draws; the image naming no file must never have one.
demo_part() {
  local part="$1" out="$OUT/$2" name
  run_flow "$FLOWS-$part.yaml" "$out" || exit 1
  rm -f "$out/drawn-after.txt"
  for name in "${@:3}"; do await_drawn "$out" "$name"; done
  require_not_drawn "$out" "no such picture"
}

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-024-markdown-images-demo.ipynb" "$NOTEBOOK"
seed_file "$ROOT/.maestro/fixtures/jd-024-plot.png" "img/plot.png"
# An empty HTTP cache, so the web image this run draws is one this run fetched.
"${DEVICE[@]}" shell run-as "$APP" rm -rf cache/remote-images

run_flow "$FLOWS-part1-open.yaml" "$OUT/part1-open" || exit 1
demo_part part2-top part2-top "orange chart" "blue plot"
demo_part part3-web part3-web "jupiter logo"
demo_part part4-html-and-missing part4-html-and-missing "sized plot"
require_width_dp "$OUT/part4-html-and-missing" "sized plot" "$SIZED_WIDTH_DP"
demo_part part5-edit part5-edit "blue plot"
demo_part part6-run part6-run "output plot"

# The save is asynchronous; a force-stop before it lands would reopen a notebook without the
# output. `text/html` is in the file only once the save after the run has written it.
wait_for_workspace_text "$NOTEBOOK.ipynb" 'text/html' 60
"${DEVICE[@]}" shell am force-stop "$APP"
demo_part part7-relaunch part7-relaunch "orange chart" "blue plot"
demo_part part3-web part8-relaunch-web "jupiter logo"
demo_part part4-html-and-missing part9-relaunch-html-and-missing "sized plot"
demo_part part10-relaunch-outputs part10-relaunch-outputs "output plot"

"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$NOTEBOOK.ipynb\"" >"$OUT/saved.ipynb"
python3 - "$OUT/saved.ipynb" <<'PY' | tee "$OUT/saved-notebook-check.txt"
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
attached, edited, shown, computed = cells[0], cells[1], cells[5], cells[6]
source = "".join(edited["source"])
html = "".join(shown["outputs"][0]["data"]["text/html"])
result = "".join(computed["outputs"][0]["data"]["text/plain"])
print("cell 1 attachments", sorted(attached["attachments"]))
print("cell 2", edited["cell_type"], repr(source))
print("cell 6 output", repr(html))
print("cell 7 output", repr(result))
assert sorted(attached["attachments"]) == ["chart.png"], attached
assert source == "Beside the notebook: ![blue plot](img/plot.png) (edited)", source
assert html == '<img src="img/plot.png" alt="output plot">', html
assert result == "42", result
print("the attachment, the edit, the HTML output and 42 are in the file")
PY
