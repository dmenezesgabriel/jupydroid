#!/usr/bin/env bash
# The demo of the Chat's tool calls: opencode's read call is a ✓ row between the question and the
# answer; its bash call offers the agent's own options, and Allow once runs it; Reject leaves a ✗ and
# nothing ran; Stop beside Send cuts a count to 1000 short; and after a kill the chat reads back
# every row with its last status.
#
#   nice -n 10 timeout 2400 scripts/chat-tool-calls-demo-driver.sh
#
# Run from a fresh install, the app's state wiped first: `fresh_install` (scripts/maestro-run.sh),
# then scripts/jd-046-opencode-in-a-space-driver.sh, then a person signs opencode in from base's
# terminal - their own credential, which no script types.
#
# Exits non-zero if a flow fails, if the server logged no answered cancel, or unless `untitled.chat`
# holds a completed call, an allowed call that printed permission-42, a rejected one that printed
# nothing, and a count that read the same twice SETTLE_SECONDS apart and stopped short of 1000
# (scripts/chat_tool_call_reads.py) - then the screen showed something the agent did not do.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
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

EVIDENCE="$ROOT/evidence/chat-tool-calls-demo"
FLOWS="$ROOT/.maestro/chat-tool-calls-demo"
READS="$ROOT/scripts/chat_tool_call_reads.py"
# Long enough for a reply that was not stopped to have written more lines.
SETTLE_SECONDS=15

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_chat() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$1"; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-046-read-tool-probe.txt" probe.txt
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

run_flow "$FLOWS-part1-ask-allow-reject-stop.yaml" "$EVIDENCE/ask-allow-reject-stop" || exit 1
sleep "$SETTLE_SECONDS"
read_chat "$EVIDENCE/untitled.chat"
sleep "$SETTLE_SECONDS"
read_chat "$EVIDENCE/untitled-later.chat"
# Pulled before the relaunch, which starts a new server and with it a new log.
pull_server_log "$EVIDENCE/jupyter_server.log"

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || exit 1

python3 "$READS" completed-call "$EVIDENCE/untitled.chat"
python3 "$READS" allowed-and-rejected "$EVIDENCE/untitled.chat" permission-42 second-42
python3 "$READS" stopped-short "$EVIDENCE/untitled.chat" "$EVIDENCE/untitled-later.chat"
# A tap that misses Stop leaves the reply to end by itself and no cancel in the log.
grep "200 POST /api/ai/personas/cancel" "$EVIDENCE/jupyter_server.log" || fail "the server took no cancel"
