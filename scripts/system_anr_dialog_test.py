"""The parser behind `clear_system_anr.py` - see that module for why it exists."""

import unittest

from system_anr_dialog import anr_packages

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


if __name__ == "__main__":
    unittest.main()
