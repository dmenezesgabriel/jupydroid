#!/usr/bin/env bash
# MCP servers on the Chat's list opens `.jupyter/mcp_settings.json` in a tab: written with an empty
# `mcp_servers` list when it is missing, and opened as it is when it is already there.
#
#   nice -n 10 timeout 1800 scripts/chat-mcp-settings-driver.sh
#
# Exits non-zero if either flow fails, if the file part 1 wrote is not exactly an empty list, or if
# the server the driver put in the file is gone after part 2 - then the button wrote over it.
#
# TABLET ONLY (SM-X216B): the Chat lists its chats from a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/chat-mcp-settings"
FLOWS="$ROOT/.maestro/chat-mcp-settings"
SETTINGS=".jupyter/mcp_settings.json"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

read_settings() { "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$SETTINGS\"" | tr -d '\r' >"$1"; }

require_installed_app
reset_notebooks
# reset_notebooks keeps the dot directories, so a file from an earlier run would be opened as it is.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/$SETTINGS\""
rotate_to 1

run_flow "$FLOWS-part1-created.yaml" "$EVIDENCE/created" || fail "part 1 failed"
read_settings "$EVIDENCE/mcp_settings-created.json"
python3 -I -c 'import json, sys; assert json.load(open(sys.argv[1])) == {"mcp_servers": []}' \
  "$EVIDENCE/mcp_settings-created.json" || fail "the file written is not an empty mcp_servers list"

# The edit a person would make, put in place by the driver; seed_file stops the app first.
seed_file "$ROOT/.maestro/fixtures/mcp-settings-one-server.json" "$SETTINGS"

run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
read_settings "$EVIDENCE/mcp_settings-relaunched.json"
cmp -s "$ROOT/.maestro/fixtures/mcp-settings-one-server.json" "$EVIDENCE/mcp_settings-relaunched.json" ||
  fail "MCP servers changed a file that was already there"
