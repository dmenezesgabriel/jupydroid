#!/usr/bin/env bash
# The chat open in the Chat panel moves to a tab from its header, and the panel goes back to its chats;
# moved again, the tab already open is revealed.
#
#   nice -n 10 timeout 3600 scripts/chat-move-to-tab-driver.sh
#
# Run on an install where opencode is installed in base and a person signed it in from base's
# terminal (`opencode console login`), with their own OpenRouter key in base's
# `~/.local/share/opencode/auth.json`, as scripts/chat-agent-controls-demo-driver.sh says why.
#
# Exits non-zero if the flow fails; if the server completes other than one turn, waiting a minute past
# it for a second; or unless the question is in untitled.chat once.
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

EVIDENCE="$ROOT/evidence/chat-move-to-tab"
QUESTION="Reply with one word: panel. Then the sum 251+374 in digits."
OPENCODE_AUTH="files/spaces/base/home/jupydroid/.local/share/opencode/auth.json"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

completed_turns() { grep -c "prompt_and_reply: completed" "$1" || true; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The provider's name only: the key itself never leaves the device.
"${DEVICE_ADB[@]}" shell "run-as $APP grep -q '\"openrouter\"' $OPENCODE_AUTH" ||
  fail "base holds no OpenRouter key in $OPENCODE_AUTH: a person puts their own there"
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

run_flow "$ROOT/.maestro/chat-in-tab-move-to-tab.yaml" "$EVIDENCE" || fail "the flow failed"

# The turn's end is logged after its answer is drawn, then a minute more for a second turn to show.
sleep 60
pull_server_log "$EVIDENCE/jupyter_server.log"
[ "$(completed_turns "$EVIDENCE/jupyter_server.log")" -eq 1 ] ||
  fail "the server completed $(completed_turns "$EVIDENCE/jupyter_server.log") turns; expected 1"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"

python3 -I - "$EVIDENCE/untitled.chat" "$QUESTION" <<'PY'
import json, sys
messages = json.load(open(sys.argv[1]))["messages"]
found = [m for m in messages if m["body"].strip() == sys.argv[2]]
assert len(found) == 1, f"expected {sys.argv[2]!r} once, found it {len(found)} times"
print(f"the question asked once, in {len(messages)} messages")
PY
