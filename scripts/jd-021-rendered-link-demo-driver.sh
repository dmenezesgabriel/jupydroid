#!/usr/bin/env bash
# The PRD's demo, typed into a new notebook: one markdown paragraph holds a web link and a workspace
# link; a tap on the web link opens the browser and back returns to the cell still rendered, a tap
# beside the links opens the cell's editor, and a tap on the workspace link opens `notes.md`. A
# code cell's display(Markdown(...)) link and a Console's on the same kernel open the browser; after
# a kill and a relaunch, with No Kernel, the saved cell's and the saved output's links still go
# where they say. After each web tap the device's resumed activity is read and must be another
# app's: the browser is not on this app's screen, so no flow assertion can see it.
#
#   scripts/jd-021-rendered-link-demo-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/jd-021-rendered-link/demo"
FLOWS="$ROOT/.maestro/jd-021-rendered-link-demo"
mapfile -t DEVICE < <(device_adb)

# The screen's hierarchy into <dir>/hierarchy.xml, for the bounds of the paragraph the links are in.
# `uiautomator dump` writes to the device, so it is read back rather than piped.
dump_hierarchy() {
  local dir="$1"
  "${DEVICE[@]}" shell uiautomator dump /sdcard/jd-021-hierarchy.xml >/dev/null
  "${DEVICE[@]}" shell cat /sdcard/jd-021-hierarchy.xml >"$dir/hierarchy.xml"
  "${DEVICE[@]}" shell rm -f /sdcard/jd-021-hierarchy.xml
}

# Nothing but the file the workspace link names: the demo makes its own notebook beside it.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-021-notes.md" "notes.md"

run_flow "$FLOWS-part1-cell-link.yaml" "$OUT/part1-cell-link" || exit 1
require_another_app_resumed "$OUT/part1-cell-link"
back_to_app "$OUT/part1-cell-link"
dump_hierarchy "$OUT/part1-cell-link"
run_flow "$FLOWS-part2-edit-and-notes.yaml" "$OUT/part2-edit-and-notes" || exit 1
run_flow "$FLOWS-part3-output-link.yaml" "$OUT/part3-output-link" || exit 1
require_another_app_resumed "$OUT/part3-output-link"
back_to_app "$OUT/part3-output-link"
dump_hierarchy "$OUT/part3-output-link"
run_flow "$FLOWS-part4-console-link.yaml" "$OUT/part4-console-link" || exit 1
require_another_app_resumed "$OUT/part4-console-link"
back_to_app "$OUT/part4-console-link"
# The save is asynchronous; a force-stop before it lands would reopen a notebook without the
# output. `text/markdown` is in the file only once the save after the run has written it.
wait_for_workspace_text "Untitled.ipynb" 'text/markdown' 60
"${DEVICE[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-part5-relaunch.yaml" "$OUT/part5-relaunch" || exit 1
require_another_app_resumed "$OUT/part5-relaunch"

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
assert source == "Read [Jupyter](https://jupyter.org) and [notes](notes.md)", source
assert bundle["text/markdown"] == "see https://jupyter.org", bundle
assert printed["output_type"] == "stream" and "".join(printed["text"]) == "42\n", printed
print("the markdown as typed, the output's markdown and the print are in the file")
PY
