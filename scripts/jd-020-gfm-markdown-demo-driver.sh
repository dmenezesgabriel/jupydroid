#!/usr/bin/env bash
# The PRD's demo, typed into a new notebook: a markdown cell draws a pipe table as a grid, struck
# text and a bare URL as a link; a code cell's display(Markdown(...)) draws a second table above the
# kernel's 42; a Console on the same kernel draws that table the same; and after a kill and a
# relaunch, with No Kernel, both are read back from the saved file. Each part's hierarchy is kept
# for the cells' bounds, which the alignment is measured inside.
#
#   scripts/jd-020-gfm-markdown-demo-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/jd-020-gfm-markdown/demo"
FLOWS="$ROOT/.maestro/jd-020-gfm-markdown-demo"
mapfile -t DEVICE < <(device_adb)

# Every text node on screen, with its bounds, into <dir>/text-nodes.txt. `uiautomator dump` writes
# to the device, so it is read back rather than piped.
dump_text_nodes() {
  local dir="$1"
  "${DEVICE[@]}" shell uiautomator dump /sdcard/jd-020-hierarchy.xml >/dev/null
  "${DEVICE[@]}" shell cat /sdcard/jd-020-hierarchy.xml >"$dir/hierarchy.xml"
  "${DEVICE[@]}" shell rm -f /sdcard/jd-020-hierarchy.xml
  grep -oE 'text="[^"]+"[^>]*bounds="[^"]*"' "$dir/hierarchy.xml" |
    sed -E 's/(text="[^"]+").*(bounds="[^"]*")/\1 \2/' | tee "$dir/text-nodes.txt"
}

# No seed: the demo makes its own notebook from the explorer's New menu.
reset_notebooks

run_flow "$FLOWS-part1-notebook.yaml" "$OUT/part1-notebook" || exit 1
dump_text_nodes "$OUT/part1-notebook"
run_flow "$FLOWS-part2-console.yaml" "$OUT/part2-console" || exit 1
dump_text_nodes "$OUT/part2-console"
# The save is asynchronous; a force-stop before it lands would reopen a notebook without the
# output. `text/markdown` is in the file only once the save after the run has written it.
wait_for_workspace_text "Untitled.ipynb" 'text/markdown' 60
"${DEVICE[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-part3-relaunch.yaml" "$OUT/part3-relaunch" || exit 1
dump_text_nodes "$OUT/part3-relaunch"

"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$OUT/saved.ipynb"
python3 - "$OUT/saved.ipynb" <<'PY' | tee "$OUT/saved-notebook-check.txt"
import json, sys
typed, code = json.load(open(sys.argv[1]))["cells"]
shown, printed = code["outputs"]
source = "".join(typed["source"])
bundle = {mime: "".join(payload) for mime, payload in shown["data"].items()}
print("cell 1", typed["cell_type"], repr(source))
print("output 1", shown["output_type"], bundle)
print("output 2", printed["output_type"], repr("".join(printed["text"])))
assert typed["cell_type"] == "markdown", typed
assert source == (
    "| Name | Mid | Count |\n|:-----|:---:|------:|\n| left | centre | 7 |\n\n"
    "~~gone~~ and https://jupyter.org"
), source
assert bundle["text/markdown"] == "| a | b |\n|:--|--:|\n| x | 1 |", bundle
assert printed["output_type"] == "stream" and "".join(printed["text"]) == "42\n", printed
print("the markdown as typed, the table's source and the print are in the file")
PY
