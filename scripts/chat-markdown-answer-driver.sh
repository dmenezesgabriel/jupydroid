#!/usr/bin/env bash
# The Chat panel draws an agent's answer as a markdown cell is drawn: opencode answers with a heading,
# a list and a fenced block, and the panel shows the heading as a line of its own, a bullet, and the
# code, with none of the marks.
#
#   nice -n 10 timeout 1800 scripts/chat-markdown-answer-driver.sh
#
# Exits non-zero if the flow fails, or if `untitled.chat` afterwards does not hold the heading's and the
# fence's marks - then the answer was never markdown, and nothing on screen was drawn from it.
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
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-markdown-answer"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/chat-markdown-answer.yaml" "$EVIDENCE"

wait_for_workspace_text untitled.chat "Persimmon" 60
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
grep -qF '## Persimmon' "$EVIDENCE/untitled.chat" || fail "the answer holds no '## Persimmon': it was not markdown"
grep -qF '```' "$EVIDENCE/untitled.chat" || fail "the answer holds no fence: it was not markdown"
