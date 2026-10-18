#!/usr/bin/env bash
# The demo: in base, with opencode installed in the Space and signed in by a person, the Chat panel
# asks a question that uses no tool, shows opencode's answer growing and follows it to its last
# line, and after a kill and a relaunch reads both back from `untitled.chat`, the question still
# drawn as the user's.
#
#   nice -n 10 timeout 1800 scripts/jd-046-ai-chat-demo-driver.sh
#
# Two flows, as the screen is read between them: once the answer is whole the question must be off
# the top of the panel, and only then is the app killed. Exits non-zero if either flow fails, if
# opencode is not installed in base, if the panel did not follow the answer, if `untitled.chat` does
# not hold the question and one answer counting to 40, or if after the relaunch the question is
# drawn in the answer's colour.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs the tablet online, opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh)
# and signed in from base's terminal (`opencode auth login`) - a person's own credential, which no
# script types.
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
# shellcheck source=scripts/jd-046-chat-reads.sh
. "$ROOT/scripts/jd-046-chat-reads.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/t4e"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
mkdir -p "$EVIDENCE/asked" "$EVIDENCE/relaunched"
# Empties the workspace, `untitled.chat` with it, so the chat holds this run's messages only. The
# dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/jd-046-ai-chat-demo-ask.yaml" "$EVIDENCE/asked"
require_panel_followed_count "$EVIDENCE/asked"
require_chat_counted_to_40 "$EVIDENCE/asked"

run_flow "$ROOT/.maestro/jd-046-ai-chat-demo-relaunch.yaml" "$EVIDENCE/relaunched"
require_question_drawn_as_users "$EVIDENCE/relaunched" 02-the-question-above-its-answer.png "Count from 1 to 40" "1
2"
