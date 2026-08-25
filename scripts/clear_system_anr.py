#!/usr/bin/env python3
"""Clears a *system* "isn't responding" dialog before a flow runs, and says when it did.

See `system_anr_dialog.py` for the incident this exists for. In short: an ANR dialog is modal,
nothing dismisses it, and every flow after the first one fails at 100% visibility - so a stuck
Google service reads as a screenful of app regressions.

**Only other people's dialogs.** A dialog belonging to the app under test is a defect to fix, and
this refuses to sweep it away: it reports it and exits non-zero so the driver stops rather than
retrying past it. Anything else is force-stopped, which is what makes the dialog go.

    scripts/clear_system_anr.py --serial emulator-5554 --app com.jupydroid.app --adb "$ADB"

Exit codes: 0 nothing was wrong or a foreign dialog was cleared; 2 the app under test is the one
not responding; 1 adb could not be reached.
"""

import argparse
import subprocess
import sys

from system_anr_dialog import anr_packages

# The service whose bind hangs on the Google emulator images, and the component that binds it.
# Force-stopping SystemUI alone leaves the client free to bind again within seconds, so both go.
_KNOWN_INSTIGATOR = "com.google.android.settings.intelligence"


def _adb(adb: str, serial: str, *args: str) -> str:
    command = [adb] + (["-s", serial] if serial else []) + list(args)
    done = subprocess.run(command, capture_output=True, text=True, timeout=120)
    return done.stdout


def main() -> int:
    parsed = _parser().parse_args()
    try:
        window = _adb(parsed.adb, parsed.serial, "shell", "dumpsys", "window")
    except (subprocess.SubprocessError, OSError) as failure:
        print(f"could not reach adb for '{parsed.serial}': {failure}", file=sys.stderr)
        return 1

    stuck = anr_packages(window)
    if not stuck:
        return 0
    if parsed.app in stuck:
        print(f"{parsed.app} is not responding - that is a defect, not a dialog to clear")
        return 2

    for package in stuck:
        print(f"clearing the '{package}' ANR dialog, which is not this app's")
        _adb(parsed.adb, parsed.serial, "shell", "am", "force-stop", _KNOWN_INSTIGATOR)
        _adb(parsed.adb, parsed.serial, "shell", "am", "force-stop", package)
    return 0


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
