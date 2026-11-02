#!/usr/bin/env bash
# Copy under a code block of an agent's answer puts its code on the system clipboard: opencode
# answers with a fenced print(6*7), Copy is tapped, and the platform's Paste in a new notebook's
# empty cell writes it into the cell.
#
#   nice -n 10 timeout 1800 scripts/chat-markdown-copy-driver.sh
#
# Exits non-zero if the flow fails, or if `Untitled.ipynb` afterwards does not hold one code cell
# whose source is exactly print(6*7) - the screen alone cannot say the text reached the cell rather
# than some other field.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/opencode-in-a-space-driver.sh) and signed in from
# base's terminal (`opencode console login`) - a person's own credential, which no script types.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-markdown-copy"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/chat-markdown-copy.yaml" "$EVIDENCE"

wait_for_workspace_text Untitled.ipynb "print(6*7)" 60
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" | tr -d '\r' >"$EVIDENCE/Untitled.ipynb"
python3 "$ROOT/scripts/notebook_cell_reads.py" "$EVIDENCE/Untitled.ipynb" 'code:print(6*7)' ||
  fail "Untitled.ipynb holds no code cell of exactly print(6*7)"
