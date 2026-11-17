#!/usr/bin/env python3
"""Clears a *system* "isn't responding" dialog before a flow runs, and says when it did.

See `system_anr_dialog.py` for the incident this exists for. In short: an ANR dialog is modal,
nothing dismisses it, and every flow after the first one fails at 100% visibility - so a stuck
Google service reads as a screenful of app regressions.

**Only other people's dialogs.** A dialog belonging to the app under test is a defect to fix, and
this refuses to sweep it away: it reports it and exits non-zero so the driver stops rather than
retrying past it. Anything else gets "Wait" pressed, once per dialog on screen: SystemUI's dialog held
window focus for a whole instrumented run on dev36, a force-stop never removed it in any run, and a
press did.

    scripts/clear_system_anr.py --serial emulator-5554 --app com.jupydroid.app --adb "$ADB"

Exit codes: 0 nothing was wrong or a foreign dialog was cleared; 2 the app under test is the one
not responding; 1 adb could not be reached, or a foreign dialog is still up after the presses.
"""

import argparse
import subprocess
import sys
import time
from collections.abc import Callable

from system_anr_dialog import anr_packages, wait_button_center

# What the window manager is given to drop a dialog after a press; the probe that found a press
# working read the screen five seconds after it.
_SETTLE_SECONDS = 3

# Two dialogs stacked on dev36 (SystemUI's and the launcher's), one press each.
_MOST_PRESSES = 3


def _adb(adb: str, serial: str, *args: str) -> str:
    command = [adb] + (["-s", serial] if serial else []) + list(args)
    done = subprocess.run(command, capture_output=True, text=True, timeout=120)
    return done.stdout


def tap_wait_button(adb: Callable[..., str]) -> bool:
    """Presses "Wait" on the dialog on screen, if there is one.

    SystemUI's dialog held window focus for a whole instrumented run on dev36, so every Espresso
    call that needs the root's focus failed; this press removed it.

    Args:
        adb: runs one adb command (`adb("shell", "input", "tap", "1", "2")`) and returns its stdout.

    Returns:
        True when a button was found and pressed.
    """
    # `exec-out`, not `shell`: through `shell` the dump goes to the tty and only a one-line
    # notice comes back (clear_system_anr_test.FakeAdb).
    centre = wait_button_center(adb("exec-out", "uiautomator", "dump", "/dev/tty"))
    if centre is None:
        return False
    adb("shell", "input", "tap", str(centre[0]), str(centre[1]))
    return True


def clear_dialogs(
    adb: Callable[..., str], sleep: Callable[[float], None] = time.sleep, presses: int = _MOST_PRESSES
) -> list[str]:
    """Presses "Wait" until no "isn't responding" dialog is on screen, or `presses` run out.

    Example: `remaining = clear_dialogs(run)`, then a non-empty `remaining` is the packages to report.

    Args:
        adb: runs one adb command and returns its stdout, as for `tap_wait_button`.
        sleep: waits for the window manager to drop the dialog; injected so a test does not wait.
        presses: the most presses, so a dialog that ignores them cannot hold this forever.

    Returns:
        The packages whose dialog is still up; empty when the screen is clear.
    """
    for _ in range(presses):
        if not anr_packages(adb("shell", "dumpsys", "window")):
            return []
        tap_wait_button(adb)
        sleep(_SETTLE_SECONDS)
    return anr_packages(adb("shell", "dumpsys", "window"))


def _clear(parsed: argparse.Namespace, stuck: list[str]) -> int:
    print(f"pressing Wait on the {stuck} ANR dialog, which is not this app's")
    remaining = clear_dialogs(lambda *args: _adb(parsed.adb, parsed.serial, *args))
    if remaining:
        print(f"the {remaining} ANR dialog is still up after {_MOST_PRESSES} presses on Wait")
    return 1 if remaining else 0


def main() -> int:
    parsed = _parser().parse_args()
    try:
        window = _adb(parsed.adb, parsed.serial, "shell", "dumpsys", "window")
    except (subprocess.SubprocessError, OSError) as failure:
        print(f"could not reach adb for '{parsed.serial}': {failure}", file=sys.stderr)
        return 1

    stuck = anr_packages(window)
    if parsed.app in stuck:
        print(f"{parsed.app} is not responding - that is a defect, not a dialog to clear")
        return 2
    return _clear(parsed, stuck) if stuck else 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="", help="the device to look at; empty uses ANDROID_SERIAL")
    # Passed in rather than looked up, because `maestro-run.sh` already resolves adb and a second
    # rule here could disagree with it - a driver runs with adb off PATH more often than on it.
    parser.add_argument("--adb", default="adb", help="the adb binary, as the caller resolved it")
    parser.add_argument("--app", required=True, help="the app under test, whose dialog is never cleared")
    return parser


if __name__ == "__main__":
    sys.exit(main())
