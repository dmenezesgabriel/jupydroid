#!/usr/bin/env bash
# The Chat panel offers a tool call's permission options and answers the one tapped: opencode asks
# before its bash tool runs, Allow once runs it and the answer holds its output, Reject leaves a
# cross and no output. Minutes later the agent still answers.
#
#   nice -n 10 timeout 1800 scripts/chat-tool-calls-approval-driver.sh
#
# Part 1 allows one call and rejects another; this driver waits; part 2 asks again. Exits non-zero if
# a flow fails, or unless `untitled.chat` afterwards holds a call resolved with an allowing option
# that printed permission-42 and one resolved with a rejecting option that printed nothing - then
# the rows on screen came from somewhere else.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/opencode-in-a-space-driver.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/chat-tool-calls-approval"
# Past the two minutes after which an unanswered request once saw the agent's connection close.
AFTER_SECONDS=150

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

status=0
run_part() { run_flow "$ROOT/.maestro/chat-tool-calls-approval-part$1.yaml" "$EVIDENCE/part$1"; }
run_part 1 || status=$?
[ "$status" -eq 0 ] && sleep "$AFTER_SECONDS"
[ "$status" -eq 0 ] && { run_part 2 || status=$?; }
pull_server_log "$EVIDENCE/jupyter_server.log"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
[ "$status" -eq 0 ] || fail "a flow failed ($status)"

python3 "$ROOT/scripts/chat_tool_call_reads.py" allowed-and-rejected "$EVIDENCE/untitled.chat" \
  permission-42 second-42
echo "jupyter_server.log: $(grep -c 'request_permission: CALLED' "$EVIDENCE/jupyter_server.log" || true) permission requests," \
  "$(grep -c 'Connection closed' "$EVIDENCE/jupyter_server.log" || true) 'Connection closed'"
