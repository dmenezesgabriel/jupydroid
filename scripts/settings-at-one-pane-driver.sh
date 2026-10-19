#!/usr/bin/env bash
# At one pane: where the bottom bar's five items sit and how wide each is, how much of each panel's
# own bar is free, and how many nodes answer to "Settings", read from the window's hierarchy on
# Sessions and Tabs, Files and Search in turn. Pinned to portrait, which is one pane on SM-X216B,
# and restored to auto-rotate on exit. Bounds are in px; SM-X216B draws 1.5 px to the dp (240 dpi).
#
# Runs on either device - nothing here touches a kernel - but only a one-pane window has the bar.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"   # rotate_to, restore_rotation, "adb"

OUT="$ROOT/evidence/settings-at-one-pane"

trap restore_rotation EXIT
rotate_to 0
run_flow "$ROOT/.maestro/settings-at-one-pane.yaml" "$OUT" || exit 1

# The window's hierarchy as it stands, saved under the panel's name.
dump_window() {
  "${adb[@]}" shell uiautomator dump /sdcard/settings-at-one-pane.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/settings-at-one-pane.xml >"$OUT/$1.xml"
  "${adb[@]}" shell rm -f /sdcard/settings-at-one-pane.xml
}

# Taps the bar item named $1 at the centre of its bounds in $2's dump.
tap_item() {
  read -r x y < <(python3 -I -c '
import re, sys
l, t, r, b = map(int, re.search(r"content-desc=\"" + sys.argv[1] + r"\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",
                                open(sys.argv[2]).read()).groups())
print((l + r) // 2, (t + b) // 2)' "$1" "$2")
  "${adb[@]}" shell input tap "$x" "$y"
  sleep 2
}

dump_window sessions
tap_item "Files" "$OUT/sessions.xml"
dump_window files
tap_item "Search" "$OUT/files.xml"
dump_window search

# Per panel: every node with a name, top to bottom, in dp - the bar's five items, the panel's own
# bar and whatever answers to "Settings" are all among them.
python3 -I -c '
import re, sys
for path in sys.argv[1:]:
    xml = open(path).read()
    print("==", path.rsplit("/", 1)[-1])
    named = re.findall(r"(?:text|content-desc)=\"([^\"]+)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", xml)
    for name, *px in sorted(set(named), key=lambda n: (int(n[2]), int(n[1]))):
        l, t, r, b = (round(int(v) / 1.5) for v in px)
        print(f"  {name[:40]:40} x {l}-{r} ({r - l}dp)  y {t}-{b} ({b - t}dp)")
    print("  nodes named Settings:", len(re.findall(r"=\"Settings\"", xml)))' \
  "$OUT"/sessions.xml "$OUT"/files.xml "$OUT"/search.xml | tee "$OUT/bounds.txt"
