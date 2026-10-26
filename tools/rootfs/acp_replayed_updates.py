"""Counts the history an ACP agent replays on `session/load`, in the order it was sent.

Copied into jupyter_ai_acp_client as `replayed_updates.py` by acp_replayed_updates_patch.py, which says
why the client's own guard misses part of that history.
"""

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from acp.connection import StreamEvent

LOAD_METHOD = "session/load"
UPDATE_METHOD = "session/update"


class ReplayedUpdates:
    """The `session/update` notifications that arrived between a session's load request and its response.

    `observe` is an `acp` stream observer, which the connection calls for every message as it is read
    and before it is dispatched, so the count is in the agent's order, whenever each update's own task
    runs. Each update a load replayed is then dropped once, in `take`.

    Example:
        replayed = ReplayedUpdates()
        conn = connect_to_agent(client, proc.stdin, proc.stdout, observers=[replayed.observe])
        replayed.begin(session_id)  # before the request, since a reply can be read before send returns
        await conn.load_session(cwd=cwd, session_id=session_id, mcp_servers=[])
        replayed.end(session_id)
        if replayed.take(session_id): ...  # in session_update: a replayed update, already in the chat
    """

    def __init__(self) -> None:
        self._loading: set[str] = set()
        self._load_request_sessions: dict[int, str] = {}
        self._pending: dict[str, int] = {}

    def begin(self, session_id: str) -> None:
        self._loading.add(session_id)

    def end(self, session_id: str) -> None:
        self._loading.discard(session_id)

    def observe(self, event: "StreamEvent") -> None:
        message = event.message
        method = message.get("method")
        if event.direction == "outgoing":
            if method == LOAD_METHOD:
                self._load_request_sessions[message["id"]] = message["params"]["sessionId"]
            return
        if method == UPDATE_METHOD:
            self._count(message["params"]["sessionId"])
        elif method is None and message.get("id") in self._load_request_sessions:
            self.end(self._load_request_sessions.pop(message["id"]))

    def take(self, session_id: str) -> bool:
        """Whether this update is one a load replayed, which is then no longer counted."""
        if self._pending.get(session_id, 0) == 0:
            return False
        self._pending[session_id] -= 1
        return True

    def _count(self, session_id: str) -> None:
        if session_id in self._loading:
            self._pending[session_id] = self._pending.get(session_id, 0) + 1
