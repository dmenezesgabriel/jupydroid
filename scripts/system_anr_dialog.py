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
