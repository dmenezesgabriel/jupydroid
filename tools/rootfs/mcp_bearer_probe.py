"""Proves the MCP port serves `insert_cell` alone, and only to a caller holding the server's token.

Run at image build time, after the strip, against the real `jupyter_server` started with the image's
own config, so what it proves is what the device runs. jupyter-server-mcp@d1823a5 serves MCP on its
own port, "outside Jupyter Server's token/XSRF protection" (`mcp_server.py`), and Android's loopback is
reachable by every app holding INTERNET. `jupydroid_mcp_bearer` closes that port to anyone without the
server's token.

It has to be proved here rather than read off the config: `MCPExtensionApp._add_middleware` logs a
factory that fails to load and goes on serving, so a broken middleware would leave the tools open
rather than fail the server.

The server's whole output is read last, because a refusal is logged with its reason, and a reason
that quoted the header would log the token whenever it was sent without its scheme.
"""

import asyncio
import json
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from typing import TextIO

from fastmcp import Client
from fastmcp.client.transports import StreamableHttpTransport

TOKEN = "mcp-bearer-probe-token"
STARTUP_SECONDS = 180

#: The same `initialize` the device driver sends (scripts/mcp-port-requires-token-driver.sh).
INITIALIZE = json.dumps(
    {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "clientInfo": {"name": "mcp-bearer-probe", "version": "1"},
        },
    }
).encode()
MCP_HEADERS = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}


def free_loopback_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def start_server(mcp_port: int, log: TextIO) -> subprocess.Popen:
    return subprocess.Popen(
        [
            "python3",
            "-m",
            "jupyter_server",
            "--allow-root",
            "--ServerApp.ip=127.0.0.1",
            "--ServerApp.port=0",
            "--ServerApp.root_dir=/tmp",
            f"--IdentityProvider.token={TOKEN}",
            f"--MCPExtensionApp.mcp_port={mcp_port}",
        ],
        stdout=log,
        stderr=subprocess.STDOUT,
    )


def wait_until_listening(server: subprocess.Popen, port: int) -> None:
    deadline = time.monotonic() + STARTUP_SECONDS
    while time.monotonic() < deadline:
        assert server.poll() is None, f"jupyter_server exited with {server.returncode} before MCP listened"
        with socket.socket() as attempt:
            if attempt.connect_ex(("127.0.0.1", port)) == 0:
                return
        time.sleep(1)
    raise AssertionError(f"nothing listened on MCP port {port} within {STARTUP_SECONDS} s")


async def listed_tools(url: str, headers: dict[str, str]) -> list[str]:
    async with Client(StreamableHttpTransport(url, headers=headers)) as client:
        return sorted(tool.name for tool in await client.list_tools())


async def refusal(url: str, headers: dict[str, str]) -> str | None:
    try:
        tools = await listed_tools(url, headers)
    except Exception as refused:  # noqa: BLE001 - any failure to reach the tools is the refusal sought
        return repr(refused)
    raise AssertionError(f"headers {sorted(headers)} reached the tools {tools}, expected a refusal")


def http_request(
    method: str, url: str, headers: dict[str, str], body: bytes | None = None
) -> tuple[int, dict[str, str]]:
    """One raw request to the MCP endpoint: (status, lower-cased headers). Reads no body.

    A `GET` on a live session is an SSE stream that never ends, so the body is never read.

    Example:
        >>> status, headers = http_request("DELETE", url, {"Authorization": "Bearer t"})
    """
    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, {key.lower(): value for key, value in response.headers.items()}
    except urllib.error.HTTPError as refused:
        return refused.code, {key.lower(): value for key, value in refused.headers.items()}


def refused_without_session(method: str, url: str, headers: dict[str, str], body: bytes | None = None) -> None:
    """A tokenless `method` gets 401 and no `mcp-session-id` back.

    Example:
        >>> refused_without_session("GET", url, {"Accept": "text/event-stream"})
    """
    status, answered = http_request(method, url, headers, body)
    assert status == 401 and "mcp-session-id" not in answered, (
        f"a tokenless {method} got HTTP {status} with headers {sorted(answered)}, expected 401 and no session minted"
    )
    assert "www-authenticate" in answered, f"the {method} refusal carried no challenge"


def prove_http_layer(url: str) -> None:
    """The transport itself refuses a caller without the token, so no session is ever minted.

    The message middleware's refusal runs after FastMCP's stateful transport mints the session:
    unpatched, a tokenless `initialize` answers 200 with an `mcp-session-id` that `GET` and
    `DELETE` then serve (measured on the tablet; refused only *inside* the stream).
    `jupydroid_mcp_bearer.BearerTokenHttpMiddleware` runs before the session manager.
    """
    refused_without_session("POST", url, MCP_HEADERS, INITIALIZE)
    for method in ("GET", "DELETE"):
        refused_without_session(method, url, {"Accept": "text/event-stream"})
    bearer = {"Authorization": f"Bearer {TOKEN}"}
    status, answered = http_request("POST", url, MCP_HEADERS | bearer, INITIALIZE)
    session = answered.get("mcp-session-id")
    assert status == 200 and session, f"the token's initialize got HTTP {status}, expected 200 and an mcp-session-id"
    for method in ("GET", "DELETE"):
        refused_without_session(method, url, {"Accept": "text/event-stream", "mcp-session-id": session})
    served = {
        **bearer,
        "Accept": "application/json, text/event-stream",
        "mcp-session-id": session,
        "MCP-Protocol-Version": "2025-11-25",
    }
    for method in ("GET", "DELETE"):
        status, _ = http_request(method, url, served)
        assert status == 200, f"the token's {method} on its session got HTTP {status}, expected 200"
    print("no token mints nothing, on POST, GET or DELETE; the token is served")


async def prove(url: str) -> None:
    print("no header:", await refusal(url, {}))
    print("wrong token:", await refusal(url, {"Authorization": "Bearer not-the-token"}))
    print("token without its scheme:", await refusal(url, {"Authorization": TOKEN}))
    tools = await listed_tools(url, {"Authorization": f"Bearer {TOKEN}"})
    assert tools == ["insert_cell"], f"the token listed {tools}, expected exactly ['insert_cell']"
    print("the server's token lists", tools)
    prove_http_layer(url)


mcp_port = free_loopback_port()
with tempfile.TemporaryFile("w+") as server_log:
    server = start_server(mcp_port, server_log)
    try:
        wait_until_listening(server, mcp_port)
        asyncio.run(prove(f"http://127.0.0.1:{mcp_port}/mcp"))
    finally:
        server.terminate()
        server.wait(timeout=30)
        server_log.seek(0)
        output = server_log.read()
        print(output)
assert TOKEN not in output, "the server's output holds its token, expected every refusal to leave it out"
print("the server's output never holds its token")
