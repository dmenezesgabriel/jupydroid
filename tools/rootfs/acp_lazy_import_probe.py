"""Proves importing jupyter_ai_acp_client loads no `acp`, and that its permission route still can.

Run at image build time, after the strip, so the bytecode tested is the bytecode shipped. A server
start imports every enabled extension, and `acp` with pydantic beneath it was 0.85 s of the 1.22 s
this one cost on the tablet (`python3 -X importtime`). The personas that use `acp` load when a chat
opens, so nothing at server start needs it (jupyter_ai_persona_manager 0.2.2 `persona_manager.py`).

The second half calls `PermissionHandler._find_client_for_session` over one persona that is not an
ACP persona, which is the one path that reads `BaseAcpPersona`: an import that no longer resolves
is a build error rather than a 500 on the first permission a person answers.
"""

import asyncio
import sys
from types import SimpleNamespace

import jupyter_server.serverapp  # noqa: F401 - the server has loaded it before any extension
import jupyter_ai_acp_client  # noqa: F401
from jupyter_ai_acp_client.routes import PermissionHandler


def loaded_acp_modules():
    return sorted(name for name in sys.modules if name == "acp" or name.startswith("acp."))


def handler_over_one_plain_persona():
    manager = SimpleNamespace(personas={"plain": object()})
    settings = {"jupyter-ai": {"persona-managers": {"probe.chat": manager}}}
    return SimpleNamespace(serverapp=SimpleNamespace(web_app=SimpleNamespace(settings=settings)))


at_import = loaded_acp_modules()
assert not at_import, f"importing jupyter_ai_acp_client loaded acp, expected none: {at_import[:5]}"

found = asyncio.run(PermissionHandler._find_client_for_session(handler_over_one_plain_persona(), "s"))
assert found is None, f"a plain persona yielded a client, expected None: {found!r}"
assert loaded_acp_modules(), "the permission route ran without loading acp, so it never read BaseAcpPersona"
print("jupyter_ai_acp_client imports without acp; its permission route still resolves BaseAcpPersona")
