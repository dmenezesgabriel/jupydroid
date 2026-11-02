#!/usr/bin/env bash
# The Chat panel, contributed by the AI extension, asks jupyter-ai's opencode persona and shows
# opencode's answer as one message while the agent streams it.
#
#   nice -n 10 timeout 1800 scripts/jd-046-ai-chat-driver.sh [evidence subdirectory, t3 by default]
#
# Exits non-zero if the flow fails, if opencode is not installed in base, or if `untitled.chat`
# afterwards does not hold the question, addressed to the persona, and one answer from it that
# counts to 40, or if the panel still shows the question once the answer is whole: an answer 40 lines
# tall pushes it above the list when the panel follows the answer to its last line.
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
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"
# shellcheck source=scripts/jd-046-chat-reads.sh
. "$ROOT/scripts/jd-046-chat-reads.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/${1:-t3}"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
mkdir -p "$EVIDENCE"
# Empties the workspace, `untitled.chat` with it, so the chat holds this run's messages only. The
# dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/jd-046-ai-chat.yaml" "$EVIDENCE"

require_panel_followed_count "$EVIDENCE"
require_chat_counted_to_40 "$EVIDENCE"
