#!/usr/bin/env bash
# The demo of the Chat's agent controls, at one pane, in one chat: before any question the chips name
# opencode's model and mode; North Mini Code is picked and asked with; plan is picked and
# opencode, asked to write a file, does not; `/` lists opencode's commands and `/review` picked and
# sent runs; after a kill the chat opened again still names both picks.
#
#   nice -n 10 timeout 2400 scripts/chat-agent-controls-demo-driver.sh
#
# Run on an install where opencode is installed in base and a person signed it in from base's
# terminal (`opencode console login`) - their own credential, which no script types - and put their
# own OpenRouter key in base's `~/.local/share/opencode/auth.json` (`opencode auth login` writes the
# same file), since OpenCode's own models answered no question here on 2026-10-26: free ones "Free
# usage limit reached.", paid ones "Insufficient account funds".
#
# Exits non-zero if a flow fails; if the server does not complete three turns after part 1; if
# sorbet.txt exists; and unless untitled.chat shows each pick reached the persona and `/review` ran:
# the model question carries North Mini Code's `metadata.model.id` and the plan question
# `metadata.settings.__mode__` plan, each kept in the chat's `acp_config_options` (`_record_config_choice`,
# usr/lib/python3.12/site-packages/jupyter_ai_acp_client/base_acp_persona.py in the shipped rootfs); no
# edit call completed; and after `/review` a call completed with opencode's review prompt, which no
# typed question holds.
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

EVIDENCE="$ROOT/evidence/chat-agent-controls-demo"
FLOWS="$ROOT/.maestro/chat-agent-controls-demo"
MODEL_QUESTION="Which model are you? One line, then the sum 347+258 in digits."
PLAN_QUESTION="Create the file sorbet.txt holding sorbet. End your reply with the sum 512+219 in digits."
PERSONA="jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
OPENCODE_AUTH="files/spaces/base/home/jupydroid/.local/share/opencode/auth.json"
TURNS=3

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_workspace_file() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$1\"" | tr -d '\r' >"$2"; }

# The flow ends once /review's call is a row; its answer has no word to wait for on screen, so the turn's
# end is read where the persona logs it (`prompt_and_reply: completed`), polled for up to 10 minutes.
await_turns() { # await_turns <count> <log>
  local completed
  for _ in $(seq 60); do
    pull_server_log "$2"
    completed=$(grep -c "prompt_and_reply: completed" "$2" || true)
    if [ "$completed" -ge "$1" ]; then return 0; fi
    sleep 10
  done
  fail "the server completed $completed turns of $1 in 10 minutes"
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/opencode-in-a-space-driver.sh first"
# The provider's name only: the key itself never leaves the device.
"${DEVICE_ADB[@]}" shell "run-as $APP grep -q '\"openrouter\"' $OPENCODE_AUTH" ||
  fail "base holds no OpenRouter key in $OPENCODE_AUTH: a person puts their own there"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 0

run_flow "$FLOWS-part1-pick-ask-plan-review.yaml" "$EVIDENCE/pick-ask-plan-review" || fail "part 1 failed"
await_turns "$TURNS" "$EVIDENCE/jupyter_server.log"
read_workspace_file untitled.chat "$EVIDENCE/untitled.chat"
if "${DEVICE_ADB[@]}" shell "run-as $APP test -e \"$NOTEBOOK_DIR/sorbet.txt\""; then
  fail "sorbet.txt exists: plan mode wrote the file"
fi

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"

python3 -I - "$EVIDENCE/untitled.chat" "$MODEL_QUESTION" "$PLAN_QUESTION" "$PERSONA" <<'PY'
import json, sys
chat, model_question, plan_question, persona = json.load(open(sys.argv[1])), *sys.argv[2:]
messages = chat["messages"]
kept = chat["metadata"]["acp_config_options"][persona]

def asked(body):
    found = [m for m in messages if m["body"].strip() == body]
    assert len(found) == 1, f"expected the one message {body!r}, found {len(found)}"
    return found[0]

model = asked(model_question)["metadata"]["model"]["id"]
assert model == kept["model"] == "openrouter/cohere/north-mini-code:free", \
    f"asked with {model!r}, kept {kept['model']!r}; expected North Mini Code"
mode = asked(plan_question)["metadata"]["settings"]["__mode__"]
assert mode == kept["mode"] == "plan", f"asked in {mode!r}, kept {kept['mode']!r}; expected 'plan'"
calls = [c for m in messages for c in (m.get("metadata") or {}).get("tool_calls", [])]
edits = [c["title"] for c in calls if c.get("kind") == "edit" and c.get("status") == "completed"]
assert not edits, f"plan mode completed edit calls {edits}; expected none"
review = messages.index(asked("/review"))
after = [c for m in messages[review + 1:] for c in (m.get("metadata") or {}).get("tool_calls", [])]
# The first line of opencode's review template (anomalyco/opencode@e3c4eb24 command/template/review.txt).
ran = [c["title"] for c in after if c.get("status") == "completed"
       and "You are a code reviewer" in json.dumps(c.get("raw_input"))]
assert ran, f"no call after /review ran opencode's review; calls after it: {[c.get('title') for c in after]}"
print(f"asked with {model}, then in mode {mode}, no edit completed; /review ran {ran}")
PY
