#!/usr/bin/env python3
"""What a `.chat` file says the agent did, read after a Chat tool-call flow.

A row on screen can come from somewhere other than the call it claims to show, and a Stop that
only stopped the screen looks like one that stopped the agent. The `.chat` file is what the server
stored, so each driver checks it after its flow. jupyter_ai_acp_client's `tool_call_manager.py` (in
the shipped rootfs) keeps a message's calls in `metadata.tool_calls`, with snake_case keys.

    python3 scripts/chat_tool_call_reads.py completed-call untitled.chat
    python3 scripts/chat_tool_call_reads.py allowed-and-rejected untitled.chat permission-42 second-42
    python3 scripts/chat_tool_call_reads.py stopped-short untitled.chat untitled-later.chat
    python3 scripts/chat_tool_call_reads.py answered-in-turn untitled.chat "What is 2+2?" 4 "What is 3+3?" 6
"""

import json
import re
import sys

TypedMessage = dict[str, object]
TypedCall = dict[str, object]

PERSONA_PREFIX = "jupyter-ai-personas::"
COUNT_LIMIT = 1000


class ChatReadError(Exception):
    """The `.chat` file does not hold what the flow on screen showed."""


def tool_calls(messages: list[TypedMessage]) -> list[TypedCall]:
    """Every tool call in the chat, in message order.

    >>> tool_calls([{"metadata": {"tool_calls": [{"status": "completed"}]}}])
    [{'status': 'completed'}]
    """
    return [c for m in messages for c in (m.get("metadata") or {}).get("tool_calls", [])]


def describe(c: TypedCall) -> str:
    """One call as a line for a driver's log, e.g. `execute 'echo hi' completed resolved chose once`."""
    permission = f"{c.get('permission_status')} chose {c.get('selected_option_id')}"
    return f"{c.get('kind')} '{c.get('title')}' {c.get('status')} {permission}"


def require_completed_call(messages: list[TypedMessage]) -> None:
    """Fails unless some call completed, e.g. the read call a row shows a check for.

    >>> require_completed_call([{"metadata": {"tool_calls": [{"status": "completed"}]}}])
    """
    calls = tool_calls(messages)
    if not any(c.get("status") == "completed" for c in calls):
        raise ChatReadError(f"no completed tool call in the chat, so the row shows nothing the agent did: {calls}")


def chosen_kind(c: TypedCall) -> str:
    """The kind of the option a call's permission was answered with, e.g. `allow_once`; "" if none."""
    options = c.get("permission_options") or []
    return next((o["kind"] for o in options if o["option_id"] == c.get("selected_option_id")), "")


def printed(c: TypedCall) -> str:
    return json.dumps(c.get("raw_output", ""))


def require_allowed_and_rejected(messages: list[TypedMessage], allowed_text: str, rejected_text: str) -> None:
    """Fails unless an allowed call completed printing `allowed_text`, and a rejected one printed no `rejected_text`.

    >>> require_allowed_and_rejected(chat, "permission-42", "second-42")  # doctest: +SKIP
    """
    calls = tool_calls(messages)
    allowed = [c for c in calls if chosen_kind(c).startswith("allow")]
    rejected = [c for c in calls if "reject" in chosen_kind(c)]
    if not any(c.get("status") == "completed" and allowed_text in printed(c) for c in allowed):
        raise ChatReadError(f"no allowed call printed {allowed_text}: {allowed}")
    if not rejected or any(rejected_text in printed(c) for c in rejected):
        raise ChatReadError(f"no rejected call, or it printed {rejected_text}: {rejected}")


def newest_answer(messages: list[TypedMessage]) -> str:
    return [m for m in messages if str(m["sender"]).startswith(PERSONA_PREFIX)][-1]["body"]


def require_stopped_short(first: list[TypedMessage], later: list[TypedMessage]) -> tuple[int, int | None]:
    """Fails unless the newest answer is the same in both reads and never reached COUNT_LIMIT.

    Returns how many numbers the answer holds and the last of them.

    >>> stopped = [{"sender": PERSONA_PREFIX, "body": "1\\n2"}]
    >>> require_stopped_short(stopped, stopped)
    (2, 2)
    """
    body, later_body = newest_answer(first), newest_answer(later)
    if body != later_body:
        raise ChatReadError(f"the answer grew after Stop: {len(body)} then {len(later_body)} characters")
    numbers = [int(n) for n in re.findall(r"\d+", body)]
    if not numbers or max(numbers) >= COUNT_LIMIT:
        raise ChatReadError(f"the answer reached {max(numbers, default=None)}, expected numbers below {COUNT_LIMIT}")
    return len(numbers), numbers[-1]


def answers(messages: list[TypedMessage]) -> list[str]:
    return [str(m["body"]) for m in messages if str(m["sender"]).startswith(PERSONA_PREFIX)]


def require_answered_in_turn(messages: list[TypedMessage], queued: list[tuple[str, str]]) -> int:
    """Fails unless the count before the first of `queued` reached COUNT_LIMIT, and each queued question
    was answered with its expected text before the next was asked.

    A question asked while the count arrived would have cut it short, and one asked before the previous
    was answered would have cut that answer short (`QuestionQueue`). Returns the count's last number.

    >>> count = {"sender": PERSONA_PREFIX, "body": "1\\n1000"}
    >>> chat = [count, {"sender": "me", "body": "2+2?"}, {"sender": PERSONA_PREFIX, "body": "4"}]
    >>> require_answered_in_turn(chat, [("2+2?", "4")])
    1000
    """
    bodies = [m["body"] for m in messages]
    missing = [question for question, _ in queued if question not in bodies]
    if missing:
        raise ChatReadError(f"never asked {missing}; the chat holds {[str(b)[:40] for b in bodies]}")
    starts = [bodies.index(question) for question, _ in queued] + [len(messages)]
    numbers = [int(n) for n in re.findall(r"\d+", "\n".join(answers(messages[: starts[0]])))]
    if max(numbers, default=0) < COUNT_LIMIT:
        reached = max(numbers, default=None)
        raise ChatReadError(f"the count before the queued questions reached {reached}, expected {COUNT_LIMIT}")
    for (question, expected), start, end in zip(queued, starts, starts[1:]):
        replied = answers(messages[start + 1 : end])
        if not any(expected in body for body in replied):
            raise ChatReadError(f"{question!r} was not answered with {expected!r} before the next: {replied}")
    return numbers[-1]


def read_messages(path: str) -> list[TypedMessage]:
    with open(path, encoding="utf-8") as chat:
        return json.load(chat)["messages"]


def main(argv: list[str]) -> int:
    check, paths = argv[0], argv[1:]
    for c in tool_calls(read_messages(paths[0])):
        print(f"{paths[0]}: {describe(c)}")
    if check == "completed-call":
        require_completed_call(read_messages(paths[0]))
    elif check == "allowed-and-rejected":
        require_allowed_and_rejected(read_messages(paths[0]), paths[1], paths[2])
    elif check == "stopped-short":
        count, last = require_stopped_short(read_messages(paths[0]), read_messages(paths[1]))
        print(f"{paths[0]}: the answer holds {count} numbers, the last {last}")
    elif check == "answered-in-turn":
        queued = list(zip(paths[1::2], paths[2::2]))
        last = require_answered_in_turn(read_messages(paths[0]), queued)
        print(f"{paths[0]}: the count ended at {last}, then {len(queued)} queued questions were answered in turn")
    else:
        raise ChatReadError(
            f"unknown check {check!r}, expected completed-call, allowed-and-rejected, stopped-short or answered-in-turn"
        )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except ChatReadError as error:
        sys.exit(str(error))
