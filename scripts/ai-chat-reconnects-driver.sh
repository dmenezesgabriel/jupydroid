#!/usr/bin/env bash
# The Chat panel opens its chat again after base's server stops under it, with no relaunch: it says
# it is reconnecting, the server starts afresh, and a question sent after that is echoed back.
#
#   nice -n 10 timeout 900 scripts/ai-chat-reconnects-driver.sh
#
# The flow asks a first question; this driver waits for that question in the server's log, as the
# chat's `send`, then stops the server with `POST /api/shutdown`, the `ServerApp.stop()` its idle
# shutdown also ends in (jupyter_server 2.20.0 `serverapp.py`). Exits non-zero if the flow fails,
# if the server after the flow is the one that was stopped (same pid in its connection file), or if
# logcat holds no `chat.socket.failed` line.
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

EVIDENCE="$ROOT/evidence/ai-chat-reconnects"
CHAT_OPENED="101 GET /api/chat/ws/untitled.chat"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

server_pid() {
  server_info | python3 -c 'import json,sys; print(json.load(sys.stdin)["pid"])' 2>/dev/null || true
}

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

# The server writes the chat file once it has the question, so the question in the file is the
# question in the chat, and in the server's memory before it is stopped.
wait_for_question() {
  local tries
  for tries in $(seq 1 60); do
    "${DEVICE_ADB[@]}" shell "run-as $APP grep -l 'Before the drop' $NOTEBOOK_DIR/untitled.chat" \
      >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "the first question never reached untitled.chat after $tries s" >&2
  return 1
}

stop_server() {
  open_forward || return 1
  curl -sS -X POST -H "Authorization: token $TOKEN" "http://127.0.0.1:$FORWARDED_PORT/api/shutdown" -o /dev/null \
    -w "POST /api/shutdown -> %{http_code}\n"
  close_forward
}

require_installed_app
mkdir -p "$EVIDENCE"
# A previous server's log could already hold the 101; the flow's launch starts base afresh.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

status=0
run_flow "$ROOT/.maestro/ai-chat-reconnects.yaml" "$EVIDENCE" &
flow_pid=$!
stopped_pid=""
if wait_for_chat_socket && wait_for_question; then
  stopped_pid="$(server_pid)"
  stop_server || echo "the server was not stopped" >&2
fi
wait "$flow_pid" || status=$?
pull_server_log "$EVIDENCE/jupyter_server.log"
reopened_pid="$(server_pid)"
echo "server pid stopped: ${stopped_pid:-none}, after the flow: ${reopened_pid:-none}" | tee "$EVIDENCE/server-pids.txt"

[ "$status" -eq 0 ] || fail "the flow failed ($status)"
[ -n "$stopped_pid" ] && [ -n "$reopened_pid" ] && [ "$stopped_pid" != "$reopened_pid" ] ||
  fail "the server after the flow is not a new one"
grep -q "$CHAT_OPENED" "$EVIDENCE/jupyter_server.log" || fail "the new server's log has no '$CHAT_OPENED'"
grep -h "JupydroidChat" "$EVIDENCE/logcat.txt" | tee "$EVIDENCE/chat-log-lines.txt" |
  grep -q '"stage":"chat.socket.failed"' || fail "logcat holds no chat.socket.failed line"
