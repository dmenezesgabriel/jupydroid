"""Passes the server's-token ASGI middleware to `FastMCP.http_app` inside `MCPServer`.

`MCPServer._run_http_async_without_signals` builds `self.mcp.http_app(...)` itself
(jupyter-server-mcp 0.4.1 `jupyter_server_mcp/mcp_server.py`), and the package's only middleware
hook, `mcp_middleware`, reaches the MCP message layer - inside the transport, after its session
manager has already minted a session for a tokenless `initialize`. The `middleware=` parameter is
FastMCP's own surface for a refusal in front of that manager, in place since 2.3.2 and unchanged
at PrefectHQ/fastmcp@0f4674e (`fastmcp_slim/fastmcp/server/mixins/transport.py` `http_app`,
`fastmcp_slim/fastmcp/server/http.py` `create_streamable_http_app`).

Each edit must match exactly once, so a release that changes the file fails the image build here,
which is the moment to check whether upstream added the hook this patch supplies - the refusal it
carries is the one https://github.com/jupyter-ai-contrib/jupyter-server-mcp/issues/25 proposes.
"""

import importlib.util
import pathlib
import sys

FUNCTION_START = (
    "    async def _run_http_async_without_signals(self, host: str, port: int) -> None:\n"
    '        """Run FastMCP over HTTP without taking over process signal handlers."""\n'
    '        transport = "http"\n'
)
FUNCTION_START_WITH_IMPORT = FUNCTION_START + "        import jupydroid_mcp_bearer\n"
HTTP_APP_CALL_TAIL = "            allowed_origins=list(self.allowed_origins) or None,\n        )\n"
HTTP_APP_CALL_TAIL_WITH_MIDDLEWARE = (
    "            allowed_origins=list(self.allowed_origins) or None,\n"
    "            middleware=jupydroid_mcp_bearer.server_token_http_middlewares(),\n"
    "        )\n"
)


def mcp_server_path() -> pathlib.Path:
    spec = importlib.util.find_spec("jupyter_server_mcp")
    return pathlib.Path(spec.origin).with_name("mcp_server.py")


def replace_once(source: str, old: str, new: str) -> str:
    count = source.count(old)
    if count != 1:
        sys.exit(f"mcp_server.py holds {count} copies of {old!r}, expected exactly 1")
    return source.replace(old, new)


path = mcp_server_path()
source = replace_once(path.read_text(), FUNCTION_START, FUNCTION_START_WITH_IMPORT)
path.write_text(replace_once(source, HTTP_APP_CALL_TAIL, HTTP_APP_CALL_TAIL_WITH_MIDDLEWARE))
print(f"passed the token middleware to http_app() in {path}")
