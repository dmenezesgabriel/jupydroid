#!/usr/bin/env bash
# The workspace .maestro/site-mcp-shots.yaml shoots: the Chat list's "MCP servers" action opening
# .jupyter/mcp_settings.json, first empty and then holding one server. The file is a dot-path, so
# reset_notebooks' `rm -rf ./*` never reaches it - without this seed the dark pass would photograph
# the light pass's typed server instead of the empty file the page opens on. quince_mcp.py is the
# server the typed configuration names, so the example on screen is one that would run.
# Run by scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_file "$ROOT/.maestro/fixtures/quince_mcp.py" "quince_mcp.py"
"$ADB" shell "run-as $APP rm -f $NOTEBOOK_DIR/.jupyter/mcp_settings.json"
