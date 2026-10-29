#!/usr/bin/env bash
# The agent's MCP port, asked on the device: jupyter-server-mcp answers inside base, and a caller
# without the server's own token is refused at the HTTP layer - before FastMCP's transport can mint
# it a session - while one holding it is listed `insert_cell` and nothing else
# (tools/rootfs/jupydroid_mcp_bearer.py, tools/rootfs/mcp_http_bearer_patch.py,
# tools/rootfs/jupyter_server_config.json).
#
# The launcher picks the port per launch (`mcpServerArgs`,
# app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt), so it is read from the
# MCP server's own runtime file (`wait_for_mcp_port`, scripts/space-server.sh).
#
# **A measurement first and a gate second.** Every answer is kept verbatim in `answers.txt`, and the
# server's log beside it. The gate is every answer: a tokenless POST, GET or DELETE - even one
# carrying a live session - is refused 401 with a WWW-Authenticate challenge and no mcp-session-id
# back, and the server's token initializes, lists exactly ['insert_cell'], and has its GET stream
# and DELETE served. The app is then killed and relaunched, and `relaunched/` asks the same calls
# of the fresh token and expects the same answers.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator no Space runs and no server starts.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/mcp-port-requires-token"
FLOW="$ROOT/.maestro/mcp-port-requires-token.yaml"

mapfile -t DEVICE_ADB < <(device_adb)

mkdir -p "$EVIDENCE"
# A failed run must not leave the last passing run's answers looking fresh.
rm -f "$EVIDENCE/answers.txt" "$EVIDENCE/jupyter_server.log" \
  "$EVIDENCE/relaunched/answers.txt" "$EVIDENCE/relaunched/jupyter_server.log"

MCP_FORWARDED_PORT=""
close_mcp_forward() {
  [ -n "$MCP_FORWARDED_PORT" ] &&
    "${DEVICE_ADB[@]}" forward --remove "tcp:$MCP_FORWARDED_PORT" >/dev/null 2>&1 || true
  MCP_FORWARDED_PORT=""
  close_forward
}
trap close_mcp_forward EXIT

# Streamable HTTP by hand (MCP 2025-11-25 `basic/transports`): the tokenless arms are raw requests,
# since a refusal never reaches a JSON-RPC answer; with the token, an `initialize` POST and then
# `notifications/initialized` and `tools/list` on the session it returned, and the GET stream and
# DELETE the session serves. An answer is JSON or an SSE stream whose `data:` lines carry it -
# except a GET stream, which never ends and whose body is never read.
probe_answers() {
  python3 -I - "http://127.0.0.1:$MCP_FORWARDED_PORT/mcp" "$TOKEN" "$1/answers.txt" <<'EOF'
import json, sys, urllib.error, urllib.request

url, token, out = sys.argv[1], sys.argv[2], open(sys.argv[3], "w")
INITIALIZE = {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
    "protocolVersion": "2025-11-25", "capabilities": {},
    "clientInfo": {"name": "mcp-port-requires-token-driver", "version": "1"}}}
INITIALIZE_BODY = json.dumps(INITIALIZE).encode()
POST_HEADERS = {"Content-Type": "application/json"}
STREAM_HEADERS = {"Accept": "text/event-stream"}

def post(message, headers):
    request = urllib.request.Request(url, json.dumps(message).encode(), method="POST", headers={
        "Content-Type": "application/json", "Accept": "application/json, text/event-stream", **headers})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body, status, session = response.read().decode(), response.status, response.headers.get("mcp-session-id")
    except urllib.error.HTTPError as refused:
        body, status, session = refused.read().decode(), refused.code, None
    out.write(f"POST {message.get('method')} {sorted(headers)} -> HTTP {status}\n{body}\n\n")
    data = [line[5:] for line in body.splitlines() if line.startswith("data:")]
    answer = json.loads(data[-1] if data else body) if body.strip() else {}
    return answer, session

