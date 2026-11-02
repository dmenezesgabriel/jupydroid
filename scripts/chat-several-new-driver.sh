#!/usr/bin/env bash
# New chat in the Chat panel makes an untitled chat, opens it, and the agent answers a question
# there; a second New chat is another file. The workspace is then read to show both files exist.
#
#   nice -n 10 timeout 1800 scripts/chat-several-new-driver.sh
#
# Exits non-zero if the flow fails, or if the workspace does not hold `untitled.chat` and
# `untitled1.chat` afterwards.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/opencode-in-a-space-driver.sh) and signed in from
# base's terminal (`opencode auth login`) - a person's own credential, which no script types.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-several-new"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/chat-several-new.yaml" "$EVIDENCE"

"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cd $NOTEBOOK_DIR && ls -l *.chat'" | tr -d '\r' |
  tee "$EVIDENCE/workspace-chats.txt"
for chat in untitled.chat untitled1.chat; do
  grep -q " $chat\$" "$EVIDENCE/workspace-chats.txt" || fail "the workspace holds no $chat"
done
