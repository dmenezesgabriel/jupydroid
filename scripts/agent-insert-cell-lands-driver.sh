#!/usr/bin/env bash
# The agent's insert_cell lands in the open notebook. opencode calls insert_cell. The commands
# toolkit then emits get-notebook-info and add-cell on the server's event bus, and the app's events
# socket acks each one, runs it over the open notebook and posts its result. The new cell is saved
# as the notebook's first cell.
#
#   nice -n 10 timeout 2400 scripts/agent-insert-cell-lands-driver.sh
#
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh) and signed in by a
# person from base's terminal (`opencode console login`) - their own credential, which no script
# types.
#
# Exits non-zero:
# - if a flow fails;
# - unless the MCP server listened on the port its own runtime file names;
# - unless the chat holds a completed insert_cell call that returned add-cell's "code cell added
#   successfully" (scripts/chat_tool_call_reads.py);
# - unless untitled.ipynb was saved with print(42) above x = 1 (scripts/notebook_cell_reads.py).
# The screen alone cannot settle the last two, because the question shows print(42) as well.
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

EVIDENCE="$ROOT/evidence/agent-insert-cell-lands"
FLOWS="$ROOT/.maestro/agent-insert-cell-lands"
# registerAddCellCommand's message, jupyterlab-ai-commands@2743d32 src/notebook-commands.ts, which
# extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/NotebookLabCommands.kt answers with.
ADDED="code cell added successfully"
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
seed_notebook "$ROOT/.maestro/fixtures/agent-insert-cell-lands.ipynb" untitled
# A settings file would add its servers beside the built-in one.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/.jupyter/mcp_settings.json\""
"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'rm -f $SPACE_RUN_DIR/jupyter_server.log $SPACE_RUN_DIR/jpserver-mcp-*.json'"
rotate_to 1

run_flow "$FLOWS-part1-open.yaml" "$EVIDENCE/open" || fail "part 1 failed"
wait_for_mcp_port 60 || fail "base's MCP server never listened"
echo "MCP server on device port $MCP_PORT"

run_flow "$FLOWS-part2-ask.yaml" "$EVIDENCE/ask" || fail "part 2 failed"

open_forward || fail "base's server wrote no connection file"
pull_server_log "$EVIDENCE/jupyter_server.log"
grep -qF "MCP server started on port $MCP_PORT" "$EVIDENCE/jupyter_server.log" ||
  fail "the log does not say the MCP server started on $MCP_PORT"

"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"
python3 "$ROOT/scripts/chat_tool_call_reads.py" called-with "$EVIDENCE/untitled.chat" "$INSERT_CELL_TITLE" "$ADDED"

wait_for_workspace_text untitled.ipynb "print(42)" 60
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.ipynb\"" | tr -d '\r' >"$EVIDENCE/untitled.ipynb"
python3 "$ROOT/scripts/notebook_cell_reads.py" "$EVIDENCE/untitled.ipynb" 'code:print(42)' 'code:x = 1' ||
  fail "untitled.ipynb is not a cell of exactly print(42) above one of x = 1"
