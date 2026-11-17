"""Finding the "isn't responding" dialogs that make a flow fail for someone else's reason.

An ANR dialog is modal and covers whatever is behind it, so every `assertVisible` at the
default 100% visibility fails while one is up - and nothing dismisses it. One stuck service
therefore fails not one flow but every flow after it.

That is not hypothetical. On 2026-08-25 `com.google.android.settings.intelligence` bound
`com.android.systemui/...batteryhealth.HealthService`, the service never returned its binder
(`binder=null`, `requested=true received=false` for 19 minutes), SystemUI ANR'd, and the dialog
sat there for the rest of the sweep: ten flows failed, nine of them purely because the dialog
was on top of a screen that was otherwise correct. The frames show the app rendering perfectly
behind it. Clearing the dialog and re-running made all nine pass unchanged.

The parsing lives apart from the adb call so it can be tested without a device.
"""

import re

# `dumpsys window` names the dialog in both the window list and `mCurrentFocus`; the package is
# what follows the localized-but-stable "Application Not Responding: " marker the framework uses
# for these windows.
_ANR_WINDOW = re.compile(r"Application Not Responding:\s*([A-Za-z0-9_.]+)")

# The framework's "Wait" button. Found by id: its label is localized, its id is not.
_WAIT_BUTTON = re.compile(r'resource-id="android:id/aerr_wait"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def anr_packages(dumpsys_window: str) -> list[str]:
    """The packages showing an "isn't responding" dialog, in the order they first appear.

    Every occurrence counts, not only `mCurrentFocus`: the dialog blocks what is behind it
    whether or not it currently holds input focus, and blocking is what breaks a flow.

    >>> anr_packages("mCurrentFocus=Window{1 u0 Application Not Responding: com.android.systemui}")
    ['com.android.systemui']

    Args:
        dumpsys_window: the output of `adb shell dumpsys window`.

    Returns:
        Each package once, first-seen order. Empty when the screen is clear.
    """
    seen: list[str] = []
    for package in _ANR_WINDOW.findall(dumpsys_window):
        if package not in seen:
            seen.append(package)
    return seen


def wait_button_center(ui_dump: str) -> tuple[int, int] | None:
    """Where to press "Wait" on an "isn't responding" dialog, in screen pixels.

    A force-stop does not remove the dialog on dev36: it held window focus for a whole
    instrumented run after one (`clear_system_anr.py`), and one press on "Wait" removed it.

    >>> wait_button_center('<node resource-id="android:id/aerr_wait" bounds="[0,100][100,200]" />')
    (50, 150)

    Args:
        ui_dump: the output of `adb shell uiautomator dump`.

    Returns:
        The centre of the button, or None when no such dialog is on screen.
    """
    found = _WAIT_BUTTON.search(ui_dump)
    if found is None:
        return None
    left, top, right, bottom = (int(edge) for edge in found.groups())
    return (left + right) // 2, (top + bottom) // 2
