#!/usr/bin/env bash
# The demo, typed into a new notebook: a `\r` progress loop draws one line and the prints
# after it join it, the Console does the same, and after a kill and a relaunch the one block is
# still drawn and saved as one `stream` holding the processed text.
#
#   scripts/progress-bar-demo-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/progress-bar}/demo"
FLOWS="$ROOT/.maestro/progress-bar-demo"
SAVED="$OUT/saved-notebook.json"

# No seed: the demo makes its own notebook from the explorer's New menu.
reset_notebooks

run_flow "$FLOWS-runs.yaml" "$OUT/part1-runs" || exit 1
"$ADB" shell am force-stop "$APP"
run_flow "$FLOWS-relaunch.yaml" "$OUT/part2-relaunch" || exit 1

# The file rather than the screen: the six stream frames are saved as one output, and its text is
# what the `\r`s left, not the frames themselves.
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$SAVED"
python3 - "$SAVED" <<'PY'
import json, sys
outputs = json.load(open(sys.argv[1]))["cells"][0]["outputs"]
saved = [(o["output_type"], o.get("name"), "".join(o.get("text", ""))) for o in outputs]
print("outputs as saved:", saved)
assert saved == [("stream", "stdout", "100% done\na\nb\n")], saved
PY
