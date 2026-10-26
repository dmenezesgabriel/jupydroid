#!/usr/bin/env bash
# Insert under a code block of an agent's answer puts its code in a new code cell below the
# notebook's active cell: opencode answers with a fenced print(6*7), a new notebook's empty cell is
# selected beside the Chat, and Insert is tapped.
#
#   nice -n 10 timeout 1800 scripts/chat-markdown-insert-driver.sh
#
# Exits non-zero if the flow fails, or if `Untitled.ipynb` afterwards does not hold the empty cell
# and, below it, a code cell whose source is exactly print(6*7) - the screen alone cannot say which
# cell the code went into, or that it was saved. Then it turns the tablet to portrait, one pane, and
# fails unless the Chat, reached from there, finds no active cell (chat-markdown-insert-one-pane.yaml).
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh) and signed in from
# base's terminal (`opencode console login`) - a person's own credential, which no script types.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-markdown-insert"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/chat-markdown-insert.yaml" "$EVIDENCE"

wait_for_workspace_text Untitled.ipynb "print(6*7)" 60
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" | tr -d '\r' >"$EVIDENCE/Untitled.ipynb"
python3 "$ROOT/scripts/notebook_cell_reads.py" "$EVIDENCE/Untitled.ipynb" code: 'code:print(6*7)' ||
  fail "Untitled.ipynb is not an empty code cell above one of exactly print(6*7)"

rotate_to 0
run_flow "$ROOT/.maestro/chat-markdown-insert-one-pane.yaml" "$EVIDENCE/one-pane"
