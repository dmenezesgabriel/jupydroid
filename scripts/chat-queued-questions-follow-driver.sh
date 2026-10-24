#!/usr/bin/env bash
# The Chat panel's queued questions follow their chat: a question queued in "untitled" and one in
# "untitled1", each while opencode's bash call waits on approval, are kept when the app is killed. Once
# it is relaunched, "untitled" is renamed "notes" and "untitled1" deleted from the list, and the app's
# own storage keeps the first question under notes.chat and nothing under either old path. Opened,
# "notes" asks it, since the new server has no reply in flight.
#
#   nice -n 10 timeout 2400 scripts/chat-queued-questions-follow-driver.sh
#
# Exits non-zero if a flow fails, unless the app's own storage held each question under its chat
# before the kill, held only the first and only under notes.chat after the rename and the delete, and
# held nothing after notes asked it, and unless notes.chat holds it asked once and answered 4.
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

EVIDENCE="$ROOT/evidence/chat-queued-questions-follow"
FLOWS="$ROOT/.maestro/chat-queued-questions-follow"
FIRST="What is 2+2? Answer with the number only."
SECOND="What is 3+3? Answer with the number only."
# Where JupydroidApplication keeps base's queued questions.
KEPT="files/chat-queues/base.json"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_kept() { "${DEVICE_ADB[@]}" shell "run-as $APP cat $KEPT" | tr -d '\r' >"$1"; }

# Fails unless the JSON object in $1, chat path to questions, is exactly $2.
require_kept() {
  python3 -c 'import json, sys
kept, expected = json.load(open(sys.argv[1])), json.loads(sys.argv[2])
sys.exit(0 if kept == expected else f"{sys.argv[1]} holds {kept}, expected {expected}")' "$1" "$2"
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $KEPT $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

run_flow "$FLOWS-part1-queued.yaml" "$EVIDENCE/queued" || fail "part 1 failed"
read_kept "$EVIDENCE/kept-before-the-kill.json"
require_kept "$EVIDENCE/kept-before-the-kill.json" \
  "{\"untitled.chat\": [\"$FIRST\"], \"untitled1.chat\": [\"$SECOND\"]}"
# The process and the server it runs, with both replies still in flight and neither question asked.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"

run_flow "$FLOWS-part2-renamed-and-deleted.yaml" "$EVIDENCE/renamed-and-deleted" || fail "part 2 failed"
read_kept "$EVIDENCE/kept-after-the-rename-and-the-delete.json"
require_kept "$EVIDENCE/kept-after-the-rename-and-the-delete.json" "{\"notes.chat\": [\"$FIRST\"]}"

run_flow "$FLOWS-part3-asked-in-notes.yaml" "$EVIDENCE/asked-in-notes" || fail "part 3 failed"
read_kept "$EVIDENCE/kept-after-it-was-asked.json"
require_kept "$EVIDENCE/kept-after-it-was-asked.json" "{}"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/notes.chat\"" | tr -d '\r' >"$EVIDENCE/notes.chat"
pull_server_log "$EVIDENCE/jupyter_server.log"

python3 "$ROOT/scripts/chat_tool_call_reads.py" answered-in-turn "$EVIDENCE/notes.chat" "$FIRST" 4
asked=$(grep -oF "\"body\": \"$FIRST\"" "$EVIDENCE/notes.chat" | wc -l)
[ "$asked" -eq 1 ] || fail "the kept question was asked $asked times, expected once"
