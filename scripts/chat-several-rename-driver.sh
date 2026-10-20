#!/usr/bin/env bash
# A chat the agent answered is renamed from the Chat panel's list, keeps its answer, and is answered
# again under its new name. The workspace is then read to show the file moved.
#
#   nice -n 10 timeout 1800 scripts/chat-several-rename-driver.sh
#
# Exits non-zero if the flow fails, or if the workspace does not hold `notes.chat`, or still holds
# `untitled.chat`, afterwards.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh) and signed in from
# base's terminal (`opencode auth login`) - a person's own credential, which no script types.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-several-rename"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/chat-several-rename.yaml" "$EVIDENCE"

"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cd $NOTEBOOK_DIR && ls -l *.chat'" | tr -d '\r' |
  tee "$EVIDENCE/workspace-chats.txt"
grep -q " notes.chat$" "$EVIDENCE/workspace-chats.txt" || fail "the workspace holds no notes.chat"
if grep -q " untitled.chat$" "$EVIDENCE/workspace-chats.txt"; then fail "the workspace still holds untitled.chat"; fi
