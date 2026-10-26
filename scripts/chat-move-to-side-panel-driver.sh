#!/usr/bin/env bash
# A chat tab's long-press menu moves it to the side panel, which opens on that chat, and the tab closes:
# at two panes beside the document area, and at one pane as the window.
#
#   nice -n 10 timeout 3600 scripts/chat-move-to-side-panel-driver.sh
#
# Run on an install where opencode is installed in base and a person signed it in from base's
# terminal (`opencode console login`), with their own OpenRouter key in base's
# `~/.local/share/opencode/auth.json`, as scripts/chat-agent-controls-demo-driver.sh says why.
#
# Exits non-zero if either flow fails; if part 1's server completes other than one turn; if part 2's,
# restarted by its launch, completes any, waiting a minute past it; or unless the question is in
# untitled.chat once.
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

EVIDENCE="$ROOT/evidence/chat-move-to-side-panel"
FLOWS="$ROOT/.maestro/chat-in-tab-move-to-side-panel"
QUESTION="Reply with one word: tab. Then the sum 318+457 in digits."
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

# expect_turns <log> <count>: the server whose log was pulled to <log> completed <count> turns.
expect_turns() {
  [ "$(completed_turns "$1")" -eq "$2" ] || fail "$1: the server completed $(completed_turns "$1") turns; expected $2"
}

rotate_to 1
run_flow "$FLOWS-part1-two-panes.yaml" "$EVIDENCE/two-panes" || fail "part 1 failed"
# Pulled before part 2's launch restarts the app, whose server starts a new log. The turn's end is
# logged after its answer is drawn.
sleep 10
pull_server_log "$EVIDENCE/two-panes/jupyter_server.log"
expect_turns "$EVIDENCE/two-panes/jupyter_server.log" 1

rotate_to 0
run_flow "$FLOWS-part2-one-pane.yaml" "$EVIDENCE/one-pane" || fail "part 2 failed"
# A minute for a turn the moves asked for to show.
sleep 60
pull_server_log "$EVIDENCE/one-pane/jupyter_server.log"
expect_turns "$EVIDENCE/one-pane/jupyter_server.log" 0
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"

python3 -I - "$EVIDENCE/untitled.chat" "$QUESTION" <<'PY'
import json, sys
messages = json.load(open(sys.argv[1]))["messages"]
found = [m for m in messages if m["body"].strip() == sys.argv[2]]
assert len(found) == 1, f"expected {sys.argv[2]!r} once, found it {len(found)} times"
print(f"the question asked once, in {len(messages)} messages")
PY
