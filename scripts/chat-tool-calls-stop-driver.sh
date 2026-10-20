#!/usr/bin/env bash
# The Chat panel's Stop cancels opencode's reply while it arrives: Stop shows beside Send while the
# agent writes, a tap on it stops the answer short, and Stop goes away.
#
#   nice -n 10 timeout 1800 scripts/chat-tool-calls-stop-driver.sh
#
# Exits non-zero if the flow fails, if the server logged no answered cancel, or unless
# `untitled.chat`, read twice SETTLE_SECONDS apart after the flow, holds the same answer both times
# and that answer never reached 1000 - then the reply was not stopped, only the screen.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh).
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

EVIDENCE="$ROOT/evidence/chat-tool-calls-stop"
# Long enough for a reply that was not stopped to have written more lines.
SETTLE_SECONDS=15

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_chat() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$1"; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

status=0
run_flow "$ROOT/.maestro/chat-tool-calls-stop.yaml" "$EVIDENCE" || status=$?
[ "$status" -eq 0 ] || fail "the flow failed ($status)"
sleep "$SETTLE_SECONDS"
read_chat "$EVIDENCE/untitled.chat"
sleep "$SETTLE_SECONDS"
read_chat "$EVIDENCE/untitled-later.chat"
pull_server_log "$EVIDENCE/jupyter_server.log"

python3 - "$EVIDENCE/untitled.chat" "$EVIDENCE/untitled-later.chat" <<'PY'
import json, re, sys
def answer(path):
    messages = json.load(open(path))["messages"]
    return [m for m in messages if m["sender"].startswith("jupyter-ai-personas::")][-1]["body"]
first, later = answer(sys.argv[1]), answer(sys.argv[2])
numbers = [int(n) for n in re.findall(r"\d+", first)]
print(f"untitled.chat: the answer holds {len(numbers)} numbers, the last {numbers[-1] if numbers else None}")
assert first == later, f"the answer grew after Stop: {len(first)} then {len(later)} characters"
assert numbers and max(numbers) < 1000, f"the answer reached {max(numbers, default=None)}"
PY
# A tap that misses Stop leaves the reply to end by itself and no cancel in the log.
grep "200 POST /api/ai/personas/cancel" "$EVIDENCE/jupyter_server.log" || fail "the server took no cancel"
