#!/usr/bin/env bash
# The demo, typed into a new notebook: a 200k-line print and 300 display() calls draw cut,
# each with a row that draws the rest, in the notebook and the Console; after a kill and a relaunch
# both are cut again and the saved file holds every line and every output; and stepping onto a
# search hit in the hidden lines draws them.
#
#   scripts/output-caps-demo-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/output-caps}/demo"
FLOWS="$ROOT/.maestro/output-caps-demo"
mapfile -t DEVICE < <(device_adb)
# 200k lines are not evidence anyone reads; the check's verdict on them is.
SAVED="$(mktemp)"
trap 'rm -f "$SAVED"' EXIT

# No seed: the demo makes its own notebook from the explorer's New menu.
reset_notebooks

run_flow "$FLOWS-run.yaml" "$OUT/part1-run" || exit 1
# The save is asynchronous; a force-stop before it lands would reopen an unrun notebook. Only the
# display cell's last text/plain reads `"299"` with its closing quote - the print's is `"299\n"`.
wait_for_workspace_text "Untitled.ipynb" '"299"' 60
"${DEVICE[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-relaunch.yaml" "$OUT/part2-relaunch" || exit 1

# The file rather than the screen: the view cut nothing from the model, so the file holds what
# JupyterLab would have saved from the same run.
"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$SAVED"
python3 - "$SAVED" <<'PY' | tee "$OUT/saved-notebook-check.txt"
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
printed = cells[0]["outputs"]
displayed = cells[1]["outputs"]
stream = "".join(printed[0]["text"])
shown = ["".join(o["data"]["text/plain"]) for o in displayed]
print("print cell   ", len(printed), "output(s),", stream.count("\n"), "lines, last", repr(stream[-8:]))
print("display cell ", len(displayed), "outputs, first", repr(shown[0]), "last", repr(shown[-1]))
assert len(printed) == 1, len(printed)
assert stream == "".join(f"{i}\n" for i in range(200_000)), stream[-40:]
assert shown == [str(i) for i in range(300)], shown[-3:]
print("every line and every output is in the file")
PY

run_flow "$FLOWS-search.yaml" "$OUT/part3-search" || exit 1
