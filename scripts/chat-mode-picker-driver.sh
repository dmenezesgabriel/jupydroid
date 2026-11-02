#!/usr/bin/env bash
# The mode chip under the Chat's input, at one pane: in a new chat it names opencode's mode (`build`)
# before anything is asked, `plan` is picked from its list and a question asked with it; after a kill,
# the chat opened again still names `plan`.
#
#   nice -n 10 timeout 2400 scripts/chat-mode-picker-driver.sh
#
# Run on an install where opencode is installed in base and a person signed it in from base's
# terminal (`opencode console login`) - their own credential, which no script types.
#
# Exits non-zero if a flow fails, and unless untitled.chat shows the pick reached the persona: the
# question carries `metadata.settings.__mode__`, and the chat's `acp_config_options` keeps that same
# value for opencode's `mode` option, which `_record_config_choice` writes only once the agent switched
# (usr/lib/python3.12/site-packages/jupyter_ai_acp_client/base_acp_persona.py in the shipped rootfs:
# `update_settings` routes `__mode__` to the mode config option opencode advertises).
#
# After part 1 the toolbar row is dumped to toolbar.txt: the chips and Send with their bounds.
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

EVIDENCE="$ROOT/evidence/chat-mode-picker"
FLOWS="$ROOT/.maestro/chat-mode-picker"
QUESTION="Reply with only the word ready."
PERSONA="jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_workspace_file() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$1\"" | tr -d '\r' >"$2"; }

# The input box's toolbar row, as `uiautomator dump` has it: each node named plan, build, Send or a
# model (`Default / …`), with its bounds.
dump_toolbar() {
  "${DEVICE_ADB[@]}" shell uiautomator dump /sdcard/chat-mode-picker.xml >/dev/null
  "${DEVICE_ADB[@]}" shell cat /sdcard/chat-mode-picker.xml >"$EVIDENCE/hierarchy.xml"
  "${DEVICE_ADB[@]}" shell rm -f /sdcard/chat-mode-picker.xml
  # A node carries both a text and a content-desc, so each line keeps the one that matched and the bounds.
  { grep -oE '(text|content-desc)="(plan|build|Send|Default / [^"]*)"[^>]*bounds="[^"]*"' \
    "$EVIDENCE/hierarchy.xml" || true; } | sed -E 's/^[a-z-]+="([^"]*)".*bounds="([^"]*)"$/\1\t\2/' \
    >"$EVIDENCE/toolbar.txt"
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 0

run_flow "$FLOWS-part1-pick-and-ask.yaml" "$EVIDENCE/pick-and-ask" || fail "part 1 failed"
dump_toolbar
read_workspace_file untitled.chat "$EVIDENCE/untitled.chat"
pull_server_log "$EVIDENCE/jupyter_server.log"

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"

python3 -I - "$EVIDENCE/untitled.chat" "$QUESTION" "$PERSONA" <<'PY'
import json, sys
chat, question, persona = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
asked = [m for m in chat["messages"] if m["body"] == question]
assert len(asked) == 1, f"expected the one question {question!r}, found {len(asked)}"
picked = asked[0]["metadata"]["settings"]["__mode__"]
kept = chat["metadata"]["acp_config_options"][persona]["mode"]
assert picked == kept == "plan", f"the question picked {picked!r}, the persona kept {kept!r}; expected 'plan'"
print(f"asked with __mode__ {picked}, and the persona kept mode {kept}")
PY
