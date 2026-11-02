#!/usr/bin/env bash
# A `stdin` stream round-trips: a notebook JupyterLab saved after an `input()` opens with the
# echo drawn, and the next save writes it back as `stdin`, not dropped and not `stderr`.
#
#   scripts/input-prompt-stdin-echo-round-trips-driver.sh
#
# Runs no cell, so any device will do: the echo is seeded into the fixture.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/input-prompt}/t1-stdin-echo-round-trips"
SAVED="$OUT/saved-notebook.json"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/stdin-echo.ipynb" "Stdin Echo"

run_flow "$ROOT/.maestro/input-prompt-stdin-echo-round-trips.yaml" "$OUT"

# The autosave's write rather than the screen: two cells now, so two echoes, each still `stdin`.
sleep 2
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Stdin Echo.ipynb\"" >"$SAVED"
python3 - "$SAVED" <<'PY'
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
names = [o.get("name") for c in cells for o in c.get("outputs", []) if o["output_type"] == "stream"]
print("stream names as saved:", names)
assert len(cells) == 2, f"expected 2 cells after the paste, saw {len(cells)}"
assert names.count("stdin") == 2, f"expected 2 stdin streams, saw {names}"
PY
