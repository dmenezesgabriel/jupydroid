"""Drops the history an ACP agent replays on `session/load` by its place in the stream, not by the clock.

jupyter_ai_acp_client 0.3.3 `default_acp_client.py` ignores a `session/update` while the session's load
task is not done, since the chat file already holds that history. But `acp` runs each notification as a
task of its own off a queue, and resolves a response's future directly
(usr/lib/python3.12/site-packages/acp/connection.py, `_process_message`), so a replayed chunk whose task
runs after the load resolved passes the guard and is written as a new message. On SM-X216B a chat moved
to a tab showed the agent's last answer twice, written 4 ms before the persona logged the load.
Unchanged on https://github.com/jupyter-ai-contrib/jupyter-ai-acp-client at 77016f63530f.

The client is handed `ReplayedUpdates` (acp_replayed_updates.py), an `acp` stream observer that counts
the updates read between the load's request and its response, and `session_update` drops that many.
acp_replayed_history_probe.py is the gate.

Each edit must match exactly once, so a release that changes the file fails the image build here,
which is the moment to check whether upstream has fixed the guard and this can go.
"""

import importlib.util
import pathlib
import shutil
import sys

IMPORT_ANCHOR = "from .permission_manager import PermissionManager\n"
INIT_ANCHOR = "        self._loading_sessions: dict[str, asyncio.Task[LoadSessionResponse]] = {}\n"
CONNECT = "conn = connect_to_agent(self, proc.stdin, proc.stdout)\n"
LOAD_RPC = (
    "        response = await conn.load_session("
    "cwd=persona.get_chat_dir(), session_id=session_id, mcp_servers=mcp_servers)\n"
)
GUARD = "        if self._is_session_loading(session_id):\n            return\n"

EDITS = [
    (IMPORT_ANCHOR, IMPORT_ANCHOR + "from .replayed_updates import ReplayedUpdates\n"),
    (INIT_ANCHOR, INIT_ANCHOR + "        self._replayed_updates = ReplayedUpdates()\n"),
    (CONNECT, "conn = connect_to_agent(self, proc.stdin, proc.stdout, observers=[self._replayed_updates.observe])\n"),
    (
        LOAD_RPC,
        "        self._replayed_updates.begin(session_id)\n"
        + LOAD_RPC
        + "        self._replayed_updates.end(session_id)\n",
    ),
    (GUARD, "        if self._replayed_updates.take(session_id):\n            return\n"),
]


def package_dir():
    return pathlib.Path(importlib.util.find_spec("jupyter_ai_acp_client").origin).parent


def replace_once(source, old, new):
    count = source.count(old)
    if count != 1:
        sys.exit(f"default_acp_client.py holds {count} copies of {old!r}, expected exactly 1")
    return source.replace(old, new)


package = package_dir()
shutil.copyfile(pathlib.Path(__file__).with_name("acp_replayed_updates.py"), package / "replayed_updates.py")
client = package / "default_acp_client.py"
source = client.read_text()
for old, new in EDITS:
    source = replace_once(source, old, new)
client.write_text(source)
print(f"{client} drops a loaded session's replayed history by its place in the stream")
