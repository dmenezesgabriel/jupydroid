#!/usr/bin/env bash
# The Chat panel holds a question sent while opencode answers: two questions sent during a count to
# 1000 wait as pending rows, the count runs to its end, and each is asked once the reply before it
# has ended.
#
#   nice -n 10 timeout 2400 scripts/chat-queued-questions-driver.sh
#
# Exits non-zero if the flow fails, if the server logged a cancel, or unless `untitled.chat` holds the
# count up to 1000 followed by each queued question with its answer before the next was asked.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/chat-queued-questions"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

status=0
run_flow "$ROOT/.maestro/chat-queued-questions.yaml" "$EVIDENCE" || status=$?
[ "$status" -eq 0 ] || fail "the flow failed ($status)"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
pull_server_log "$EVIDENCE/jupyter_server.log"

python3 "$ROOT/scripts/chat_tool_call_reads.py" answered-in-turn "$EVIDENCE/untitled.chat" \
  "What is 2+2? Answer with the number only." 4 "What is 3+3? Answer with the number only." 6
# Nothing here taps Stop, so a cancel in the log is a question that cut a reply short.
if grep "POST /api/ai/personas/cancel" "$EVIDENCE/jupyter_server.log"; then fail "the server took a cancel"; fi
