#!/usr/bin/env bash
# The Chat panel keeps a queued question across a kill: one sent while opencode's bash call waits on
# approval is a pending row, the app is killed with the reply unfinished and the question not yet
# asked, and once it is relaunched and the chat opened, the question is asked, since the new server
# has no reply in flight.
#
#   nice -n 10 timeout 2400 scripts/chat-queued-questions-kept-driver.sh
#
# Exits non-zero if either flow fails, unless the app's own storage held the question after part 1
# and holds it no longer after part 2, unless `untitled.chat` did not hold it at the kill, and unless
# it holds it afterwards asked once and answered 4.
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

EVIDENCE="$ROOT/evidence/chat-queued-questions-kept"
FLOWS="$ROOT/.maestro/chat-queued-questions-kept"
QUESTION="What is 2+2? Answer with the number only."
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

run_flow "$FLOWS-part1-queued.yaml" "$EVIDENCE/queued" || fail "part 1 failed"
read_kept "$EVIDENCE/kept-before-the-kill.json"
grep -F "$QUESTION" "$EVIDENCE/kept-before-the-kill.json" || fail "the queued question was not kept"
# The process and the server it runs, with the reply still in flight.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
# Not asked before the kill, so only what was kept can ask it after the relaunch.
read_chat "$EVIDENCE/untitled-at-the-kill.chat"
if grep -F "$QUESTION" "$EVIDENCE/untitled-at-the-kill.chat"; then fail "the question was asked before the kill"; fi

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
read_kept "$EVIDENCE/kept-after-it-was-asked.json"
if grep -F "$QUESTION" "$EVIDENCE/kept-after-it-was-asked.json"; then fail "the question asked is still kept"; fi
read_chat "$EVIDENCE/untitled.chat"
pull_server_log "$EVIDENCE/jupyter_server.log"

python3 "$ROOT/scripts/chat_tool_call_reads.py" answered-in-turn "$EVIDENCE/untitled.chat" "$QUESTION" 4
asked=$(grep -oF "\"body\": \"$QUESTION\"" "$EVIDENCE/untitled.chat" | wc -l)
[ "$asked" -eq 1 ] || fail "the kept question was asked $asked times, expected once"
