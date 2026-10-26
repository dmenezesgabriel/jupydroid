#!/usr/bin/env bash
# The demo of a chat in a tab, on one chat: asked in the side panel and moved to a tab, asked there and
# moved back, opened from Files as a chat tab and revealed when opened again; turned to one pane it is
# still open and still answers; after process death it comes back, and after a force-stop it does not.
#
#   nice -n 10 timeout 3600 scripts/chat-in-tab-demo-driver.sh
#
# Run on an install where opencode is installed in base and a person signed it in from base's
# terminal (`opencode console login`), with their own OpenRouter key in base's
# `~/.local/share/opencode/auth.json`, as scripts/chat-agent-controls-demo-driver.sh says why.
#
# Exits non-zero if a flow fails; if the turn to portrait relaunched the process; if the server
# completes other than three turns before the kill, waiting a minute past the third for a fourth;
# unless untitled.chat holds each of the three questions once and one answer to each; or if the process
# is still alive after `am kill`.
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

EVIDENCE="$ROOT/evidence/chat-in-tab-demo"
FLOWS="$ROOT/.maestro/chat-in-tab-demo"
QUESTIONS=(
  "Reply with one word: panel. Then the sum 136+529 in digits."
  "Reply with one word: tab. Then the sum 247+381 in digits."
  "Reply with one word: portrait. Then the sum 409+276 in digits."
)
OPENCODE_AUTH="files/spaces/base/home/jupydroid/.local/share/opencode/auth.json"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

completed_turns() { grep -c "prompt_and_reply: completed" "$1" || true; }

# The last answer is on screen when the flow ends, but the persona logs the turn's end after it, so it
# is polled for; then a minute more, so a turn a move asked again has time to show as a fourth.
await_exactly_turns() { # await_exactly_turns <count> <log>
  for _ in $(seq 30); do
    pull_server_log "$2"
    if [ "$(completed_turns "$2")" -ge "$1" ]; then break; fi
    sleep 10
  done
  sleep 60
  pull_server_log "$2"
  [ "$(completed_turns "$2")" -eq "$1" ] || fail "the server completed $(completed_turns "$2") turns; expected $1"
}

# How Android reclaims a backgrounded app: the process goes, the task and its saved state stay.
kill_in_background() {
  "${DEVICE_ADB[@]}" shell input keyevent KEYCODE_HOME
  sleep 3
  "${DEVICE_ADB[@]}" shell am kill "$APP"
  sleep 2
  [ -z "$(app_pid)" ] || fail "the app's process survived am kill"
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The provider's name only: the key itself never leaves the device.
"${DEVICE_ADB[@]}" shell "run-as $APP grep -q '\"openrouter\"' $OPENCODE_AUTH" ||
  fail "base holds no OpenRouter key in $OPENCODE_AUTH: a person puts their own there"
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"

rotate_to 1
run_flow "$FLOWS-part1-two-panes.yaml" "$EVIDENCE/two-panes" || fail "part 1 failed"
pid_before_turn="$(app_pid)"
rotate_to 0
[ "$(app_pid)" = "$pid_before_turn" ] || fail "the turn to portrait relaunched the process"
run_flow "$FLOWS-part2-one-pane.yaml" "$EVIDENCE/one-pane" || fail "part 2 failed"
await_exactly_turns "${#QUESTIONS[@]}" "$EVIDENCE/jupyter_server.log"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"

kill_in_background
run_flow "$FLOWS-part3-process-death.yaml" "$EVIDENCE/process-death" || fail "part 3 failed"
run_flow "$FLOWS-part4-force-stopped.yaml" "$EVIDENCE/force-stopped" || fail "part 4 failed"

python3 -I - "$EVIDENCE/untitled.chat" "${QUESTIONS[@]}" <<'PY'
import json, sys
messages = json.load(open(sys.argv[1]))["messages"]
for question in sys.argv[2:]:
    found = [m for m in messages if m["body"].strip() == question]
    assert len(found) == 1, f"expected {question!r} once, found it {len(found)} times"
# One answer each: a reload writing replayed history as a new message showed as a second one
# (tools/rootfs/acp_replayed_updates_patch.py).
expected = 2 * (len(sys.argv) - 2)
assert len(messages) == expected, f"untitled.chat holds {len(messages)} messages; expected {expected}"
print(f"each of {len(sys.argv) - 2} questions asked once and answered once, in {len(messages)} messages")
PY
