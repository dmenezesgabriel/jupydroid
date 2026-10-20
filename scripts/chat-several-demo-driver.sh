#!/usr/bin/env bash
# The demo of the Chat's several chats: on an empty workspace the panel offers only New chat; New chat
# makes `untitled`, which the agent answers, and a second makes `untitled1`, empty; `untitled` opens
# again with its answer; `untitled1` is renamed to `notes`, then deleted, from the list and from the
# explorer; `untitled` stays open across a turn of the tablet, in the same process; and after a kill
# the panel opens on the list, and `untitled` still holds its answer.
#
#   nice -n 10 timeout 2400 scripts/chat-several-demo-driver.sh
#
# Run from a fresh install, the app's state wiped first: `fresh_install` (scripts/maestro-run.sh),
# then scripts/jd-046-opencode-in-a-space-driver.sh, then a person signs opencode in from base's
# terminal - their own credential, which no script types, and which opencode keeps in its database
# rather than in a file a driver could wait for.
#
# Exits non-zero if a flow fails, if opencode is not installed in base, if the turn relaunched the
# process, or if the workspace does not hold `untitled.chat` alone afterwards.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"   # rotate_to, restore_rotation, app_pid

EVIDENCE="$ROOT/evidence/chat-several-demo"
FLOWS="$ROOT/.maestro/chat-several-demo"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$FLOWS-part1-new-and-ask.yaml" "$EVIDENCE/new-and-ask" || exit 1
run_flow "$FLOWS-part2-switch-rename-delete.yaml" "$EVIDENCE/switch-rename-delete" || exit 1

before="$(app_pid)"
rotate_to 0
after="$(app_pid)"
echo "pid before the turn $before, after it $after" | tee "$EVIDENCE/pid.txt"
[ -n "$before" ] && [ "$before" = "$after" ] || fail "the turn relaunched the process"
run_flow "$FLOWS-part3-turned.yaml" "$EVIDENCE/turned" || exit 1

rotate_to 1
run_flow "$FLOWS-part4-relaunched.yaml" "$EVIDENCE/relaunched" || exit 1

"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cd $NOTEBOOK_DIR && ls -l *.chat'" | tr -d '\r' |
  tee "$EVIDENCE/workspace-chats.txt"
[ "$(grep -c '\.chat$' "$EVIDENCE/workspace-chats.txt")" = 1 ] ||
  fail "the workspace holds more than one chat"
grep -q " untitled.chat$" "$EVIDENCE/workspace-chats.txt" || fail "the workspace holds no untitled.chat"
