#!/usr/bin/env bash
# The model chip under the Chat's input: in a new chat it names opencode's model before anything is
# asked, Beam Free is picked from its list and a question asked with it; after a kill, the chat opened
# again still names Beam Free.
#
#   nice -n 10 timeout 2400 scripts/chat-model-picker-driver.sh
#
# Run on an install where opencode is installed in base and a person signed it in from base's
# terminal (`opencode console login`) - their own credential, which no script types.
#
# Exits non-zero if a flow fails, and unless untitled.chat shows the pick reached the persona: the
# question carries `metadata.model.id`, and the chat's `acp_config_options` keeps that same model for
# opencode's persona, which `_record_config_choice` writes only once the agent switched to it
# (usr/lib/python3.12/site-packages/jupyter_ai_acp_client/base_acp_persona.py in the shipped rootfs).
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
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/chat-model-picker"
FLOWS="$ROOT/.maestro/chat-model-picker"
QUESTION="Reply with only the word ready."
PERSONA="jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_workspace_file() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$1\"" | tr -d '\r' >"$2"; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$FLOWS-part1-pick-and-ask.yaml" "$EVIDENCE/pick-and-ask" || fail "part 1 failed"
read_workspace_file untitled.chat "$EVIDENCE/untitled.chat"
pull_server_log "$EVIDENCE/jupyter_server.log"

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"

python3 -I - "$EVIDENCE/untitled.chat" "$QUESTION" "$PERSONA" <<'PY'
import json, sys
chat, question, persona = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
asked = [m for m in chat["messages"] if m["body"] == question]
assert len(asked) == 1, f"expected the one question {question!r}, found {len(asked)}"
picked = asked[0]["metadata"]["model"]["id"]
kept = chat["metadata"]["acp_config_options"][persona]["model"]
assert picked == kept, f"the question picked {picked!r} but the persona kept {kept!r}"
assert "beam" in picked.lower(), f"the question picked {picked!r}, not Beam Free"
print(f"asked with {picked}, and the persona kept {kept}")
PY
