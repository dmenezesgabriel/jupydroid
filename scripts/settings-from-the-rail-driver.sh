#!/usr/bin/env bash
# Beside two panes, Settings opens from the gear at the activity rail's foot with Search, then
# Sessions and Tabs, showing. Pinned to landscape, which is two panes on SM-X216B, and restored to
# auto-rotate on exit. After the flow the window's hierarchy is read: how many nodes answer to
# "Settings" (one - the Files bar draws no gear beside two panes), and where the gear sits against
# the `+` and the window's foot.
#
# Runs on either device - nothing here touches a kernel - but only a two-pane window has the rail.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"   # rotate_to, restore_rotation, "adb"

OUT="$ROOT/evidence/settings-from-the-rail"

trap restore_rotation EXIT
rotate_to 1
run_flow "$ROOT/.maestro/settings-from-the-rail.yaml" "$OUT" || exit 1

"${adb[@]}" shell uiautomator dump /sdcard/settings-rail.xml >/dev/null
"${adb[@]}" exec-out cat /sdcard/settings-rail.xml | python3 -I -c '
import re, sys
xml = sys.stdin.read()
def bounds(desc):
    return re.findall(r"content-desc=\"" + desc + r"\"[^>]*bounds=\"(\[[^\"]*\])\"", xml)
window = re.search(r"<node [^>]*bounds=\"(\[[^\"]*\])\"", xml).group(1)
print("window", window)
print("nodes named Settings:", len(bounds("Settings")), bounds("Settings"))
print("New Launcher:", bounds("New Launcher"))' | tee "$OUT/bounds.txt"
"${adb[@]}" shell rm -f /sdcard/settings-rail.xml
