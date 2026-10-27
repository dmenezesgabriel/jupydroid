"""MCP middleware that serves only a caller holding the Jupyter server's own token.

jupyter-server-mcp@d1823a5 serves its tools on a port of its own, "outside Jupyter Server's
token/XSRF protection" (`jupyter_server_mcp/mcp_server.py`, `host_origin_protection`). Its Host and
Origin checks stop a browser, but Android's loopback is shared by every app that holds INTERNET, so
any of them could call the tools. MCP 2025-11-25 says a local HTTP server should "require an
authorization token" (https://modelcontextprotocol.io/specification/2025-11-25/basic/security_best_practices,
Local MCP Server Compromise). The token is the server's own, sent as `Authorization: Bearer`, as
https://github.com/jupyter-ai-contrib/jupyter-server-mcp/issues/25 proposes. So the agent needs no
second secret, and nobody without the first reaches anything.

It is loaded through the package's own hook, `MCPExtensionApp.mcp_middleware`
(`jupyter_server_mcp/extension.py`), because `MCPServer` builds `FastMCP(self.name)` itself and leaves
FastMCP's `auth=` out of reach. That hook logs a factory that fails and goes on serving, so the image
build proves the refusal end to end (`mcp_bearer_probe.py`).

    {"MCPExtensionApp": {"mcp_middleware": ["jupydroid_mcp_bearer:server_token_bearer"]}}
"""

import hmac
from collections.abc import Callable
from typing import Any

from fastmcp.exceptions import AuthorizationError
from fastmcp.server.dependencies import get_http_request
from fastmcp.server.middleware import CallNext, Middleware, MiddlewareContext
from jupyter_server.serverapp import ServerApp

EXPECTED_SHAPE = "an `Authorization: Bearer <the Jupyter server's token>` header"


class BearerTokenMiddleware(Middleware):
    """Refuses every MCP message, `initialize` included, whose HTTP request lacks the bearer token.

    Example:
        >>> server.add_middleware(BearerTokenMiddleware(lambda: "s3cret"))
    """

    def __init__(self, expected_token: Callable[[], str]) -> None:
        self._expected_token = expected_token

    async def on_message(self, context: MiddlewareContext[Any], call_next: CallNext[Any, Any]) -> Any:
        refusal = self._refusal()
        if refusal is not None:
            raise AuthorizationError(f"{refusal}; expected {EXPECTED_SHAPE}")
        return await call_next(context)

    def _refusal(self) -> str | None:
        expected = self._expected_token()
        if not expected:
            return "the server has no token, so no caller can prove it holds one"
        try:
            header = get_http_request().headers.get("authorization")
        except RuntimeError:
            return "the message arrived without an HTTP request"
        return refusal_of_header(header, expected)


def refusal_of_header(header: str | None, expected: str) -> str | None:
    """Why `header` does not carry `expected` as a bearer token, or None when it does.

    The scheme is matched case-insensitively (RFC 9110 §11.1), and the token in constant time. No
    reason quotes the header: it may hold the token itself, sent without its scheme, and the reason
    is logged.

    Example:
        >>> refusal_of_header("Bearer s3cret", "s3cret") is None
        True
    """
    if header is None:
        return "the request has no Authorization header"
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer":
        return "the Authorization header does not start with the scheme 'Bearer'"
    if not hmac.compare_digest(token.strip().encode(), expected.encode()):
        return "the bearer token is not the server's"
    return None


def server_token_bearer() -> Middleware:
    """The `mcp_middleware` factory: the running server's `IdentityProvider.token`, read per message.

    Example:
        >>> {"MCPExtensionApp": {"mcp_middleware": ["jupydroid_mcp_bearer:server_token_bearer"]}}
    """
    return BearerTokenMiddleware(lambda: ServerApp.instance().identity_provider.token)
