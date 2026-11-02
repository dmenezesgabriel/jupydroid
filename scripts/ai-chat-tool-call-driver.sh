#!/usr/bin/env bash
# The Chat panel draws no row for a message that holds only an agent's tool calls: opencode reads a
# file with its read tool, and the panel shows the question and the answer and nothing between.
#
#   nice -n 10 timeout 1800 scripts/ai-chat-tool-call-driver.sh
#
# Exits non-zero if the flow fails, if opencode is not installed in base, or if `untitled.chat`
# afterwards holds no empty message from opencode's persona - then the run proved nothing about it -
# or no answer naming the probe's word.
#
# TABLET ONLY (SM-X216B), with opencode installed in base and signed in, as
# scripts/ai-chat-streamed-answer-driver.sh needs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/ai-chat-tool-call"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
mkdir -p "$EVIDENCE"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/read-tool-probe.txt" probe.txt
rotate_to 1

run_flow "$ROOT/.maestro/ai-chat-tool-call.yaml" "$EVIDENCE"

wait_for_workspace_text untitled.chat "kumquat" 60
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
python3 - "$EVIDENCE/untitled.chat" <<'PY'
import json, sys
messages = json.load(open(sys.argv[1]))["messages"]
persona = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
replies = [m for m in messages if m["sender"] == persona]
empty = [m for m in replies if m["body"] == ""]
assert empty, f"opencode's persona sent no empty message, so the run shows nothing hidden: {replies}"
assert any("kumquat" in m["body"].lower() for m in replies), f"no answer names the probe's word: {replies}"
print(f"untitled.chat: {len(empty)} empty persona message(s), holding {len(empty[0].get('metadata', {}))} "
      f"metadata key(s), and an answer naming kumquat")
PY
