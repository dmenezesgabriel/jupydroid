# JD-071 — The MCP port mints no session for a tokenless caller

Milestone: `M8` · State: open · Depends on: JD-048 · Closes: `GAP-191`

> Copy this file to start an item. **Delete it when the item ships** — its durable residue is an ADR
> and the code comments, nothing else ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

An app holding INTERNET can no longer mint MCP sessions on a Space's MCP port without the server's
token: a tokenless request of any method — `POST`, `GET`, `DELETE` — is refused at the HTTP layer
before FastMCP's `StreamableHTTPSessionManager` runs, so nothing is minted and nothing is held open.

## Demo

On the arm64 tablet (`SM-X216B`): the MCP server runs inside the Space, so nothing here exists on
x86_64, where no Space runs.

1. `tools/rootfs/build.sh arm64-v8a` — the build itself runs `mcp_bearer_probe.py`, which fails the
   build if a tokenless `POST`/`GET`/`DELETE` reaches the transport.
2. `tools/rootfs/stage-app-artifacts.sh arm64-v8a`, then `JAVA_HOME=<jdk> ./gradlew :app:assembleDebug`
   and install on the tablet.
3. `scripts/mcp-port-requires-token-driver.sh` — extended to `GET` and `DELETE`. `answers.txt` shows
   each tokenless call refused with no `mcp-session-id`, and the token-bearing client's `initialize`,
   `GET` stream and `DELETE` served. `evidence/mcp-port-requires-token/` carries it plus
   `jupyter_server.log` and the flow screenshot.
4. Kill and relaunch — a fresh token is minted; the same tokenless calls are still refused.

## Who else implements this

