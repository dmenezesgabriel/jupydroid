#!/usr/bin/env bash
# The Chat panel draws an agent's tool call as a row with its status: opencode reads a file with its
# read tool, and the panel shows the question, a check beside the file's path, and the answer.
#
#   nice -n 10 timeout 1800 scripts/chat-tool-calls-row-driver.sh
#
# Exits non-zero if the flow fails, or if `untitled.chat` afterwards holds no persona message whose
# metadata has a completed tool call - then the row on screen came from somewhere else.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh) and signed in from
# base's terminal (`opencode auth login`) - a person's own credential, which no script types.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-tool-calls-row"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-046-read-tool-probe.txt" probe.txt
rotate_to 1

run_flow "$ROOT/.maestro/chat-tool-calls-row.yaml" "$EVIDENCE"

wait_for_workspace_text untitled.chat "kumquat" 60
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
python3 "$ROOT/scripts/chat_tool_call_reads.py" completed-call "$EVIDENCE/untitled.chat"
