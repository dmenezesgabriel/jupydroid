#!/usr/bin/env bash
# The PRD's demo, typed into a new notebook: a coloured print, a repr that colours itself and a
# traceback draw in their colours in the notebook and the Console, first in the system's dark theme
# and then in the app's Light, and after a kill and a relaunch they are still coloured and saved
# with their escapes.
#
#   scripts/jd-014-ansi-colour-demo-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-014-ansi-colour}/demo"
FLOWS="$ROOT/.maestro/jd-014-ansi-colour-demo"
SAVED="$OUT/saved-notebook.json"

# No seed: the demo makes its own notebook from the explorer's New menu. Clearing the stores also
# leaves the app's theme on System, so part 1 is dark because the system is.
reset_notebooks

# The system-wide setting isSystemInDarkTheme() reads, put back however this run ends. Light comes
# from the app's own Settings in part 2, not from here: flipping this recreates the activity, and a
# recreated Console tab composes nothing (Navigation.kt's `consoleSession` is plain `remember`).
NIGHT_BEFORE="$("$ADB" shell cmd uimode night | awk '{print $3}')"
trap '"$ADB" shell cmd uimode night "$NIGHT_BEFORE" >/dev/null' EXIT

"$ADB" shell cmd uimode night yes
run_flow "$FLOWS-part1-dark.yaml" "$OUT/part1-dark" || exit 1
run_flow "$FLOWS-part2-light.yaml" "$OUT/part2-light" || exit 1
# The save is asynchronous; a force-stop before it lands would reopen an unrun notebook.
wait_for_workspace_text "Untitled.ipynb" "ZeroDivisionError" 30
"$ADB" shell am force-stop "$APP"
run_flow "$FLOWS-part3-relaunch.yaml" "$OUT/part3-relaunch" || exit 1

# The file rather than the screen: each coloured output is stored with its escapes, so another
# front end opening it draws the same colours.
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$SAVED"
python3 - "$SAVED" <<'PY'
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
stream = "".join(cells[0]["outputs"][0]["text"])
plain = "".join(cells[1]["outputs"][0]["data"]["text/plain"])
traceback = "".join(cells[2]["outputs"][0]["traceback"])
for kind, text in {"stream": stream, "text/plain": plain, "traceback": traceback}.items():
    print(kind, repr(text[:80]))
assert stream == "\x1b[31mred\x1b[0m \x1b[1;32mbold green\x1b[0m \x1b[38;5;208morange\x1b[0m\n", stream
assert plain == "\x1b[34mblue\x1b[0m", plain
assert "\x1b[" in traceback and "ZeroDivisionError" in traceback, traceback
PY
