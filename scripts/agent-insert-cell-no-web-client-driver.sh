#!/usr/bin/env bash
# The agent reaches base's MCP port: a new chat's session is handed the built-in server with the
# port the launcher picked and the server's token, opencode calls `insert_cell`, and the call gets
# as far as the commands toolkit, whose command no web client acknowledges: the app's events socket
# subscribes, so the toolkit emits, but nothing in the app answers a command yet.
#
#   nice -n 10 timeout 2400 scripts/agent-insert-cell-no-web-client-driver.sh
#
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh) and signed in by a
# person from base's terminal (`opencode console login`) - their own credential, which no script
# types.
#
# Exits non-zero if a flow fails; unless the MCP server listened on the port its own runtime file
# names; unless the session was handed one built-in server and none from a settings file; and unless
# the chat holds a completed insert_cell call that returned the toolkit's "No JupyterLab web client
# acknowledged the command" (scripts/chat_tool_call_reads.py). The middleware refuses a caller without the
# token (tools/rootfs/jupydroid_mcp_bearer.py), so only an authorized call gets that answer.
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

EVIDENCE="$ROOT/evidence/agent-insert-cell-no-web-client"
FLOWS="$ROOT/.maestro/agent-insert-cell-no-web-client"
READS="$ROOT/scripts/chat_tool_call_reads.py"
# get_mcp_settings, usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_manager.py
LOADED="MCP servers loaded: 1 builtin, 0 from settings file."
# usr/lib/python3.12/site-packages/jupyterlab_commands_toolkit/tools.py, `not_acknowledged`
NOT_ACKNOWLEDGED="No JupyterLab web client acknowledged the command"
# opencode titles an MCP tool's call `<server>_<tool>`.
INSERT_CELL_TITLE="Jupyter_MCP_Server_insert_cell"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
# A settings file would add its servers beside the built-in one.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/.jupyter/mcp_settings.json\""
"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'rm -f $SPACE_RUN_DIR/jupyter_server.log $SPACE_RUN_DIR/jpserver-mcp-*.json'"
rotate_to 1

run_flow "$FLOWS-part1-enter.yaml" "$EVIDENCE/enter" || fail "part 1 failed"
wait_for_mcp_port 60 || fail "base's MCP server never listened"
echo "MCP server on device port $MCP_PORT"

run_flow "$FLOWS-part2-ask.yaml" "$EVIDENCE/ask" || fail "part 2 failed"

open_forward || fail "base's server wrote no connection file"
pull_server_log "$EVIDENCE/jupyter_server.log"
# At DEBUG the server logs its whole config, the token among it; the evidence keeps the log, not it.
sed -i "s/$TOKEN/<token>/g" "$EVIDENCE/jupyter_server.log"
grep -qF "MCP server started on port $MCP_PORT" "$EVIDENCE/jupyter_server.log" ||
  fail "the log does not say the MCP server started on $MCP_PORT"
grep -qF "$LOADED" "$EVIDENCE/jupyter_server.log" || fail "the log does not say '$LOADED'"

"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
python3 "$READS" called-with "$EVIDENCE/untitled.chat" "$INSERT_CELL_TITLE" "$NOT_ACKNOWLEDGED"
