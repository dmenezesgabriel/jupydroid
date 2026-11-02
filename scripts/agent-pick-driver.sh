#!/usr/bin/env bash
# The Chat asks the agent the user picked: with opencode and Claude both in base, a new chat lists both
# and chooses neither; Claude, picked, answers under its name, then OpenCode does; after a kill, the
# chat opened again asks OpenCode, the agent its newest question went to.
#
#   nice -n 10 timeout 2400 scripts/agent-pick-driver.sh [evidence subdirectory, run by default]
#
# Run on an install where base holds opencode, signed in with a person's own OpenRouter key as
# scripts/chat-agent-controls-demo-driver.sh says, and Claude's ACP adapter, installed as
# scripts/claude-acp-in-a-space-driver.sh does and signed in by a person from base's terminal.
#
# Exits non-zero if a flow fails, or unless untitled.chat holds each question addressed to the persona
# whose answer follows it: the name on screen is the server's routing, not one the app made up.
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

EVIDENCE="$ROOT/evidence/agent-pick/${1:-run}"
FLOWS="$ROOT/.maestro/agent-pick"
OPENCODE="jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
CLAUDE="jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona"
OPENCODE_AUTH="files/spaces/base/home/jupydroid/.local/share/opencode/auth.json"
CLAUDE_CREDENTIALS="files/spaces/base/home/jupydroid/.claude/.credentials.json"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/claude-agent-acp" ||
  fail "Claude's ACP adapter is not installed in base: run scripts/claude-acp-in-a-space-driver.sh first"
# Each sign-in's presence only: no key or token leaves the device.
"${DEVICE_ADB[@]}" shell "run-as $APP grep -q '\"openrouter\"' $OPENCODE_AUTH" ||
  fail "base holds no OpenRouter key in $OPENCODE_AUTH: a person puts their own there"
"${DEVICE_ADB[@]}" shell "run-as $APP test -s $CLAUDE_CREDENTIALS" ||
  fail "base holds no Claude sign-in in $CLAUDE_CREDENTIALS: a person signs in from base's terminal"
reset_notebooks
rotate_to 0

run_flow "$FLOWS-asked.yaml" "$EVIDENCE/asked" || fail "part 1 failed"
run_flow "$FLOWS-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
dump_named_bounds "$EVIDENCE/relaunched"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"

python3 -I - "$EVIDENCE/untitled.chat" "$CLAUDE" "$OPENCODE" <<'PY'
import json, re, sys
chat, claude, opencode = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
messages = chat["messages"]

def asked_and_answered(question, total):
    asked = [m for m in messages if question in m["body"]]
    assert len(asked) == 1, f"untitled.chat holds {len(asked)} questions with {question!r}"
    to = (asked[0].get("metadata") or {}).get("to_persona")
    answers = [m for m in messages if re.search(rf"\b{total}\b", m["body"]) and question not in m["body"]]
    assert answers, f"no message in untitled.chat holds the answer's {total}"
    return to, {m["sender"] for m in answers}

for question, total, persona, name in (("347+258", 605, claude, "Claude"), ("512+219", 731, opencode, "OpenCode")):
    to, senders = asked_and_answered(question, total)
    assert to == persona, f"the question with {question} was asked of {to}; expected {persona}"
    assert senders == {persona}, f"its answer was sent by {senders}; expected {persona}"
    shown = chat["users"][persona]["display_name"]
    assert shown == name, f"the users map names {persona} {shown!r}; expected {name!r}"
    print(f"{question} was asked of {name}, and {name} answered it with {total}")
PY
