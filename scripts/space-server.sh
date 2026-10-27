#!/usr/bin/env bash
# Sourced by the drivers that talk to a running Space's `jupyter_server` from the host. Not
# executable on its own. Source it after `maestro-run.sh`, and fill DEVICE_ADB first:
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/space-server.sh"
#   mapfile -t DEVICE_ADB < <(device_adb)
#   open_forward || exit 1
#   curl -H "Authorization: token $TOKEN" "http://127.0.0.1:$FORWARDED_PORT/api/status"
#
# The forward is removed on exit by the trap this file sets.

# Where the server writes its connection file and its log, inside base.
SPACE_RUN_DIR="files/spaces/base/run"

# The port and token the running server wrote into its own connection file; both are per launch.
# `jpserver-<pid>.json` only: jupyter-server-mcp writes `jpserver-mcp-<pid>.json` beside it
# (jupyter-server-mcp@d1823a5 `jupyter_server_mcp/runtime.py`), as `JupyterRuntimeFile` says.
server_info() {
  "${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cat $SPACE_RUN_DIR/jpserver-[0-9]*.json'" 2>/dev/null | tr -d '\r'
}

FORWARDED_PORT=""
TOKEN=""
open_forward() {
  local info port
  info="$(server_info)"
  [ -n "$info" ] || { echo "no jpserver-*.json under $SPACE_RUN_DIR - the server never started" >&2; return 1; }
  port="$(printf '%s' "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["port"])')"
  # shellcheck disable=SC2034  # the caller uses it
  TOKEN="$(printf '%s' "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"
  FORWARDED_PORT="$("${DEVICE_ADB[@]}" forward tcp:0 "tcp:$port" | tr -d '\r')"
  echo "server on device port $port, forwarded to 127.0.0.1:$FORWARDED_PORT"
}

close_forward() {
  [ -n "$FORWARDED_PORT" ] || return 0
  "${DEVICE_ADB[@]}" forward --remove "tcp:$FORWARDED_PORT" >/dev/null 2>&1 || true
  FORWARDED_PORT=""
}
trap close_forward EXIT

# The server's own log, for the evidence directory. Missing is not an error: a server that never
# started has none, and the caller's own check says why.
pull_server_log() { # pull_server_log <dest>
  "${DEVICE_ADB[@]}" shell "run-as $APP cat $SPACE_RUN_DIR/jupyter_server.log" >"$1" 2>/dev/null || true
}
