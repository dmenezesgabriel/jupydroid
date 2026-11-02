#!/usr/bin/env bash
# The demo: in base, with opencode installed in the Space and signed in by a person, the Chat panel
# asks a question that uses no tool, shows opencode's answer growing and follows it to its last
# line, and after a kill and a relaunch reads both back from `untitled.chat`, the question still
# drawn as the user's. Then, left open past base's idle setting, the chat is still open and answers
# again, and the input's geometry is read off the screen it leaves.
#
#   nice -n 10 timeout 2400 scripts/ai-chat-demo-driver.sh <evidence subdirectory, e.g. first-run>
#
# Separate flows, as the screen is read between them: once the answer is whole the question must be
# off the top of the panel, and only then is the app killed; the idle wait is the driver's, between
# two flows. Exits non-zero if any flow fails, if opencode is not installed in base, if the panel did
# not follow the answer, if `untitled.chat` does not hold the question and one answer counting to
# 40, if after the relaunch the question is drawn in the answer's colour, or if the server stopped
# itself under the open chat.
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

EVIDENCE="$ROOT/evidence/ai-chat-demo/${1:?evidence subdirectory, e.g. first-run}"
# The cull at 1 to 2 minutes after the start, then one more 60 s check past a 60 s timeout, as
# scripts/ai-chat-keeps-server-driver.sh waits.
IDLE_WAIT_SECONDS=270

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
mkdir -p "$EVIDENCE/asked" "$EVIDENCE/relaunched" "$EVIDENCE/idle"
# Empties the workspace, `untitled.chat` with it, so the chat holds this run's messages only. The
# dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/ai-chat-demo-ask.yaml" "$EVIDENCE/asked"
require_panel_followed_count "$EVIDENCE/asked"
require_chat_counted_to_40 "$EVIDENCE/asked"

run_flow "$ROOT/.maestro/ai-chat-demo-relaunch.yaml" "$EVIDENCE/relaunched"
require_question_drawn_as_users "$EVIDENCE/relaunched" 02-the-question-above-its-answer.png "Count from 1 to 40" "1
2"

# base idles out after 1 minute, Chat is left open past it, and the question asked after the wait
# is answered. The idle setting is put back whether or not the ask passed.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
status=0
run_flow "$ROOT/.maestro/ai-chat-keeps-server-opened.yaml" "$EVIDENCE/idle/set" || status=$?
[ "$status" -eq 0 ] && sleep "$IDLE_WAIT_SECONDS"
[ "$status" -eq 0 ] &&
  { run_flow "$ROOT/.maestro/ai-chat-demo-idle-ask.yaml" "$EVIDENCE/idle/asked" || status=$?; }
# Read before part 3's launch stops the app and the panel with it.
[ "$status" -eq 0 ] && print_input_geometry "$EVIDENCE/idle/asked"
pull_server_log "$EVIDENCE/idle/jupyter_server.log"
run_flow "$ROOT/.maestro/ai-chat-keeps-server-idle-restored.yaml" "$EVIDENCE/idle/reset" ||
  fail "the idle setting was not put back"
require_server_kept_up "$EVIDENCE/idle"
[ "$status" -eq 0 ] || fail "a flow after the relaunch failed ($status)"
