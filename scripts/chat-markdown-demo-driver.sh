#!/usr/bin/env bash
# The demo of an agent's answer read as markdown, in one chat: opencode answers with a heading, a list
# and a fenced print(6*7), drawn without their marks; Copy under the code block and the platform's
# Paste put it in a new notebook's cell; Insert under the same block puts it in a cell below that one;
# a keystroke after it lands in no cell; and after a kill the answer read back is still drawn as markdown.
#
#   nice -n 10 timeout 2400 scripts/chat-markdown-demo-driver.sh
#
# Run from a fresh install, the app's state wiped first: `fresh_install` (scripts/maestro-run.sh),
# then scripts/opencode-in-a-space-driver.sh, then a person signs opencode in from base's
# terminal (`opencode console login`) - their own credential, which no script types.
#
# Exits non-zero if a flow fails; unless `untitled.chat` holds the heading's and the fence's marks,
# without which nothing on screen was drawn from markdown; and unless `Untitled.ipynb` holds exactly
# two code cells of print(6*7), the pasted one and the inserted one below it.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-markdown-demo"
FLOWS="$ROOT/.maestro/chat-markdown-demo"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

read_notebook() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" | tr -d '\r' >"$1"; }
holds_both_cells() {
  python3 "$ROOT/scripts/notebook_cell_reads.py" "$1" 'code:print(6*7)' 'code:print(6*7)'
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$FLOWS-part1-copy-insert.yaml" "$EVIDENCE/copy-insert" || fail "part 1 failed"
# Both cells saved before the kill, which would otherwise take an unsaved insert with it.
for _ in $(seq 60); do
  read_notebook "$EVIDENCE/Untitled-before-the-kill.ipynb"
  holds_both_cells "$EVIDENCE/Untitled-before-the-kill.ipynb" && break
  sleep 1
done
holds_both_cells "$EVIDENCE/Untitled-before-the-kill.ipynb" || fail "Untitled.ipynb was never saved with both cells"
"${DEVICE_ADB[@]}" shell am force-stop "$APP"

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"

"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
read_notebook "$EVIDENCE/Untitled.ipynb"
grep -qF '## Persimmon' "$EVIDENCE/untitled.chat" || fail "the answer holds no '## Persimmon': it was not markdown"
grep -qF '```' "$EVIDENCE/untitled.chat" || fail "the answer holds no fence: it was not markdown"
holds_both_cells "$EVIDENCE/Untitled.ipynb" ||
  fail "Untitled.ipynb is not two code cells of exactly print(6*7)"
