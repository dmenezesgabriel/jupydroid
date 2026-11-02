#!/usr/bin/env bash
# The Chat panel says what failed when base's server stops under an open chat, and logs it as one
# JSON line under the JupydroidChat tag.
#
#   nice -n 10 timeout 900 scripts/ai-chat-socket-failure-driver.sh
#
# The flow opens Chat and waits; this driver waits for the socket's `101` in the server's log and
# then stops the server with `POST /api/shutdown`, the `ServerApp.stop()` its idle shutdown also
# ends in (jupyter_server 2.20.0 `serverapp.py`). Exits non-zero if the flow fails or logcat holds
# no `chat.socket.*` line. No opencode needed: no question is asked.
#
# TABLET ONLY (SM-X216B): only arm64 ships a Space with a server to stop.
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

EVIDENCE="$ROOT/evidence/ai-chat-socket-failure"
CHAT_OPENED="101 GET /api/chat/ws/untitled.chat"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

# Polls base's server log, which a new server starts afresh, until the panel's socket is upgraded.
wait_for_chat_socket() {
  local tries
  for tries in $(seq 1 120); do
    "${DEVICE_ADB[@]}" shell "run-as $APP cat $SPACE_RUN_DIR/jupyter_server.log" 2>/dev/null |
      grep -q "$CHAT_OPENED" && return 0
    sleep 1
  done
  echo "no '$CHAT_OPENED' in the server log after $tries s" >&2
  return 1
}

stop_server() {
  open_forward || return 1
  curl -sS -X POST -H "Authorization: token $TOKEN" "http://127.0.0.1:$FORWARDED_PORT/api/shutdown" -o /dev/null \
    -w "POST /api/shutdown -> %{http_code}\n"
}

require_installed_app
mkdir -p "$EVIDENCE"
# A previous server's log could already hold the 101; the flow's launch starts base afresh.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

status=0
run_flow "$ROOT/.maestro/ai-chat-socket-failure.yaml" "$EVIDENCE" &
flow_pid=$!
wait_for_chat_socket && stop_server ||
  echo "the server was not stopped" >&2
wait "$flow_pid" || status=$?
pull_server_log "$EVIDENCE/jupyter_server.log"

[ "$status" -eq 0 ] || fail "the flow failed ($status)"
grep -h "JupydroidChat" "$EVIDENCE/logcat.txt" | tee "$EVIDENCE/chat-log-lines.txt" | grep -q '"stage":"chat.socket.' ||
  fail "logcat holds no chat.socket.* line"
