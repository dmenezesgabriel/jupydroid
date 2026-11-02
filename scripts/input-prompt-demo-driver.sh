#!/usr/bin/env bash
# The demo, typed into a new notebook: input() and getpass() prompt under the cell and carry
# on with the answer, Stop closes an unanswered prompt, a Console entry prompts the same way, and
# after a kill and a relaunch the echo is still drawn and still saved as `stdin`.
#
#   scripts/input-prompt-demo-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/input-prompt}/demo"
FLOWS="$ROOT/.maestro/input-prompt-demo"
SAVED="$OUT/saved-notebook.json"

# No seed: the demo makes its own notebook from the explorer's New menu.
reset_notebooks

run_flow "$FLOWS-answers.yaml" "$OUT/part1-answers" || exit 1
"$ADB" shell am force-stop "$APP"
run_flow "$FLOWS-relaunch.yaml" "$OUT/part2-relaunch" || exit 1

# The file rather than the screen: each answered prompt's echo is a `stdin` stream before what the
# cell printed, and the password's echo carries the mask, never the value.
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$SAVED"
python3 - "$SAVED" <<'PY'
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
streams = [[(o["name"], "".join(o["text"])) for o in c["outputs"] if o["output_type"] == "stream"]
           for c in cells]
print("streams as saved:", streams[:2])
assert streams[0] == [("stdin", "Name? Ada\n"), ("stdout", "hi Ada\n")], streams[0]
assert streams[1] == [("stdin", "Secret? ········\n"), ("stdout", "3\n")], streams[1]
PY
