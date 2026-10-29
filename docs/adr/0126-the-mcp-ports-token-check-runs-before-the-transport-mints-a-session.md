# ADR-0126 — The MCP port's token check runs before the transport mints a session

Status: accepted
Date: 2026-10-29

## Context

[ADR-0118](0118-the-app-is-the-commands-toolkits-web-client-and-the-mcp-port-takes-the-servers-token.md)
put the token check on `mcp_middleware`, FastMCP's message-level hook. Measured on the tablet with
the port's own driver, that check runs too late: FastMCP's stateful transport mints the session
before the first message dispatches, so a tokenless `initialize` got HTTP 200 with an
`mcp-session-id`, and `GET` and `DELETE` on it were served — refused only inside the stream. A
caller holding nothing but `INTERNET` could grow the session table and hold its streams without
limit.

jupyter-server-mcp 0.4.1 offers no config hook for HTTP middleware:
`MCPServer._run_http_async_without_signals` builds `self.mcp.http_app(...)` itself, and
`MCPServer.__init__` builds `FastMCP(self.name)`, keeping the `auth=` parameter out of reach
(ADR-0118). FastMCP's `http_app(middleware=)` is the documented extension point, forwarded to
`create_streamable_http_app` since fastmcp 2.3.2 (PrefectHQ/fastmcp@0f4674e
`fastmcp_slim/fastmcp/server/http.py`): the custom middleware lands after the Host/Origin guard and
before the router, so before `StreamableHTTPSessionManager.handle_request`. FastMCP's own comment
names the hole a gateway must close — `auth=None` "must prevent direct unauthenticated access" —
and jupyter-server-mcp issue
<https://github.com/jupyter-ai-contrib/jupyter-server-mcp/issues/25> proposes the same refusal and
remains open and unimplemented.

## Decision drivers

- No request without the token mints or reaches anything: no session row, no held stream.
- Adopt the documented extension point over a bigger patch or a new config surface.
- One refusal predicate, reused — the answer a wrong credential earns must not drift between layers.
- The refusal never echoes the header back: it may carry the token sent without its scheme, and the
  reason is logged.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Where the refusal attaches | `http_app(middleware=)` via an image-build patch; wrap `app` before `uvicorn.Config` (also one patch); patch `FastMCP(self.name, auth=…)` (pulls the auth-provider stack, scopes and OAuth routes nothing needs); a new `mcp_http_middleware` trait (two files patched, a config surface nothing else uses) | `middleware=`. It is the documented hook and lands exactly where `auth=` would have, without the OAuth surface |
| Refusal status | 401 + `WWW-Authenticate: Bearer` (RFC 9110 §15.5.2, and FastMCP's own `RequireAuthMiddleware`); 403 (issue #25's FastAPI sketch) | 401 — the code a missing-or-wrong credential earns, and the challenge header is free |
| Keep the message-layer `mcp_middleware` | drop it, now that the HTTP layer refuses first; keep it | keep — the transport guard and the tool guard are different boundaries, and it costs one early return per message |

## Decision

**`BearerTokenHttpMiddleware` — `refusal_of_header` one layer up, as a Starlette middleware — is
passed to `FastMCP.http_app(middleware=…)` by `tools/rootfs/mcp_http_bearer_patch.py`, which adds
the argument to `mcp_server.py`'s `http_app()` call at image build.**

- The patch is the `mcp_lazy_import_patch.py` convention: an exact-match anchor that fails the
  build when upstream drifts, which is the moment to check whether issue #25 landed the refusal
  and the patch can go.
- The refusal answers 401 with `WWW-Authenticate: Bearer` and a reason naming the expected shape,
  never the header sent.
- The token is still read per request through the same lazy callable, so the factory stays
  side-effect-free.
- `mcp_bearer_probe.py` gates the whole matrix inside the image build on both ABIs; the build
  fails before any of it ships.

Measured on SM-X216B with `scripts/mcp-port-requires-token-driver.sh`: a tokenless `POST`, `GET`
and `DELETE` each got 401 and no `mcp-session-id` — including a `GET` and a `DELETE` carrying a
live session id. The server's token's `initialize` minted the session, `tools/list` returned
`['insert_cell']`, and its `GET` stream and `DELETE` were served 200. After a kill and relaunch
under the fresh token, the same calls got the same answers, and the server log's access lines
show the same sequence.

## Consequences

- A tokenless request of any method mints nothing; the pre-patch hole — a `GET` stream held open
  by a caller with no token — is gone, while a token-bearing client's stream passes straight
  through.
- The patch names `jupydroid_mcp_bearer` inside `mcp_server.py` rather than adding a trait: the
  image always wants the middleware, and a drift in the call site fails the build, which is when
  the decision gets revisited.
- If issue #25 lands an HTTP-layer refusal upstream, this patch is deleted rather than kept beside
  it — the probe is what says so, by going red.
