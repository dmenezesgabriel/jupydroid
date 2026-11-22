"""`focus_sample`, `summarize_samples` and `record_samples`: what held window focus, and when.

See `focus_samples.py`: a whole-suite run on dev36 ended with failures nobody could place, because
nothing had sampled the focused window while it ran.
"""

import io
import json
import unittest

from focus_samples import focus_sample, record_samples, summarize_samples

APP_FOCUSED = "  mCurrentFocus=Window{1a2b u0 com.jupydroid.app/com.jupydroid.app.MainActivity}\n"
DIALOG_FOCUSED = "  mCurrentFocus=Window{3c4d u0 Application Not Responding: com.android.systemui}\n"
NOTHING_FOCUSED = "  mCurrentFocus=null\n"


class FakeAdb:
    """A device whose `dumpsys window` answers from a script, one reply per call, the last one repeating."""

    def __init__(self, replies: list[str]) -> None:
        self.replies = list(replies)
        self.commands: list[tuple[str, ...]] = []

    def __call__(self, *args: str) -> str:
        self.commands.append(args)
        return self.replies.pop(0) if len(self.replies) > 1 else self.replies[0]


class FakeClock:
    """Times that advance by one second per read, and a sleep that records what it was asked for."""

    def __init__(self) -> None:
        self.reads = 0
        self.slept: list[float] = []

    def now(self) -> str:
        self.reads += 1
        return f"2026-11-17T10:00:{self.reads:02d}"

    def sleep(self, seconds: float) -> None:
        self.slept.append(seconds)


class FocusSampleTest(unittest.TestCase):
    def test_names_the_window_that_has_focus(self):
        sample = json.loads(focus_sample(APP_FOCUSED, "2026-11-17T10:00:00"))

        self.assertEqual("com.jupydroid.app/com.jupydroid.app.MainActivity", sample["focus"])
        self.assertEqual([], sample["anr"])
        self.assertEqual("2026-11-17T10:00:00", sample["taken_at"])

    def test_names_the_dialog_that_took_focus(self):
        sample = json.loads(focus_sample(DIALOG_FOCUSED, "t"))

        self.assertEqual("Application Not Responding: com.android.systemui", sample["focus"])
        self.assertEqual(["com.android.systemui"], sample["anr"])

    def test_no_focused_window_is_null_not_the_word_null(self):
        sample = json.loads(focus_sample(NOTHING_FOCUSED, "t"))

        self.assertIsNone(sample["focus"])

    def test_a_dump_without_a_focus_line_is_null_too(self):
        # `dumpsys window` answers nothing useful while system_server restarts.
        self.assertIsNone(json.loads(focus_sample("", "t"))["focus"])

    def test_a_dialog_behind_the_focused_window_still_counts_as_an_anr(self):
        stacked = APP_FOCUSED + "  Window{9 u0 Application Not Responding: com.android.systemui}\n"

        self.assertEqual(["com.android.systemui"], json.loads(focus_sample(stacked, "t"))["anr"])


class SummarizeSamplesTest(unittest.TestCase):
    def test_counts_the_samples_with_a_dialog_and_says_when_it_was_first_and_last_up(self):
        lines = [
            focus_sample(APP_FOCUSED, "10:00:00"),
            focus_sample(DIALOG_FOCUSED, "10:00:02"),
            focus_sample(DIALOG_FOCUSED, "10:00:04"),
            focus_sample(APP_FOCUSED, "10:00:06"),
        ]

        self.assertEqual(
            "4 samples, 2 with an ANR dialog (com.android.systemui), first at 10:00:02, last at 10:00:04",
            summarize_samples(lines),
        )

    def test_says_so_when_no_dialog_was_up(self):
        lines = [focus_sample(APP_FOCUSED, "10:00:00"), focus_sample(APP_FOCUSED, "10:00:02")]

        self.assertEqual("2 samples, no ANR dialog", summarize_samples(lines))

    def test_counts_the_samples_with_no_focused_window(self):
        lines = [focus_sample(NOTHING_FOCUSED, "10:00:00"), focus_sample(APP_FOCUSED, "10:00:02")]

        self.assertEqual("2 samples, no ANR dialog, 1 with no focused window", summarize_samples(lines))

    def test_no_samples_is_said_not_divided_by(self):
        self.assertEqual("0 samples: the sampler never ran", summarize_samples([]))

    def test_a_malformed_line_is_quoted_with_the_shape_expected(self):
        with self.assertRaisesRegex(ValueError, r"'not json'.*taken_at.*focus.*anr"):
            summarize_samples(["not json"])


class RecordSamplesTest(unittest.TestCase):
    def test_writes_one_timestamped_line_per_poll_and_waits_between_them(self):
        adb, clock, out = FakeAdb([APP_FOCUSED, DIALOG_FOCUSED]), FakeClock(), io.StringIO()
        polls = iter([False, False, True])

        record_samples(adb, out, clock.now, clock.sleep, lambda: next(polls), interval_seconds=2.0)

        written = [json.loads(line) for line in out.getvalue().splitlines()]
        self.assertEqual(["2026-11-17T10:00:01", "2026-11-17T10:00:02"], [s["taken_at"] for s in written])
        self.assertEqual([[], ["com.android.systemui"]], [s["anr"] for s in written])
        self.assertEqual([2.0, 2.0], clock.slept)
        self.assertEqual([("shell", "dumpsys", "window")] * 2, adb.commands)

    def test_a_sample_is_flushed_when_taken_so_a_killed_run_keeps_it(self):
        flushes: list[int] = []

        class CountingOut(io.StringIO):
            def flush(self) -> None:
                flushes.append(len(self.getvalue().splitlines()))

        stops = iter([False, True])
        record_samples(FakeAdb([APP_FOCUSED]), CountingOut(), FakeClock().now, FakeClock().sleep, lambda: next(stops))

        self.assertEqual([1], flushes)


if __name__ == "__main__":
    unittest.main()
