#!/usr/bin/env bash
# The agent's MCP port, asked on the device: jupyter-server-mcp answers inside base, refuses a caller
# without the server's own token, and lists `insert_cell` and nothing else to one holding it
# (tools/rootfs/jupydroid_mcp_bearer.py, tools/rootfs/jupyter_server_config.json).
#
# Until the launcher picks the port per launch, the server listens on jupyter-server-mcp's default,
# 3001 (`MCPExtensionApp.mcp_port`).
#
# **A measurement first and a gate second.** Every answer is kept verbatim in `answers.txt`, and the
# server's log beside it. The gate is the four answers: no header, a wrong token and a token without
# its scheme are each refused, and the server's token lists exactly ['insert_cell'].
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
MCP_DEVICE_PORT=3001

mapfile -t DEVICE_ADB < <(device_adb)

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/answers.txt" "$EVIDENCE/jupyter_server.log"

require_installed_app

run_flow "$ROOT/.maestro/mcp-port-requires-token.yaml" "$EVIDENCE" || exit 1
open_forward || exit 1

MCP_FORWARDED_PORT="$("${DEVICE_ADB[@]}" forward tcp:0 "tcp:$MCP_DEVICE_PORT" | tr -d '\r')"
close_mcp_forward() {
  "${DEVICE_ADB[@]}" forward --remove "tcp:$MCP_FORWARDED_PORT" >/dev/null 2>&1 || true
  close_forward
}
trap close_mcp_forward EXIT
echo "MCP on device port $MCP_DEVICE_PORT, forwarded to 127.0.0.1:$MCP_FORWARDED_PORT"

pull_server_log "$EVIDENCE/jupyter_server.log"
# At DEBUG the server logs its whole config, `IdentityProvider.token` included; the evidence keeps
# the log and not the token.
sed -i "s/$TOKEN/<token>/g" "$EVIDENCE/jupyter_server.log"

# Streamable HTTP by hand (MCP 2025-11-25 `basic/transports`): an `initialize` POST, and with the
# token, `notifications/initialized` and `tools/list` on the session it returned. An answer is JSON
# or an SSE stream whose `data:` lines carry it.
python3 -I - "http://127.0.0.1:$MCP_FORWARDED_PORT/mcp" "$TOKEN" "$EVIDENCE/answers.txt" <<'EOF'
import json, sys, urllib.error, urllib.request

url, token, out = sys.argv[1], sys.argv[2], open(sys.argv[3], "w")
INITIALIZE = {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
    "protocolVersion": "2025-11-25", "capabilities": {},
    "clientInfo": {"name": "mcp-port-requires-token-driver", "version": "1"}}}

def post(message, headers):
    request = urllib.request.Request(url, json.dumps(message).encode(), method="POST", headers={
        "Content-Type": "application/json", "Accept": "application/json, text/event-stream", **headers})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body, status, session = response.read().decode(), response.status, response.headers.get("mcp-session-id")
    except urllib.error.HTTPError as refused:
        body, status, session = refused.read().decode(), refused.code, None
    out.write(f"{message.get('method')} {sorted(headers)} -> HTTP {status}\n{body}\n\n")
    data = [line[5:] for line in body.splitlines() if line.startswith("data:")]
    answer = json.loads(data[-1] if data else body) if body.strip() else {}
    return answer, session

def refused(headers, label):
    answer, _ = post(INITIALIZE, headers)
    assert "result" not in answer, f"{label} initialized a session, expected a refusal: {answer}"
    print(f"{label}: refused, {json.dumps(answer)[:300]}")

refused({}, "no header")
refused({"Authorization": "Bearer not-the-token"}, "wrong token")
refused({"Authorization": token}, "token without its scheme")

bearer = {"Authorization": f"Bearer {token}"}
answer, session = post(INITIALIZE, bearer)
assert "result" in answer and session, f"the server's token did not initialize a session: {answer}"
with_session = {**bearer, "mcp-session-id": session, "MCP-Protocol-Version": answer["result"]["protocolVersion"]}
post({"jsonrpc": "2.0", "method": "notifications/initialized"}, with_session)
listed, _ = post({"jsonrpc": "2.0", "id": 2, "method": "tools/list"}, with_session)
tools = sorted(tool["name"] for tool in listed["result"]["tools"])
assert tools == ["insert_cell"], f"the server's token listed {tools}, expected exactly ['insert_cell']"
print(f"the server's token: session {session[:8]}..., tools {tools}")
EOF
