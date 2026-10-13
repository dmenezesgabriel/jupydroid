#!/usr/bin/env bash
# Sourced by the jd-030-rotation drivers. Not executable on its own.
#
# What each driver does to the device and reads back from it: the turn that relaunches the
# activity, the app's pid that says the process was not relaunched with it, and the tab strip's
# order as the window itself reports it. It sets "adb", the device's own adb command line.
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/jd-030-rotation-reads.sh"
#   trap restore_rotation EXIT
#   rotate_to 0

mapfile -t adb < <(device_adb)

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "${adb[@]}" shell settings put system accelerometer_rotation 0
  "${adb[@]}" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "${adb[@]}" shell settings put system accelerometer_rotation 1; }

app_pid() { "${adb[@]}" shell pidof "$APP" | tr -d '\r'; }

# The strip's tabs, left to right by their bounds in the window - "Tab: <title>" is each one's
# accessible name, so this is the order a screen reader would walk them in too.
tab_order() {
  "${adb[@]}" shell uiautomator dump /sdcard/jd-030-window.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/jd-030-window.xml | python3 -I -c '
import re, sys
tabs = re.findall(r"content-desc=\"Tab: ([^\"]*)\"[^>]*bounds=\"\[(\d+),", sys.stdin.read())
print(", ".join(title for title, _ in sorted(tabs, key=lambda tab: int(tab[1]))))'
}
