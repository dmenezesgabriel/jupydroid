#!/usr/bin/env bash
# Each answer says which agent wrote it: opencode answers a question in `untitled`, and "OpenCode" is
# drawn above the answer; after a kill, the chat opened again still draws it there.
#
#   nice -n 10 timeout 1800 scripts/jd-050-answer-names-its-agent-driver.sh
#
# Run on an install where opencode is installed in base, a person signed it in from base's terminal,
# and put their own OpenRouter key in base's `~/.local/share/opencode/auth.json`, as
# scripts/chat-agent-controls-demo-driver.sh says.
#
# Exits non-zero if a flow fails, or unless untitled.chat holds the answer as opencode's persona's and
# its `users` map names that persona OpenCode: the name on screen is the server's, not one the app made.
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

EVIDENCE="$ROOT/evidence/jd-050-claude-agent/t2"
FLOWS="$ROOT/.maestro/jd-050-answer-names-its-agent"
PERSONA="jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
OPENCODE_AUTH="files/spaces/base/home/jupydroid/.local/share/opencode/auth.json"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The provider's name only: the key itself never leaves the device.
"${DEVICE_ADB[@]}" shell "run-as $APP grep -q '\"openrouter\"' $OPENCODE_AUTH" ||
  fail "base holds no OpenRouter key in $OPENCODE_AUTH: a person puts their own there"
reset_notebooks
rotate_to 0

run_flow "$FLOWS-part1-asked.yaml" "$EVIDENCE/asked" || fail "part 1 failed"
run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"

python3 -I - "$EVIDENCE/untitled.chat" "$PERSONA" <<'PY'
import json, re, sys
chat, persona = json.load(open(sys.argv[1])), sys.argv[2]
answers = [m for m in chat["messages"] if re.search(r"\b605\b", m["body"]) and "347+258" not in m["body"]]
assert answers, "no message in untitled.chat holds the answer's 605"
senders = {m["sender"] for m in answers}
assert senders == {persona}, f"the answer was sent by {senders}; expected {persona}"
name = chat["users"][persona]["display_name"]
assert name == "OpenCode", f"the users map names the persona {name!r}; expected 'OpenCode'"
print(f"the answer is {persona}'s, and the chat's users map names it {name}")
PY
