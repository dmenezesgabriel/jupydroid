#!/usr/bin/env bash
# Sourced by the rotation drivers. Not executable on its own.
#
# What each driver does to the device and reads back from it: the turn that relaunches the
# activity, the app's pid that says the process was not relaunched with it, the tab strip's order
# as the window itself reports it, and where every named node sits. It sets "adb", the device's own
# adb command line.
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/rotation-reads.sh"
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
  "${adb[@]}" shell uiautomator dump /sdcard/rotation-window.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/rotation-window.xml | python3 -I -c '
import re, sys
tabs = re.findall(r"content-desc=\"Tab: ([^\"]*)\"[^>]*bounds=\"\[(\d+),", sys.stdin.read())
print(", ".join(title for title, _ in sorted(tabs, key=lambda tab: int(tab[1]))))'
}

# Every named node of the window, top to bottom in dp, and how many answer to "Settings", into
# [out]/window.xml and [out]/bounds.txt (`read_named_bounds.py`; SM-X216B is 240 dpi).
dump_named_bounds() { # dump_named_bounds <out-dir>
  "${adb[@]}" shell uiautomator dump /sdcard/named-bounds.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/named-bounds.xml >"$1/window.xml"
  "${adb[@]}" shell rm -f /sdcard/named-bounds.xml
  python3 -I "$ROOT_DIR/read_named_bounds.py" "$1/window.xml" 240 | tee "$1/bounds.txt"
}
