"""The checks behind the Chat's tool-call drivers - see `chat_tool_call_reads.py` for why they exist."""

import unittest

from chat_tool_call_reads import (
    ChatReadError,
    require_allowed_and_rejected,
    require_completed_call,
    require_answered_in_turn,
    require_stopped_short,
)

PERSONA = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
OPTIONS = [
    {"option_id": "once", "name": "Allow once", "kind": "allow_once"},
    {"option_id": "always", "name": "Always allow", "kind": "allow_always"},
    {"option_id": "reject", "name": "Reject", "kind": "reject_once"},
]


def call(status: str, chose: str | None = None, printed: str = "") -> dict[str, object]:
    """One call as jupyter_ai_acp_client's `tool_call_manager.py` stores it, trimmed."""
    shaped: dict[str, object] = {"tool_call_id": "c", "title": "t", "kind": "execute", "status": status}
    if chose:
        shaped |= {"permission_options": OPTIONS, "permission_status": "resolved", "selected_option_id": chose}
    if printed:
        shaped["raw_output"] = {"output": printed}
    return shaped


def calls_message(*calls: dict[str, object]) -> dict[str, object]:
    return {"body": "", "sender": PERSONA, "metadata": {"tool_calls": list(calls)}}


def answer(body: str) -> dict[str, object]:
    return {"body": body, "sender": PERSONA}


QUESTION = {"body": "Count from 1 to 1000", "sender": "bbff425b", "metadata": {"to_persona": PERSONA}}


class RequireCompletedCallTest(unittest.TestCase):
    def test_passes_with_a_completed_call(self):
        require_completed_call([QUESTION, calls_message(call("completed")), answer("kumquat")])

    def test_fails_when_every_call_is_still_running(self):
        with self.assertRaisesRegex(ChatReadError, "in_progress"):
            require_completed_call([calls_message(call("in_progress"))])

    def test_fails_when_no_message_holds_a_call(self):
        with self.assertRaises(ChatReadError):
            require_completed_call([QUESTION, answer("kumquat")])


class RequireAllowedAndRejectedTest(unittest.TestCase):
    def test_passes_when_the_allowed_call_printed_and_the_rejected_one_did_not(self):
        allowed = calls_message(call("completed", "once", "permission-42\n"))
        rejected = calls_message(call("failed", "reject"))
        require_allowed_and_rejected([allowed, rejected], "permission-42", "second-42")

    def test_fails_when_the_allowed_call_printed_something_else(self):
        allowed = calls_message(call("completed", "once", "nothing\n"))
        rejected = calls_message(call("failed", "reject"))
        with self.assertRaisesRegex(ChatReadError, "permission-42"):
            require_allowed_and_rejected([allowed, rejected], "permission-42", "second-42")

    def test_fails_when_nothing_was_rejected(self):
        allowed = calls_message(call("completed", "once", "permission-42\n"))
        with self.assertRaisesRegex(ChatReadError, "rejected"):
            require_allowed_and_rejected([allowed], "permission-42", "second-42")

    def test_fails_when_the_rejected_call_ran_anyway(self):
        allowed = calls_message(call("completed", "once", "permission-42\n"))
        rejected = calls_message(call("completed", "reject", "second-42\n"))
        with self.assertRaisesRegex(ChatReadError, "second-42"):
            require_allowed_and_rejected([allowed, rejected], "permission-42", "second-42")


class RequireStoppedShortTest(unittest.TestCase):
    def test_counts_the_numbers_of_an_answer_that_stayed_short(self):
        stopped = [QUESTION, answer("1\n2\n3\n30")]
        self.assertEqual((4, 30), require_stopped_short(stopped, stopped))

    def test_reads_the_newest_persona_message(self):
        earlier = answer("1\n2\n3\n4\n5")
        stopped = [earlier, QUESTION, calls_message(call("completed")), answer("1\n2")]
        self.assertEqual((2, 2), require_stopped_short(stopped, stopped))

    def test_fails_when_the_answer_grew_after_stop(self):
        with self.assertRaisesRegex(ChatReadError, "grew"):
            require_stopped_short([answer("1\n2")], [answer("1\n2\n3")])

    def test_fails_when_the_answer_reached_1000(self):
        finished = [answer("1\n999\n1000")]
        with self.assertRaisesRegex(ChatReadError, "1000"):
            require_stopped_short(finished, finished)

    def test_fails_when_the_answer_holds_no_number(self):
        empty = [answer("")]
        with self.assertRaisesRegex(ChatReadError, "None"):
            require_stopped_short(empty, empty)


if __name__ == "__main__":
    unittest.main()


def asked(body: str) -> dict[str, object]:
    return {"body": body, "sender": "bbff425b", "metadata": {"to_persona": PERSONA}}


COUNTED = answer("\n".join(str(n) for n in range(1, 1001)))
QUEUED = [("What is 2+2?", "4"), ("What is 3+3?", "6")]


class RequireAnsweredInTurnTest(unittest.TestCase):
    def test_passes_when_the_count_ended_and_each_was_answered_before_the_next(self):
        chat = [QUESTION, COUNTED, asked("What is 2+2?"), answer("4"), asked("What is 3+3?"), answer("6")]
        self.assertEqual(1000, require_answered_in_turn(chat, QUEUED))

    def test_fails_when_a_queued_question_cut_the_count_short(self):
        chat = [QUESTION, answer("1\n2\n303"), asked("What is 2+2?"), answer("4"), asked("What is 3+3?"), answer("6")]
        with self.assertRaisesRegex(ChatReadError, "reached 303"):
            require_answered_in_turn(chat, QUEUED)

    def test_fails_when_the_next_was_asked_before_the_previous_was_answered(self):
        chat = [QUESTION, COUNTED, asked("What is 2+2?"), asked("What is 3+3?"), answer("6")]
        with self.assertRaisesRegex(ChatReadError, "2\\+2"):
            require_answered_in_turn(chat, QUEUED)

    def test_fails_when_a_queued_question_was_never_asked(self):
        with self.assertRaisesRegex(ChatReadError, "never asked"):
            require_answered_in_turn([QUESTION, COUNTED, asked("What is 2+2?"), answer("4")], QUEUED)
