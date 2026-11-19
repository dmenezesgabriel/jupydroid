"""`watch_boot`, `boot_line` and `summarise_boots`, the steps that count a cold boot's dialog.

See `count_anr_per_boot.py`: a cold `dev36` boot raised SystemUI's "isn't responding" dialog on
some boots and not on others, and nothing had counted which.
"""

import json
import unittest

from count_anr_per_boot import (
    BootSample,
    boot_line,
    is_clean_boot,
    read_load,
    require_absent,
    require_disabled,
    require_room,
    summarise_boots,
    watch_boot,
)

NO_DIALOG = "mCurrentFocus=Window{1 u0 com.jupydroid.app/.MainActivity}\n"
SYSTEMUI_DIALOG = "mCurrentFocus=Window{1 u0 Application Not Responding: com.android.systemui}\n"


class FakeClock:
    """Time that moves only when the watcher sleeps, so a test runs in no time and repeats."""

    def __init__(self) -> None:
        self.now = 0.0

    def __call__(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.now += seconds


class FakeBootedDevice:
    """A device whose window list shows the dialog from `dialog_from` to `dialog_until` seconds."""

    def __init__(self, clock: FakeClock, dialog_from: float = -1.0, dialog_until: float = -1.0) -> None:
        self.clock = clock
        self.dialog_from = dialog_from
        self.dialog_until = dialog_until

    def window(self) -> str:
        shown = self.dialog_from <= self.clock.now < self.dialog_until
        return SYSTEMUI_DIALOG if shown else NO_DIALOG


def _watch(device: FakeBootedDevice, clock: FakeClock, loads: list[float] | None = None) -> BootSample:
    readings = iter(loads or [0.5] * 100)
    return watch_boot(
        read_window=device.window,
        read_host_load=lambda: next(readings),
        clock=clock,
        sleep=clock.sleep,
        candidate="baseline",
        boot=1,
        window_seconds=10.0,
        interval_seconds=2.0,
    )


class WatchBootTest(unittest.TestCase):
    def test_a_boot_with_no_dialog_counts_none(self):
        clock = FakeClock()

        sample = _watch(FakeBootedDevice(clock), clock)

        self.assertEqual(0, sample.dialog_samples)
        self.assertIsNone(sample.first_dialog_seconds)
        self.assertEqual(5, sample.samples)

    def test_a_dialog_up_at_boot_completed_is_first_seen_at_zero(self):
        clock = FakeClock()

        sample = _watch(FakeBootedDevice(clock, dialog_from=0.0, dialog_until=100.0), clock)

        self.assertEqual(0.0, sample.first_dialog_seconds)
        self.assertEqual(["com.android.systemui"], sample.dialog_packages)
        self.assertEqual(5, sample.dialog_samples)

    def test_a_dialog_that_comes_up_mid_window_and_goes_still_counts_the_boot(self):
        clock = FakeClock()

        sample = _watch(FakeBootedDevice(clock, dialog_from=4.0, dialog_until=6.0), clock)

        self.assertEqual(4.0, sample.first_dialog_seconds)
        self.assertEqual(1, sample.dialog_samples)

    def test_the_hosts_load_is_kept_at_the_start_and_at_its_highest(self):
        clock = FakeClock()

        sample = _watch(FakeBootedDevice(clock), clock, loads=[0.4, 3.0, 9.5, 2.0, 1.0])

        self.assertEqual(0.4, sample.load_at_start)
        self.assertEqual(9.5, sample.load_peak)


class ReadLoadTest(unittest.TestCase):
    def test_reads_the_one_minute_figure(self):
        self.assertEqual(0.4, read_load("0.40 0.68 0.81 1/812 4021\n"))

    def test_a_line_that_is_not_loadavg_names_itself_and_the_shape(self):
        with self.assertRaises(ValueError) as raised:
            read_load("cannot open /proc/loadavg")

        self.assertIn("'cannot open /proc/loadavg'", str(raised.exception))
        self.assertIn("<1 min> <5 min> <15 min>", str(raised.exception))


class RequireRoomTest(unittest.TestCase):
    def test_room_above_the_floor_passes(self):
        require_room(lambda: 3_400_000_000, minimum_bytes=1_000_000_000)

    def test_room_at_the_floor_passes(self):
        require_room(lambda: 1_000_000_000, minimum_bytes=1_000_000_000)

    def test_room_under_the_floor_names_both_figures(self):
        with self.assertRaises(ValueError) as raised:
            require_room(lambda: 999_999_999, minimum_bytes=1_000_000_000)

        self.assertIn("999999999", str(raised.exception))
        self.assertIn("1000000000", str(raised.exception))


PM_LIST_DISABLED = "package:com.google.android.gms\r\npackage:com.android.vending\r\n"


class RequireDisabledTest(unittest.TestCase):
    def test_packages_all_listed_as_disabled_pass(self):
        require_disabled(PM_LIST_DISABLED, ["com.google.android.gms", "com.android.vending"])

    def test_a_package_that_is_not_listed_is_named_with_what_was_listed(self):
        with self.assertRaises(ValueError) as raised:
            require_disabled("package:com.android.vending\n", ["com.google.android.gms", "com.android.vending"])

        self.assertIn("'com.google.android.gms'", str(raised.exception))
        self.assertNotIn("'com.android.vending' is", str(raised.exception))
        self.assertIn("package:com.android.vending", str(raised.exception))

    def test_a_package_that_only_shares_a_prefix_is_not_the_one_disabled(self):
        with self.assertRaises(ValueError):
            require_disabled("package:com.google.android.gms.supervision\n", ["com.google.android.gms"])

    def test_an_empty_listing_fails_every_package_expected(self):
        with self.assertRaises(ValueError) as raised:
            require_disabled("", ["com.google.android.gms"])

        self.assertIn("com.google.android.gms", str(raised.exception))


PM_LIST_PACKAGES = "package:com.android.systemui\r\npackage:com.google.android.gms\r\npackage:com.android.vending\r\n"


class RequireAbsentTest(unittest.TestCase):
    def test_packages_the_image_does_not_ship_pass(self):
        require_absent("package:com.android.systemui\r\n", ["com.google.android.gms", "com.android.vending"])

    def test_a_package_the_image_ships_is_named_with_what_was_listed(self):
        with self.assertRaises(ValueError) as raised:
            require_absent(PM_LIST_PACKAGES, ["com.google.android.gms", "com.android.chrome"])

        self.assertIn("'com.google.android.gms'", str(raised.exception))
        self.assertNotIn("'com.android.chrome'", str(raised.exception))
        self.assertIn("package:com.android.systemui", str(raised.exception))

    def test_a_package_that_only_shares_a_prefix_is_not_the_one_present(self):
        require_absent("package:com.google.android.gms.supervision\n", ["com.google.android.gms"])

    def test_a_disabled_package_is_still_present(self):
        with self.assertRaises(ValueError):
            require_absent(PM_LIST_DISABLED, ["com.google.android.gms"])


class IsCleanBootTest(unittest.TestCase):
    def _line(self, dialog_samples: int) -> str:
        return boot_line(BootSample("settle", 1, 5, dialog_samples, None, [], 0.5, 1.0))

    def test_a_boot_that_never_showed_the_dialog_is_clean(self):
        self.assertTrue(is_clean_boot(self._line(0)))

    def test_a_boot_that_showed_the_dialog_once_is_not_clean(self):
        self.assertFalse(is_clean_boot(self._line(1)))

    def test_a_line_that_is_not_a_boot_names_itself_and_the_shape(self):
        with self.assertRaises(ValueError) as raised:
            is_clean_boot("not json")

        self.assertIn("'not json'", str(raised.exception))


class SummariseBootsTest(unittest.TestCase):
    def _line(self, candidate: str, boot: int, dialog_samples: int, load_peak: float = 1.0) -> str:
        first = 0.0 if dialog_samples else None
        return boot_line(BootSample(candidate, boot, 5, dialog_samples, first, ["a"], 0.5, load_peak))

    def test_counts_boots_and_boots_with_a_dialog_per_candidate(self):
        lines = [
            self._line("baseline", 1, 5),
            self._line("baseline", 2, 0),
            self._line("baseline", 3, 1),
            self._line("memory", 1, 0),
        ]

        summary = summarise_boots(lines)

        self.assertIn("baseline: 2 of 3 boots had a dialog", summary)
        self.assertIn("memory: 0 of 1 boots had a dialog", summary)

    def test_names_the_highest_host_load_beside_each_candidate(self):
        lines = [self._line("baseline", 1, 0, load_peak=2.5), self._line("baseline", 2, 0, load_peak=7.25)]

        self.assertIn("host load peaked at 7.25", summarise_boots(lines))

    def test_a_line_that_is_not_a_boot_names_itself_and_the_shape(self):
        with self.assertRaises(ValueError) as raised:
            summarise_boots(["not json"])

        self.assertIn("'not json'", str(raised.exception))
        self.assertIn("candidate", str(raised.exception))

    def test_boot_line_is_one_json_object(self):
        line = boot_line(BootSample("baseline", 1, 5, 0, None, [], 0.5, 1.0))

        self.assertEqual("baseline", json.loads(line)["candidate"])
        self.assertNotIn("\n", line)


if __name__ == "__main__":
    unittest.main()
