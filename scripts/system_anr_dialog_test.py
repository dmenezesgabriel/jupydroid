"""The parser behind `clear_system_anr.py` - see that module for why it exists."""

import pathlib
import unittest

from system_anr_dialog import anr_packages, wait_button_center

# `dumpsys window` on the emulator at 10:41, with SystemUI's battery-health service stuck.
# Trimmed to the lines that matter; the full dump is thousands of lines.
STUCK = """
      Window #5: WindowStateAnimator{dc584ca Application Not Responding: com.android.systemui}
    numWindow=1 mCanAddInternalSystemWindow=true
  mCurrentFocus=Window{1ad3eea u0 Application Not Responding: com.android.systemui}
  mFocusedApp=ActivityRecord{76430346 u0 com.jupydroid.app/.MainActivity t12331}
"""

HEALTHY = """
      Window #3: WindowStateAnimator{8fbbdd1 com.jupydroid.app/com.jupydroid.app.MainActivity}
  mCurrentFocus=Window{8fbbdd1 u0 com.jupydroid.app/com.jupydroid.app.MainActivity}
  mFocusedApp=ActivityRecord{76430346 u0 com.jupydroid.app/.MainActivity t12331}
"""


class AnrPackagesTest(unittest.TestCase):
    def test_names_the_package_whose_dialog_is_up(self):
        self.assertEqual(["com.android.systemui"], anr_packages(STUCK))

    def test_a_healthy_screen_has_none(self):
        self.assertEqual([], anr_packages(HEALTHY))

    def test_an_empty_dump_has_none(self):
        self.assertEqual([], anr_packages(""))

    def test_the_same_package_is_reported_once_however_many_windows_it_has(self):
        twice = STUCK + STUCK
        self.assertEqual(["com.android.systemui"], anr_packages(twice))

    def test_two_stuck_apps_are_both_named(self):
        both = STUCK + "\n  mCurrentFocus=Window{9 u0 Application Not Responding: com.example.other}"
        self.assertEqual(["com.android.systemui", "com.example.other"], anr_packages(both))

    # **Our own app is reported like any other.** A JupyDroid ANR is a defect to fix, not a dialog
    # to sweep away, so the caller decides what to do about it - see `clear_system_anr.py`.
    def test_our_own_app_is_not_special_cased(self):
        ours = "  mCurrentFocus=Window{1 u0 Application Not Responding: com.jupydroid.app}"
        self.assertEqual(["com.jupydroid.app"], anr_packages(ours))


class WaitButtonCenterTest(unittest.TestCase):
    # The `uiautomator dump` of dev36's "System UI isn't responding" dialog at 320x640, taken with
    # the dialog holding focus for the whole of an instrumented run.
    REAL_DIALOG = (pathlib.Path(__file__).resolve().parent / "testdata" / "anr-dialog").joinpath(
        "system-ui-not-responding.xml"
    )

    def test_the_wait_button_of_the_real_dialog_is_pressed_at_its_centre(self):
        self.assertEqual((159, 370), wait_button_center(self.REAL_DIALOG.read_text()))

    def test_a_screen_without_the_dialog_has_no_button(self):
        self.assertIsNone(wait_button_center('<hierarchy><node resource-id="" bounds="[0,0][320,640]"/></hierarchy>'))

    def test_the_close_app_button_is_never_the_one_returned(self):
        close_only = '<node resource-id="android:id/aerr_close" bounds="[18,298][301,346]" />'
        self.assertIsNone(wait_button_center(close_only))

    def test_the_button_is_found_by_its_id_not_its_localized_label(self):
        localized = '<node text="Esperar" resource-id="android:id/aerr_wait" bounds="[0,100][100,200]" />'
        self.assertEqual((50, 150), wait_button_center(localized))


if __name__ == "__main__":
    unittest.main()
