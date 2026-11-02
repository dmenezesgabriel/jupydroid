#!/usr/bin/env bash
# The Chat's chips take their whole width while the row has room: a new chat with Claude picked, then
# OpenCode, in portrait at one pane, then OpenCode in landscape, where they fold into one chip. Then a kill:
# the chat reopened in portrait, Claude and OpenCode picked again, and landscape again. Each window's named
# bounds are dumped so a chip's width is read, not eyeballed.
#
#   nice -n 10 timeout 1800 scripts/chat-chips-full-width-driver.sh <evidence subdirectory, e.g. t1/after>
#
# Run on an install where base holds opencode and Claude's ACP adapter, both signed in, as
# scripts/agent-pick-driver.sh requires. Nothing is asked, so no agent's quota is spent.
#
# TABLET ONLY (SM-X216B): an agent lists its chips from a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-063-chat-chips/${1:?which evidence subdirectory, e.g. t1/after}"
FLOWS="$ROOT/.maestro/chat-chips-full-width"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/claude-agent-acp" ||
  fail "Claude's ACP adapter is not installed in base: run scripts/claude-acp-in-a-space-driver.sh first"
reset_notebooks
rotate_to 0

run_flow "$FLOWS-part1-claude.yaml" "$EVIDENCE/claude" || fail "part 1 failed"
dump_named_bounds "$EVIDENCE/claude"
run_flow "$FLOWS-part2-opencode.yaml" "$EVIDENCE/opencode" || fail "part 2 failed"
dump_named_bounds "$EVIDENCE/opencode"
rotate_to 1
run_flow "$FLOWS-part3-landscape.yaml" "$EVIDENCE/landscape" || fail "part 3 failed"
dump_named_bounds "$EVIDENCE/landscape"
rotate_to 0
run_flow "$FLOWS-part4-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 4 failed"
dump_named_bounds "$EVIDENCE/relaunched"
rotate_to 1
run_flow "$FLOWS-part3-landscape.yaml" "$EVIDENCE/relaunched-landscape" || fail "part 3, relaunched, failed"
dump_named_bounds "$EVIDENCE/relaunched-landscape"
