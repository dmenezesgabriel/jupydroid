#!/usr/bin/env bash
# The Chat panel, contributed by the AI extension, asks jupyter-ai's opencode persona and shows
# opencode's answer as one message while the agent streams it.
#
#   nice -n 10 timeout 1800 scripts/jd-046-ai-chat-driver.sh
#
# Exits non-zero if the flow fails, if opencode is not installed in base, or if `untitled.chat`
# afterwards does not hold the question, addressed to the persona, and one answer from it that
# counts to 40.
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
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/t3"

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

# The server writes the chat on its own schedule, so the file is waited for, not read at once.
wait_for_workspace_text untitled.chat "40" 60
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
# One line per chunk the persona appended (`agent_message_chunk: <n> chars`); the log itself stays
# local, as it holds the server's token.
pull_server_log "$EVIDENCE/.server.log"
grep -o "agent_message_chunk: [0-9]* chars" "$EVIDENCE/.server.log" >"$EVIDENCE/chunks.txt" || true
rm -f "$EVIDENCE/.server.log"
python3 - "$EVIDENCE/untitled.chat" "$EVIDENCE/chunks.txt" <<'PY'
import json, sys
messages = json.load(open(sys.argv[1]))["messages"]
chunks = open(sys.argv[2]).read().splitlines()
persona = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
asked = [m for m in messages if m["body"].startswith("Count from 1 to 40")]
assert asked, f"the question is not in the chat: {messages}"
assert asked[0].get("metadata", {}).get("to_persona") == persona, f"not addressed to opencode: {asked[0]}"
counts = [m for m in messages if m["sender"] == persona and m["body"].split() == [str(n) for n in range(1, 41)]]
assert len(counts) == 1, f"expected one persona message counting 1..40, got {len(counts)}: {messages}"
print(f"untitled.chat: the question, addressed to {persona}, and one answer counting to 40, "
      f"appended from {len(chunks)} chunk(s)")
PY
