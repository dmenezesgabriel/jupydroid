#!/usr/bin/env bash
# The demo of the Chat's queued questions, in one chat: questions sent while opencode's bash call
# waits on approval wait as pending rows, one is taken off with its × and one edited in place, and
# once Allow once ends the reply each is asked in turn, the edited one as edited. A question queued
# behind a second call is kept across a kill and asked once the chat is opened again.
#
#   nice -n 10 timeout 3600 scripts/chat-queued-questions-demo-driver.sh
#
# Run from a fresh install, the app's state wiped first: `fresh_install` (scripts/maestro-run.sh),
# then scripts/opencode-in-a-space-driver.sh, then a person signs opencode in from base's
# terminal - their own credential, which no script types.
#
# Exits non-zero if a flow fails; if the server logged a cancel, or anything but three turns each
# completed before the next started and a fourth left running at the kill; unless the app's own
# storage held the kept question at the kill and no longer after the relaunch; unless `untitled.chat`
# did not hold it at the kill; and unless `untitled.chat` holds each question answered before the
# next was asked, the kept one once, and never the removed question nor the edited one as it was.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
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

EVIDENCE="$ROOT/evidence/chat-queued-questions-demo"
FLOWS="$ROOT/.maestro/chat-queued-questions-demo"
KEPT_QUESTION="What is 6+6? Answer with the number only."
# Where JupydroidApplication keeps base's queued questions.
KEPT="files/chat-queues/base.json"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_kept() { "${DEVICE_ADB[@]}" shell "run-as $APP cat $KEPT" | tr -d '\r' >"$1"; }
read_chat() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$1"; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $KEPT $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

run_flow "$FLOWS-part1-queue-remove-edit-kill.yaml" "$EVIDENCE/queue-remove-edit-kill" || fail "part 1 failed"
read_kept "$EVIDENCE/kept-before-the-kill.json"
grep -F "$KEPT_QUESTION" "$EVIDENCE/kept-before-the-kill.json" || fail "the queued question was not kept"
# Pulled before the relaunch, which starts a new server and with it a new log.
pull_server_log "$EVIDENCE/jupyter_server.log"
# The process and the server it runs, with the second call still asking.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
# Not asked before the kill, so only what was kept can ask it after the relaunch.
read_chat "$EVIDENCE/untitled-at-the-kill.chat"
if grep -F "$KEPT_QUESTION" "$EVIDENCE/untitled-at-the-kill.chat"; then fail "the question was asked before the kill"; fi

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
read_kept "$EVIDENCE/kept-after-it-was-asked.json"
if grep -F "$KEPT_QUESTION" "$EVIDENCE/kept-after-it-was-asked.json"; then fail "the question asked is still kept"; fi
read_chat "$EVIDENCE/untitled.chat"

python3 "$ROOT/scripts/chat_tool_call_reads.py" answered-in-turn "$EVIDENCE/untitled.chat" \
  "Run echo demo-\$((6*7)) with your bash tool, then answer with only what it printed." demo-42 \
  "What is 2+2? Answer with the number only." 4 "What is 4+4? Answer with the number only." 8 \
  "$KEPT_QUESTION" 12
asked=$(grep -oF "\"body\": \"$KEPT_QUESTION\"" "$EVIDENCE/untitled.chat" | wc -l)
[ "$asked" -eq 1 ] || fail "the kept question was asked $asked times, expected once"
# Removed while it waited, so it was never asked.
if grep -F "What is 5+5?" "$EVIDENCE/untitled.chat"; then fail "the removed question was asked"; fi
# Edited while it waited, so only what it became was asked.
if grep -F "What is 3+3?" "$EVIDENCE/untitled.chat"; then fail "the edited question was asked as it was"; fi
# Nothing here taps Stop, so a cancel in the log is a question that cut a reply short.
if grep "POST /api/ai/personas/cancel" "$EVIDENCE/jupyter_server.log"; then fail "the server took a cancel"; fi
# One turn per question, each begun only once the one before had completed; the second call's turn
# was still running at the kill.
grep "prompt_and_reply: " "$EVIDENCE/jupyter_server.log" | python3 -c '
import sys
steps = [line.split("prompt_and_reply: ")[1].split()[0] for line in sys.stdin]
print("turns:", steps)
sys.exit(steps != ["starting", "completed"] * 3 + ["starting"])
' || fail "the server did not run the questions one turn after another"
