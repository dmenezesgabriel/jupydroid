#!/usr/bin/env bash
# A Chat harness is installed from a stopped Space's Settings: a cold install opens Base's
# Settings on the Harnesses rows answering "Not installed", each row expands to the exact script
# bytes before Install runs them, both install with their output streaming into the row, a new
# Chat's agent picker lists both personas, and after a kill and relaunch the rows still report
# installed - the live probe's answer, not stored state.
#
#   ANDROID_SERIAL=TABLET_SERIAL scripts/chat-harness-install-driver.sh [evidence subdirectory, run by default]
#
# TABLET ONLY (SM-X216B): the probe and the install exec inside the Space's real proot shell,
# which only arm64 ships a rootfs for. The demo runs at one pane (portrait) because that is where
# the Chat's "More" -> "Chat" entry the picker flows use lives.
#
# Neither harness is signed in - a fresh install holds no credentials, and sign-in is its own
# item - so the ask's ending is whatever an unsigned harness honestly does; the frame is the
# evidence of which.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="$ROOT/evidence/chat-harness-install/${1:-run}"
FLOWS="$ROOT/.maestro/chat-harness-install"

fail() { echo "$1" >&2; exit 1; }

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
fresh_install "$EVIDENCE/install"
# launcherSeen is seeded here, as every workspace-entering driver needs it: a fresh install's
# first entry would otherwise fill the main area with the launcher.
reset_notebooks
rotate_to 0

run_flow "$FLOWS-part1-installs.yaml" "$EVIDENCE/installs" || fail "part 1 failed"
run_flow "$FLOWS-part2-chat-picker.yaml" "$EVIDENCE/chat" || fail "part 2 failed"
run_flow "$FLOWS-part3-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 3 failed"

# What the Space's disk holds after the demo, read back through run-as: presence and size of each
# installed binary. Their versions are the Settings rows' own probe answers, in the frames.
{
  echo "# run-as reads of files/spaces/base/usr/local/bin after the demo"
  for binary in opencode claude-agent-acp; do
    if "${adb[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/$binary"; then
      echo "$binary: installed"
      "${adb[@]}" shell "run-as $APP ls -l files/spaces/base/usr/local/bin/$binary" | tr -d '\r'
    else
      echo "$binary: ABSENT"
    fi
  done
} >"$EVIDENCE/captures.txt"
cat "$EVIDENCE/captures.txt"
