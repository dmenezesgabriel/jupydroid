#!/usr/bin/env bash
# The Chat panel, after a kill and a relaunch, reads the question and opencode's answer back from
# `untitled.chat` and still draws the question as the user's.
#
#   nice -n 10 timeout 1800 scripts/ai-chat-relaunch-driver.sh [evidence subdirectory, run by default]
#
# Exits non-zero if the flow fails, if opencode is not installed in base, if `untitled.chat` does not
# hold the question and an answer of 42 from the persona, or if, after the relaunch, the question's
# row is drawn in the same colour as the answer's. That colour is the only thing on screen that says
# whose a message is, and no flow can read a colour, so it is read off frame 02 within each row's
# bounds (`require_question_drawn_as_users`, scripts/chat-reads.sh).
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs the tablet online, opencode installed in base (scripts/opencode-in-a-space-driver.sh)
# and signed in from base's terminal (`opencode auth login`) - a person's own credential, which no
# script types.
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
# shellcheck source=scripts/chat-reads.sh
. "$ROOT/scripts/chat-reads.sh"

EVIDENCE="$ROOT/evidence/ai-chat-relaunch/${1:-run}"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
mkdir -p "$EVIDENCE"
# Empties the workspace, `untitled.chat` with it, so the chat holds this run's messages only. The
# dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/ai-chat-relaunch.yaml" "$EVIDENCE"

# The screen is still frame 02's: both rows are whole and nothing is streaming.
pull_chat_file "$EVIDENCE"
python3 - "$EVIDENCE/untitled.chat" "$CHAT_PERSONA" <<'PY'
import json, sys
messages = json.load(open(sys.argv[1]))["messages"]
question = "What is 6 times 7? Reply with the number only."
assert any(m["body"] == question for m in messages), f"the question is not in the chat: {messages}"
assert any(m["sender"] == sys.argv[2] and m["body"].strip() == "42" for m in messages), f"no answer of 42: {messages}"
PY
require_question_drawn_as_users "$EVIDENCE" 02-read-back-after-the-kill.png "What is 6 times 7?" "42"
