"""Proves a chat loaded again writes none of the history its agent replays, and still writes its answers.

On `session/load` an ACP agent replays the session's history as `session/update` notifications before
it answers, and Jupyter AI keeps that history in the chat file already, so `session_update` drops what
arrives while the load is in flight. `acp` runs each notification as a task of its own off a queue, but
resolves a response's future directly (usr/lib/python3.12/site-packages/acp/connection.py,
`_process_message`), so a replayed chunk can run after the load has resolved and be written as a new
message: the agent's last answer, twice in the chat. The guard is unchanged on
https://github.com/jupyter-ai-contrib/jupyter-ai-acp-client at 77016f63530f.

The fake agent below writes its replay and the load's response in one write, so the client reads both
before the dispatcher runs any replayed chunk: the order that wrote the duplicate, made certain. Then
one prompt, whose chunk comes in the same write as its response too, must be the chat's only message.

Run at image build time, after the strip, so the bytecode tested is the bytecode shipped.
"""

import asyncio
import json
import logging
import sys
from types import SimpleNamespace
from typing import Any

from jupyter_ai_acp_client.default_acp_client import JaiAcpClient

SESSION = "replayed-session"
ANSWER = "answered after the load"
REPLAYED = ["replayed question", "replayed answer", "replayed answer, last chunk"]

FAKE_AGENT = r"""
import json, sys

def frame(payload):
    return json.dumps(payload) + "\n"

def chunk(session, text):
    update = {"sessionUpdate": "agent_message_chunk", "content": {"type": "text", "text": text}}
    return frame({"jsonrpc": "2.0", "method": "session/update", "params": {"sessionId": session, "update": update}})

session, answer, replayed = sys.argv[1], sys.argv[2], json.loads(sys.argv[3])
for line in sys.stdin:
    request = json.loads(line)
    method, reply = request.get("method"), {"jsonrpc": "2.0", "id": request.get("id")}
    if method == "initialize":
        sys.stdout.write(frame({**reply, "result": {"protocolVersion": 1}}))
    elif method == "session/load":
        sys.stdout.write("".join(chunk(session, text) for text in replayed) + frame({**reply, "result": {}}))
    elif method == "session/prompt":
        sys.stdout.write(chunk(session, answer) + frame({**reply, "result": {"stopReason": "end_turn"}}))
    sys.stdout.flush()
"""


class RecordingChat:
    """The persona's chat, reduced to the messages the client writes into it."""

    def __init__(self) -> None:
        self.bodies: dict[str, str] = {}

    def add_message(self, new_message: Any, trigger_actions: Any = None) -> str:
        message_id = f"message-{len(self.bodies)}"
        self.bodies[message_id] = new_message.body
        return message_id

    def update_message(self, message: Any, append: bool = False, trigger_actions: Any = None) -> None:
        self.bodies[message.id] = (self.bodies[message.id] if append else "") + message.body

    def get_message(self, message_id: str) -> None:
        return None


def persona_over(chat: RecordingChat) -> SimpleNamespace:
    return SimpleNamespace(
        id="probe-persona",
        chat=chat,
        log=logging.getLogger("acp_replayed_history_probe"),
        get_chat_dir=lambda: "/tmp",
        get_mcp_settings=lambda: None,
        set_status=lambda: None,
        clear_status=lambda: None,
    )


async def written_after_load_and_prompt() -> list[str]:
    agent = await asyncio.create_subprocess_exec(
        sys.executable,
        "-c",
        FAKE_AGENT,
        SESSION,
        ANSWER,
        json.dumps(REPLAYED),
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
    )
    chat = RecordingChat()
    client = JaiAcpClient(agent_subprocess=agent, event_loop=asyncio.get_running_loop())
    await client.load_session(persona_over(chat), SESSION)
    await client.prompt_and_reply(SESSION, "a question")
    # The answer's chunk is queued behind every replayed one, so once it is written they have all run.
    for _ in range(100):
        if ANSWER in chat.bodies.values():
            break
        await asyncio.sleep(0.05)
    agent.kill()
    await agent.wait()
    return list(chat.bodies.values())


written = asyncio.run(written_after_load_and_prompt())
assert written == [ANSWER], f"the chat holds {written!r}; expected only the answer, [{ANSWER!r}]"
print("a loaded session's replayed history writes no message, and the answer after it is written once")
