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
import socket
import subprocess
import tempfile
import time
from typing import TextIO

from fastmcp import Client
from fastmcp.client.transports import StreamableHttpTransport

TOKEN = "mcp-bearer-probe-token"
STARTUP_SECONDS = 180


def free_loopback_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def start_server(mcp_port: int, log: TextIO) -> subprocess.Popen:
    return subprocess.Popen([
        "python3", "-m", "jupyter_server", "--allow-root", "--ServerApp.ip=127.0.0.1",
        "--ServerApp.port=0", "--ServerApp.root_dir=/tmp", f"--IdentityProvider.token={TOKEN}",
        f"--MCPExtensionApp.mcp_port={mcp_port}",
    ], stdout=log, stderr=subprocess.STDOUT)


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


async def prove(url: str) -> None:
    print("no header:", await refusal(url, {}))
    print("wrong token:", await refusal(url, {"Authorization": "Bearer not-the-token"}))
    print("token without its scheme:", await refusal(url, {"Authorization": TOKEN}))
    tools = await listed_tools(url, {"Authorization": f"Bearer {TOKEN}"})
    assert tools == ["insert_cell"], f"the token listed {tools}, expected exactly ['insert_cell']"
    print("the server's token lists", tools)


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
