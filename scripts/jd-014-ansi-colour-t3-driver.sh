#!/usr/bin/env bash
# A notebook's coloured print, repr and traceback, run and saved, still draw in their colours after
# a kill and a relaunch, and the saved file still holds the escapes that colour them.
#
#   scripts/jd-014-ansi-colour-t3-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-014-ansi-colour}/t3-reopen"
NOTEBOOK="Ansi Colour.ipynb"
SAVED="$OUT/saved-notebook.json"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-014-ansi-colour.ipynb" "Ansi Colour"

# Part 1 is T1's flow as it stands: run both cells and see them coloured.
run_flow "$ROOT/.maestro/jd-014-ansi-colour-t1-notebook.yaml" "$OUT/part1-runs" || exit 1
# The save is asynchronous; a force-stop before it lands would reopen the unrun fixture.
wait_for_workspace_text "$NOTEBOOK" "ZeroDivisionError" 30
"$ADB" shell am force-stop "$APP"
run_flow "$ROOT/.maestro/jd-014-ansi-colour-t3-reopen.yaml" "$OUT/part2-relaunch" || exit 1

# The file rather than the screen: each coloured output is stored with its escapes, so another
# front end opening it draws the same colours.
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/$NOTEBOOK\"" >"$SAVED"
python3 - "$SAVED" <<'PY'
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
stream, result = cells[0]["outputs"]
error = cells[1]["outputs"][0]
stored = {
    "stream": "".join(stream["text"]),
    "text/plain": "".join(result["data"]["text/plain"]),
    "traceback": "".join(error["traceback"]),
}
for kind, text in stored.items():
    print(kind, repr(text[:80]))
assert stored["stream"].startswith("\x1b[31mred 42\x1b[0m"), stored["stream"]
assert stored["text/plain"] == "\x1b[34mblue 12\x1b[0m", stored["text/plain"]
assert "\x1b[" in stored["traceback"] and "ZeroDivisionError" in stored["traceback"], stored["traceback"]
PY
