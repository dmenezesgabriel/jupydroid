#!/usr/bin/env bash
# The demo of an agent in the Chat editing the open notebook. x = 1 is typed into untitled.ipynb, and
# the agent's insert_cell puts print(42) above it as the active cell, which runs and prints 42. An
# insert_cell into other.ipynb, which is not open, comes back refused and naming the path, and
# changes neither notebook. After a kill, untitled.ipynb still opens with print(42) first.
#
#   nice -n 10 timeout 2400 scripts/agent-edits-open-notebook-demo-driver.sh
#
# Needs opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh) and signed in by a
# person from base's terminal (`opencode console login`) - their own credential, which no script
# types.
#
# Exits non-zero:
# - if a flow fails;
# - unless the MCP server listened on the port its own runtime file names;
# - unless the chat holds a completed insert_cell call that returned add-cell's "code cell added
#   successfully", and one whose output says other.ipynb is not the open notebook
#   (scripts/chat_tool_call_reads.py);
# - unless untitled.ipynb was saved as print(42) above x = 1, both after the refusal and after the
#   relaunch, and other.ipynb still holds only y = 2 (scripts/notebook_cell_reads.py).
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

EVIDENCE="$ROOT/evidence/agent-edits-open-notebook-demo"
FLOWS="$ROOT/.maestro/agent-edits-open-notebook-demo"
FIXTURES="$ROOT/.maestro/fixtures/agent-edits-open-notebook-demo"
READS="$ROOT/scripts/chat_tool_call_reads.py"
CELLS="$ROOT/scripts/notebook_cell_reads.py"
# registerAddCellCommand's message, jupyterlab-ai-commands@2743d32 src/notebook-commands.ts, which
# extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/NotebookLabCommands.kt answers with.
ADDED="code cell added successfully"
# NotebookLabCommands.kt's refusal of a path that is not the open notebook.
REFUSED="other.ipynb is not the open notebook"
# opencode titles an MCP tool's call `<server>_<tool>`.
INSERT_CELL_TITLE="Jupyter_MCP_Server_insert_cell"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

read_workspace_file() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$1\"" | tr -d '\r' >"$2"; }

require_agents_cell_first() { # require_agents_cell_first <saved untitled.ipynb>
  python3 "$CELLS" "$1" 'code:print(42)' 'code:x = 1' ||
    fail "$1 is not a cell of exactly print(42) above one of x = 1"
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
seed_notebook "$FIXTURES.ipynb" untitled
seed_notebook "$FIXTURES-other.ipynb" other
# A settings file would add its servers beside the built-in one.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/.jupyter/mcp_settings.json\""
"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'rm -f $SPACE_RUN_DIR/jupyter_server.log $SPACE_RUN_DIR/jpserver-mcp-*.json'"
rotate_to 1

run_flow "$FLOWS-part1-type.yaml" "$EVIDENCE/type" || fail "part 1 failed"
wait_for_mcp_port 60 || fail "base's MCP server never listened"
echo "MCP server on device port $MCP_PORT"

run_flow "$FLOWS-part2-ask-and-run.yaml" "$EVIDENCE/ask-and-run" || fail "part 2 failed"
run_flow "$FLOWS-part3-refused.yaml" "$EVIDENCE/refused" || fail "part 3 failed"

# The server's log is pulled before part 4's relaunch starts a new one.
open_forward || fail "base's server wrote no connection file"
pull_server_log "$EVIDENCE/jupyter_server.log"
grep -qF "MCP server started on port $MCP_PORT" "$EVIDENCE/jupyter_server.log" ||
  fail "the log does not say the MCP server started on $MCP_PORT"

read_workspace_file untitled.chat "$EVIDENCE/untitled.chat"
python3 "$READS" called-with "$EVIDENCE/untitled.chat" "$INSERT_CELL_TITLE" "$ADDED"
python3 "$READS" refused-with "$EVIDENCE/untitled.chat" "$INSERT_CELL_TITLE" "$REFUSED"

read_workspace_file untitled.ipynb "$EVIDENCE/untitled.ipynb"
require_agents_cell_first "$EVIDENCE/untitled.ipynb"
read_workspace_file other.ipynb "$EVIDENCE/other.ipynb"
python3 "$CELLS" "$EVIDENCE/other.ipynb" 'code:y = 2' || fail "other.ipynb changed: it should hold only y = 2"

run_flow "$FLOWS-part4-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 4 failed"
read_workspace_file untitled.ipynb "$EVIDENCE/untitled-relaunched.ipynb"
require_agents_cell_first "$EVIDENCE/untitled-relaunched.ipynb"
