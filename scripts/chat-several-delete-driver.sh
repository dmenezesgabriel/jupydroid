#!/usr/bin/env bash
# A chat the agent answered is deleted from the Chat panel's list, after a confirm, and a new chat
# then takes its name and opens empty. The workspace is then read to show the file went for good.
#
#   nice -n 10 timeout 1800 scripts/chat-several-delete-driver.sh
#
# Exits non-zero if the flow fails; if the workspace does not hold `untitled1.chat` and a new
# `untitled.chat` afterwards; if that new chat holds the deleted one's question, which a reused live
# model would write back; or if the delete left a trash directory in the workspace.
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

EVIDENCE="$ROOT/evidence/chat-several-delete"
# jupyterlab_chat's scan frees a chat whose file is gone every 60 s (`chat_manager.py`); a model
# still held past it would have written the file back by then.
FREE_SCAN_SECONDS=70

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/chat-several-delete.yaml" "$EVIDENCE"
sleep "$FREE_SCAN_SECONDS"

"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cd $NOTEBOOK_DIR && ls -l *.chat'" | tr -d '\r' |
  tee "$EVIDENCE/workspace-chats.txt"
grep -q " untitled1.chat$" "$EVIDENCE/workspace-chats.txt" || fail "the workspace holds no untitled1.chat"
grep -q " untitled.chat$" "$EVIDENCE/workspace-chats.txt" || fail "the workspace holds no new untitled.chat"
if "${DEVICE_ADB[@]}" shell "run-as $APP grep -q 'Count from 1 to 5' $NOTEBOOK_DIR/untitled.chat"; then
  fail "the new untitled.chat holds the deleted chat's question"
fi
if "${DEVICE_ADB[@]}" shell "run-as $APP test -e $NOTEBOOK_DIR/.local/share/Trash"; then
  fail "the delete left a trash directory in the workspace"
fi
echo "no trash directory under the workspace" | tee "$EVIDENCE/workspace-trash.txt"