**Every reference gets a row, including the ones that do not have it** — an absence across several
mature products is evidence too. Verified by reading code, not memory.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyter_server/auth/identity.py`, `jupyter_server/base/handlers.py` | every request handler checks the token before its method runs — the same refusal, one layer up from where the MCP port needs it |
| `vscode` | ABSENT | — | serves no MCP over HTTP; it is a client |
| `vscode-jupyter` | ABSENT | — | serves no MCP over HTTP |
| `intellij-community` | ABSENT | — | serves no MCP over HTTP |
| `termux-app` | ABSENT | — | no MCP server |
| `winlator` | ABSENT | — | no MCP server |
| the shipped rootfs | PARTIAL | `usr/lib/python3.12/site-packages/jupydroid_mcp_bearer.py` | `BearerTokenMiddleware` refuses every MCP *message* without the bearer — but FastMCP's transport has already minted the session by then (`GAP-191`) |

**Count:** 1 of 7 implement the HTTP-layer refusal, and it is the port our own server already runs.
jupyter-server-mcp@d1823a5 — the component itself — has the message-level `mcp_middleware` hook only;
issue <https://github.com/jupyter-ai-contrib/jupyter-server-mcp/issues/25> proposes the HTTP-layer
refusal and remains open and unimplemented. FastMCP's own comment marks the hole:
`# auth=None removes bearer-token enforcement. A gateway's authentication is outside FastMCP's
guarantees and must prevent direct unauthenticated access.` (PrefectHQ/fastmcp@0f4674e
`fastmcp_slim/fastmcp/server/http.py`, `create_streamable_http_app`) — this item is that gateway.

## Implementation inventory

### jupyter-server-mcp@d1823a5 (`reference` clone via the jupyter-ai submodule; image ships 0.4.1)

| Concern | Files |
|---|---|
| The port and its config | `jupyter_server_mcp/extension.py` — `MCPExtensionApp` traits: `mcp_port`, `mcp_tools`, `mcp_middleware` (`module:factory` → FastMCP `Middleware` — message level only) |
| The ASGI app | `jupyter_server_mcp/mcp_server.py` — `MCPServer._run_http_async_without_signals` builds `self.mcp.http_app(transport="http", host_origin_protection=…, allowed_hosts=…, allowed_origins=…)` and hands it to uvicorn. **No hook for HTTP middleware** — this is the patch site |
| Session mint | `mcp.server.streamable_http_manager.StreamableHTTPSessionManager.handle_request` (SDK) — reached only through the Starlette route on `/mcp`; `methods=None` serves every method when `auth=None` |

### PrefectHQ/fastmcp@0f4674e (v4.1.2, `fastmcp-slim`; `reference`-equivalent fetch, locked in `tools/rootfs/requirements.lock`)

| Concern | Files |
|---|---|
| The supported hook | `fastmcp/server/mixins/transport.py` — `FastMCP.http_app(..., middleware: list[starlette.middleware.Middleware] | None = None, ...)` forwards to `create_streamable_http_app` since fastmcp 2.3.2 |
| Where it lands | `fastmcp/server/http.py` `create_streamable_http_app` — `server_middleware` = `[HostOriginGuard, *auth, *middleware]`; `create_base_app` inserts `RequestContextMiddleware` at 0. Custom middleware runs **after** Host/Origin checks, **before** the router → before `StreamableHTTPASGIApp` → before the session manager |
| Shape of an ASGI middleware | `HostOriginGuardMiddleware`, `RequestContextMiddleware` in the same file: pure `__call__(scope, receive, send)` classes, non-`http` scopes passed through |
| The alternative | `auth=` on `FastMCP(...)` wraps the route in `RequireAuthMiddleware` (401 + `WWW-Authenticate`) — unreachable: `MCPServer.__init__` builds `FastMCP(self.name)` itself (ADR-0118) |

### jupyterlab@7f0e690 (for what "refuse at the HTTP layer" looks like upstream)

| Concern | Files |
|---|---|
| Token check per request | `jupyter_server/auth/identity.py`, `jupyter_server/base/handlers.py` — `JupyterHandler.prepare`/`login_available` refuse before the handler's method runs |

### Ours

| Concern | Files |
|---|---|
| The token predicate | `tools/rootfs/jupydroid_mcp_bearer.py` — `refusal_of_header` (constant-time, case-insensitive scheme, never echoes the header); `BearerTokenMiddleware` (message layer); `server_token_bearer()` (the `mcp_middleware` factory) |
| The proof | `tools/rootfs/mcp_bearer_probe.py` — runs inside `docker build` against a real `jupyter_server` (Dockerfile lines under `# Gated after the strip`) |
| The device half | `scripts/mcp-port-requires-token-driver.sh`, `.maestro/mcp-port-requires-token.yaml`, `scripts/space-server.sh` (`wait_for_mcp_port`, `open_forward`, `pull_server_log`) |
| Patch convention | `tools/rootfs/mcp_lazy_import_patch.py`, `acp_lazy_import_patch.py`, `acp_replayed_updates_patch.py` — `replace_once` exact-match edits that fail the build if upstream drifts |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the refusal attaches | `http_app(middleware=)` via an image-build patch (supported API since fastmcp 2.3.2); wrap `app` before `uvicorn.Config` (also one patch); patch `FastMCP(self.name, auth=…)` in `__init__` (pulls in the auth-provider stack, scopes and routes for OAuth we don't need); a new `mcp_http_middleware` trait (two files patched, a config surface nothing else uses) | `middleware=` is the documented extension point and lands exactly where `auth=` would have, without the OAuth surface | `middleware=` on the `http_app()` call, patched in `mcp_server.py` |
| Refusal status | 401 + `WWW-Authenticate: Bearer` (RFC 9110 §15.5.2; FastMCP `RequireAuthMiddleware`); 403 (issue #25's FastAPI sketch) | 401 is the code a missing-or-wrong credential earns, and the challenge header is free | 401 + `WWW-Authenticate: Bearer`, body carries the reason and `EXPECTED_SHAPE` — never the header |
| Keep `mcp_middleware` | drop it (HTTP layer now refuses everything first); keep it | Redundant in steady state, but it guards the tools if any path ever bypasses the transport guard, at one early return per message | keep — transport guard + tool guard, as ADR-0118 set the token story |
| Token lookup | read `ServerApp.instance().identity_provider.token` at patch/factory time; read it per request (the existing pattern) | Per request keeps the factory side-effect-free and matches `server_token_bearer()` | per request, same lazy callable |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Refuse a tokenless *message* | `jupydroid_mcp_bearer.py` `BearerTokenMiddleware.on_message` — present | `jupyter_server` `JupyterHandler.prepare` | present |
| Refuse a tokenless *request* before session mint | — nothing runs before `handle_request` | fastmcp `auth=` → `RequireAuthMiddleware` (unreachable); `middleware=` param (unpassed) | **absent** |
| Proof at image build | `mcp_bearer_probe.py` covers POST messages only | — | partial: no `GET`/`DELETE`, no "no session minted" assert |
| Proof on device | `mcp-port-requires-token-driver.sh` covers POST `initialize`/`tools/list` | — | partial: no `GET`/`DELETE` |

## Deviations

- The bearer is the **Jupyter server's own token** (ADR-0118), not issue #25's proposed
  `x-mcp-key` — one secret, minted per launch, already sent by `PersonaManager.builtin_mcp_servers`.
  No client change is needed.
- Upstream has no HTTP-middleware config hook in 0.4.1, so the patch names our module directly in
  `mcp_server.py` rather than adding a trait — the image always wants it, and a drift in the call
  site fails the build, which is the moment to revisit.

## Open decisions

None — the layer, the hook and the status code are each settled by the cited code.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/<screen>` | — |
| `di/AppContainer` | — |
| `tools/rootfs` | `jupydroid_mcp_bearer.py` (+ `BearerTokenHttpMiddleware`, `server_token_http_middlewares()`), new `mcp_http_bearer_patch.py`, `mcp_bearer_probe.py` (raw-HTTP `GET`/`DELETE`/`POST`), `Dockerfile` (COPY + RUN the patch beside `mcp_lazy_import_patch.py`) |
| unit tests | none — rootfs Python is proven by the build-time probes, per its convention |
| instrumented tests | — |
| `.maestro/` flow + `scripts/` driver | `mcp-port-requires-token.yaml` (unchanged) + `mcp-port-requires-token-driver.sh` (`GET`/`DELETE` arms) |

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — red: extend `mcp_bearer_probe.py` to assert a tokenless `POST initialize` returns 401 and
  **no `mcp-session-id`**, tokenless `GET`/`DELETE` are refused, and a tokenless `GET`/`DELETE`
  carrying a real session id is still refused. green: `BearerTokenHttpMiddleware` +
  `server_token_http_middlewares()` in `jupydroid_mcp_bearer.py`; `mcp_http_bearer_patch.py` adds the
  `middleware=` kwarg to `mcp_server.py`'s `http_app()` call; Dockerfile COPYs and RUNs it beside
  `mcp_lazy_import_patch.py`. Verified by `tools/rootfs/build.sh x86_64`.
- [x] T2 — extend `mcp-port-requires-token-driver.sh` to the `GET` and `DELETE` arms; rebuild the
  arm64 image, stage, assemble, install on `SM-X216B`; run the flow + driver; commit
  `evidence/mcp-port-requires-token/`.

## Out of scope

- Upstreaming the refusal into jupyter-server-mcp (issue #25's implementation) — the patch is the
  repair; upstream adoption only deletes it later.
- `GET`/`DELETE` semantics *with* the token beyond "the session is served" — resumability and
  session teardown behaviour are the SDK's, not ours.
- `GAP-192` (killed servers' `run/` files) — separate item JD-074.
