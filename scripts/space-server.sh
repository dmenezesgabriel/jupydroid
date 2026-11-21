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

# The port jupyter-server-mcp bound, from the runtime file it writes once it listens
# (`jpserver-mcp-<pid>.json`, usr/lib/python3.12/site-packages/jupyter_server_mcp/runtime.py in the
# shipped rootfs). The launcher picks that port per launch, and the MCP server starts about 6 s after
# the server reports running, so this waits. A file left by an earlier run would answer at once with
# a dead port: the caller deletes them before the server starts.
MCP_PORT=""
wait_for_mcp_port() { # wait_for_mcp_port <seconds>
  local deadline=$((SECONDS + $1)) info
  while [ "$SECONDS" -lt "$deadline" ]; do
    info="$("${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cat $SPACE_RUN_DIR/jpserver-mcp-*.json'" 2>/dev/null | tr -d '\r')"
    if [ -n "$info" ]; then
      # shellcheck disable=SC2034  # the caller uses it
      MCP_PORT="$(printf '%s' "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["port"])')"
      return 0
    fi
    sleep 1
  done
  echo "no jpserver-mcp-*.json under $SPACE_RUN_DIR within $1 s - the MCP server never listened" >&2
  return 1
}

# The server's own log, for the evidence directory. Missing is not an error: a server that never
# started has none, and the caller's own check says why.
#
# At DEBUG the server logs its whole config, `IdentityProvider.token` among it, and the token is
# minted per launch - so every pull lands a fresh credential in evidence. The directory the log
# lands in is scrubbed on every pull (check_device_secrets.py's TOKEN_CARRIERS), which is what
# lets the drivers that used to `sed` the token away by hand forget to.
pull_server_log() { # pull_server_log <dest>
  "${DEVICE_ADB[@]}" shell "run-as $APP cat $SPACE_RUN_DIR/jupyter_server.log" >"$1" 2>/dev/null || true
  python3 "$ROOT_DIR/scrub_device_secrets.py" "$(dirname "$1")"
}

# The pid of the proot tracing base's jupyter_server - the one process whose stop freezes the
# whole Space, every tracee blocking at its next ptrace stop until the tracer runs again.
# pylsp's proot is a different tracee command, so it does not match.
proot_pid() {
  "${DEVICE_ADB[@]}" shell "ps -A -o PID,CMDLINE | tr -d '\r' |
    grep 'libproot.so .*-m jupyter_server' | grep -- 'files/spaces/base ' | grep -v grep |
    awk '{print \$1; exit}'"
}

# The Space's processes with their states, for the evidence a freeze and a resume leave:
# the tracer, the server it traces and the kernels under it.
space_processes() {
  {
    "${DEVICE_ADB[@]}" shell "ps -A -o PID,STAT,CMDLINE | tr -d '\r' |
      grep 'libproot.so .*-m jupyter_server' | grep -- 'files/spaces/base ' | grep -v grep"
    "${DEVICE_ADB[@]}" shell "ps -A -o PID,STAT,CMDLINE | tr -d '\r' |
      grep -- '-m jupyter_server ' | grep -v 'libproot.so' | grep -v grep"
    "${DEVICE_ADB[@]}" shell "ps -A -o PID,STAT,CMDLINE | tr -d '\r' |
      grep -- '-m jupydroid_kernel' | grep -v 'libproot.so' | grep -v grep"
  }
}

# Stops base's proot and proves the freeze took: the server reads `t` - a tracee stopped
# inside ptrace waits on its tracer - while the Space still counts as running.
freeze_space() { # freeze_space <evidence-dir>
  local evidence_dir="$1" pid
  pid="$(proot_pid)"
  [ -n "$pid" ] || { echo "no proot tracing base's jupyter_server - the Space never started" >&2; return 1; }
  echo "base's proot pid $pid"
  space_processes | tee "$evidence_dir/ps-before-freeze.txt"
  "${DEVICE_ADB[@]}" shell "run-as $APP kill -STOP $pid" || return 1
  sleep 2
  space_processes | tee "$evidence_dir/ps-frozen.txt"
  # The file, not a second sample: one more `ps` round-trip is one more chance for adb to answer
  # late under a server that is still starting, and the tee'd capture is the state being judged.
  grep -- '-m jupyter_server ' "$evidence_dir/ps-frozen.txt" | grep -v 'libproot.so' | grep -Eq ' [Tt] ' ||
    { echo "jupyter_server did not freeze under its stopped proot" >&2; return 1; }
}

# CONTs the proot freeze_space stopped, so whatever reads the Space next meets the same live
# server - or, when the app died under the freeze, lets the orphaned server finish cleanly.
resume_space() { # resume_space <evidence-dir>
  local evidence_dir="$1" pid
  pid="$(proot_pid)"
  [ -n "$pid" ] || { echo "base's proot exited while frozen - nothing to resume" >&2; return 1; }
  "${DEVICE_ADB[@]}" shell "run-as $APP kill -CONT $pid" || return 1
  sleep 2
  space_processes | tee "$evidence_dir/ps-resumed.txt"
}
