#!/usr/bin/env bash
# The demo of the MCP servers a person configures: MCP servers on the Chat's list opens
# `.jupyter/mcp_settings.json` with no servers, the quince fixture server is typed into it, and in a
# new chat opencode calls quince's one tool and answers its code; after a kill the file still holds
# quince, and a new chat still calls it.
#
#   nice -n 10 timeout 2400 scripts/chat-mcp-settings-demo-driver.sh
#
# Run from a fresh install, the app's state wiped first: `fresh_install` (scripts/maestro-run.sh),
# then scripts/jd-046-opencode-in-a-space-driver.sh, then a person signs opencode in from base's
# terminal (`opencode console login`) - their own credential, which no script types.
#
# Exits non-zero if a flow fails; unless the file on disk is exactly the server typed; unless each
# server start logged one server read from the file; and unless each chat holds a completed call of
# quince_marmalade_code that returned marmalade-7349 (scripts/chat_tool_call_reads.py) - opencode ignores a
# server that fails to start, so an answer alone could have come from somewhere else.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
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

EVIDENCE="$ROOT/evidence/chat-mcp-settings-demo"
FLOWS="$ROOT/.maestro/chat-mcp-settings-demo"
READS="$ROOT/scripts/chat_tool_call_reads.py"
SETTINGS=".jupyter/mcp_settings.json"
TYPED='{"mcp_servers":[{"name":"quince","command":"python3","args":["/home/jupydroid/quince_mcp.py"]}]}'
# get_mcp_settings, usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_manager.py
LOADED="MCP servers loaded: 0 builtin, 1 from settings file."

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_workspace_file() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$1\"" | tr -d '\r' >"$2"; }

# Each server start writes a new log, so each part's log is pulled before the next part kills it.
require_loaded() {
  pull_server_log "$1"
  grep -qF "$LOADED" "$1" || fail "$1 does not say '$LOADED'"
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
# ...and so would a settings file from an earlier run, which MCP servers opens as it is.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/$SETTINGS\""
seed_file "$ROOT/.maestro/fixtures/quince_mcp.py" quince_mcp.py
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

run_flow "$FLOWS-part1-configure-and-ask.yaml" "$EVIDENCE/configure-and-ask" || fail "part 1 failed"
read_workspace_file "$SETTINGS" "$EVIDENCE/mcp_settings.json"
python3 -I -c 'import json, sys; assert json.load(open(sys.argv[1])) == json.loads(sys.argv[2])' \
  "$EVIDENCE/mcp_settings.json" "$TYPED" || fail "the file on disk is not the server typed: $TYPED"
require_loaded "$EVIDENCE/jupyter_server.log"
read_workspace_file untitled.chat "$EVIDENCE/untitled.chat"

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
require_loaded "$EVIDENCE/jupyter_server-relaunched.log"
read_workspace_file untitled1.chat "$EVIDENCE/untitled1.chat"

python3 "$READS" called-with "$EVIDENCE/untitled.chat" quince_marmalade_code marmalade-7349
python3 "$READS" called-with "$EVIDENCE/untitled1.chat" quince_marmalade_code marmalade-7349
