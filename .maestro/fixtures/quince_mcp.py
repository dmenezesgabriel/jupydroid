"""An MCP server with one tool, `marmalade_code`, which answers `marmalade-7349`.

The Chat's MCP demo lists it in `.jupyter/mcp_settings.json` and asks opencode to call it. The code
is in no question the flow types, so an answer holding it came from this process. Stdlib only, since
base ships no MCP package: newline-delimited JSON-RPC 2.0 on stdin and stdout, the MCP stdio
transport (https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#stdio).

    python3 quince_mcp.py
"""

import json
import sys

TOOL = {
    "name": "marmalade_code",
    "description": "Returns the marmalade code.",
    "inputSchema": {"type": "object", "properties": {}},
}
CODE = "marmalade-7349"
METHOD_NOT_FOUND = -32601


def initialized(params: dict[str, object]) -> dict[str, object]:
    """Agrees to the client's own protocol version, which every MCP client accepts back."""
    return {
        "protocolVersion": params["protocolVersion"],
        "capabilities": {"tools": {}},
        "serverInfo": {"name": "quince", "version": "1"},
    }


RESULTS = {
    "initialize": initialized,
    "ping": lambda params: {},
    "tools/list": lambda params: {"tools": [TOOL]},
    "tools/call": lambda params: {"content": [{"type": "text", "text": CODE}], "isError": False},
}


def reply_to(request: dict[str, object]) -> dict[str, object]:
    """The response to one request, e.g. `{"jsonrpc": "2.0", "id": 1, "result": {"tools": [...]}}`."""
    method = str(request["method"])
    if method not in RESULTS:
        error = {"code": METHOD_NOT_FOUND, "message": f"no method {method!r}, expected one of {sorted(RESULTS)}"}
        return {"jsonrpc": "2.0", "id": request["id"], "error": error}
    result = RESULTS[method](request.get("params") or {})
    return {"jsonrpc": "2.0", "id": request["id"], "result": result}


def serve() -> None:
    """Answers each request line until stdin closes; a notification, which has no id, gets nothing."""
    for line in sys.stdin:
        request = json.loads(line)
        if "id" not in request:
            continue
        print(json.dumps(reply_to(request)), flush=True)


if __name__ == "__main__":
    serve()