def http_request(method, headers, body=None):
    req = urllib.request.Request(url, body, method=method, headers={
        "Accept": "application/json, text/event-stream", **headers})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status, answered = response.status, response.headers
            payload = "" if method == "GET" else response.read().decode()
    except urllib.error.HTTPError as refused:
        status, answered, payload = refused.code, refused.headers, refused.read().decode()
    out.write(f"{method} {sorted(headers)} -> HTTP {status}\n{payload}\n\n")
    return status, {key.lower(): value for key, value in answered.items()}

def refused_without_session(method, headers, label, body=None):
    status, answered = http_request(method, headers, body)
    assert status == 401 and "mcp-session-id" not in answered, (
        f"{label}: a tokenless {method} got HTTP {status} with headers {sorted(answered)}, "
        "expected 401 and no session minted")
    assert "www-authenticate" in answered, f"{label}: the {method} refusal carried no challenge"
    print(f"{label}: {method} refused 401")

refused_without_session("POST", POST_HEADERS, "no header", INITIALIZE_BODY)
refused_without_session("POST", {**POST_HEADERS, "Authorization": "Bearer not-the-token"},
                        "wrong token", INITIALIZE_BODY)
refused_without_session("POST", {**POST_HEADERS, "Authorization": token},
                        "token without its scheme", INITIALIZE_BODY)
refused_without_session("GET", STREAM_HEADERS, "no header")
refused_without_session("DELETE", STREAM_HEADERS, "no header")

bearer = {"Authorization": f"Bearer {token}"}
answer, session = post(INITIALIZE, bearer)
assert "result" in answer and session, f"the server's token did not initialize a session: {answer}"

live = {**STREAM_HEADERS, "mcp-session-id": session}
refused_without_session("GET", live, "no header on a live session")
refused_without_session("DELETE", live, "no header on a live session")

with_session = {**bearer, "mcp-session-id": session, "MCP-Protocol-Version": answer["result"]["protocolVersion"]}
post({"jsonrpc": "2.0", "method": "notifications/initialized"}, with_session)
listed, _ = post({"jsonrpc": "2.0", "id": 2, "method": "tools/list"}, with_session)
tools = sorted(tool["name"] for tool in listed["result"]["tools"])
assert tools == ["insert_cell"], f"the server's token listed {tools}, expected exactly ['insert_cell']"

status, _ = http_request("GET", with_session)
assert status == 200, f"the token's GET on its session got HTTP {status}, expected 200"
status, _ = http_request("DELETE", with_session)
assert status == 200, f"the token's DELETE on its session got HTTP {status}, expected 200"
print(f"the server's token: session {session[:8]}..., GET stream and DELETE served, tools {tools}")
EOF
}

# One launch end to end: the stale MCP runtime file from a killed server would answer
# `wait_for_mcp_port` at once with a dead port, so it goes first; the flow then relaunches the app,
# which stops every Space process, and the fresh server's own jpserver-<pid>.json holds the fresh
# token `open_forward` reads.
launch_and_probe() {
  local out="$1" status=0
  "${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'rm -f $SPACE_RUN_DIR/jpserver-mcp-*.json'"
  run_flow "$FLOW" "$out" || return 1
  open_forward || return 1
  wait_for_mcp_port 60 || return 1
  MCP_FORWARDED_PORT="$("${DEVICE_ADB[@]}" forward tcp:0 "tcp:$MCP_PORT" | tr -d '\r')"
  echo "MCP on device port $MCP_PORT, forwarded to 127.0.0.1:$MCP_FORWARDED_PORT"
  probe_answers "$out" || status=$?
  # Pulled after the probes, so the log holds the refusals' access lines, not just the startup.
  pull_server_log "$out/jupyter_server.log"
  close_mcp_forward
  return "$status"
}

require_installed_app

launch_and_probe "$EVIDENCE" || exit 1

# Killed and relaunched, the server mints a fresh token - read back through `open_forward` from the
# new runtime file - and the same calls must get the same answers.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
launch_and_probe "$EVIDENCE/relaunched" || exit 1
