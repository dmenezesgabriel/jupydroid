"""`tap_wait_button` and `clear_dialogs`, the steps that remove a dialog holding window focus.

See `clear_system_anr.py`: a force-stop left SystemUI's dialog up on dev36, and a press on "Wait"
removed it.
"""

import pathlib
import unittest

from clear_system_anr import clear_dialogs, tap_wait_button

REAL_DIALOG = (pathlib.Path(__file__).resolve().parent / "testdata" / "anr-dialog").joinpath(
    "system-ui-not-responding.xml"
)
NO_SCREEN_DIALOG = "<hierarchy/>"


class FakeAdb:
    """A device with a stack of "isn't responding" dialogs, answering the way dev36 does.

    `exec-out uiautomator dump` returns the dialog's XML while one is up. `shell uiautomator dump`
    writes it to the tty the dump was pointed at and returns only the line below, so a press found
    through it never has a button to find. A press removes the dialog on top, unless `stubborn`.
    """

    def __init__(self, dialogs: list[str], stubborn: bool = False) -> None:
        self.dialogs = list(dialogs)
        self.stubborn = stubborn
        self.commands: list[tuple[str, ...]] = []

    def __call__(self, *args: str) -> str:
        self.commands.append(args)
        if args[:3] == ("exec-out", "uiautomator", "dump"):
            return REAL_DIALOG.read_text() if self.dialogs else NO_SCREEN_DIALOG
        if args[:3] == ("shell", "uiautomator", "dump"):
            return "UI hierchary dumped to: /dev/tty\n"
        if args[:3] == ("shell", "dumpsys", "window"):
            return "".join(f"Window{{1 u0 Application Not Responding: {p}}}\n" for p in self.dialogs)
        if args[:3] == ("shell", "input", "tap") and self.dialogs and not self.stubborn:
            self.dialogs.pop()
        return ""

    def taps(self) -> list[tuple[str, ...]]:
        return [c for c in self.commands if c[:3] == ("shell", "input", "tap")]


class TapWaitButtonTest(unittest.TestCase):
    def test_presses_wait_at_the_centre_the_dump_gives(self):
        adb = FakeAdb(["com.android.systemui"])

        self.assertTrue(tap_wait_button(adb))

        self.assertEqual([("shell", "input", "tap", "159", "370")], adb.taps())

    def test_presses_nothing_when_no_dialog_is_on_screen(self):
        adb = FakeAdb([])

        self.assertFalse(tap_wait_button(adb))

        self.assertEqual([], adb.taps())


class ClearDialogsTest(unittest.TestCase):
    def test_a_clear_screen_is_left_alone(self):
        adb = FakeAdb([])

        self.assertEqual([], clear_dialogs(adb, sleep=lambda seconds: None))
        self.assertEqual([], adb.taps())

    def test_every_stacked_dialog_is_pressed_away(self):
        adb = FakeAdb(["com.android.systemui", "com.google.android.apps.nexuslauncher"])

        self.assertEqual([], clear_dialogs(adb, sleep=lambda seconds: None))
        self.assertEqual(2, len(adb.taps()))

    def test_nothing_is_force_stopped(self):
        # A force-stop removed no dialog in any run on dev36, and the run that stacked two of them
        # lost the package service after one.
        adb = FakeAdb(["com.android.systemui"])

        clear_dialogs(adb, sleep=lambda seconds: None)

        self.assertEqual([], [c for c in adb.commands if "force-stop" in c])

    def test_a_dialog_that_survives_every_press_is_named_and_the_presses_are_bounded(self):
        adb = FakeAdb(["com.android.systemui"], stubborn=True)

        self.assertEqual(["com.android.systemui"], clear_dialogs(adb, sleep=lambda seconds: None, presses=3))
        self.assertEqual(3, len(adb.taps()))

    def test_it_waits_after_each_press_before_reading_the_screen_again(self):
        adb = FakeAdb(["com.android.systemui"])
        waited: list[float] = []

        clear_dialogs(adb, sleep=waited.append)

        self.assertEqual(1, len(waited))


if __name__ == "__main__":
    unittest.main()
