#!/usr/bin/env bash
# An open document reloads from disk: a cell in writer.ipynb rewrites notes.py and target.ipynb
# while both are open, and each tab shows the cell's version only once the strip's reload icon is
# tapped. With autosave off, a typed edit to notes.py makes the reload ask first; Cancel keeps it,
# Reload drops it. The disk is read afterwards: neither the question nor the reload may have
# written anything, so notes.py must still be exactly what the cell wrote. The strip is measured
# at two panes and at one, with the reload icon beside the save icon.
#
#   scripts/jd-038-tab-reload-t1-driver.sh
#
# Runs a cell, so it belongs on the arm64 tablet: on x86_64 the fake gateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-038-tab-reload/t1"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

# The strip as the window reports it: every tab, close slot, Up arrow and trailing icon, left to
# right, with its bounds. Appended to strip.txt under a heading.
measure_strip() { # measure_strip <heading>
  "${adb[@]}" shell uiautomator dump /sdcard/jd-038-window.xml >/dev/null
  {
    echo "== $1 (window $("${adb[@]}" shell am get-config | tr -d '\r' | grep -m1 -oE 'w[0-9]+dp-h[0-9]+dp'))"
    "${adb[@]}" exec-out cat /sdcard/jd-038-window.xml | python3 -I -c '
import re, sys
strip = re.compile(r"^(Tab strip$|Tab: |Close tab: |Save tab: |Reload tab: |Back to )")
nodes = []
for node in re.findall(r"<node [^>]*>", sys.stdin.read()):
    name = re.search(r"content-desc=\"([^\"]*)\"", node).group(1)
    if not strip.match(name):
        continue
    left, top, right, bottom = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node).groups())
    enabled = re.search(r" enabled=\"(\w+)\"", node).group(1)
    nodes.append((left, f"{name:<28} x {left}-{right} ({right - left}px)  y {top}-{bottom} ({bottom - top}px)  enabled={enabled}"))
print("\n".join(line for _, line in sorted(nodes)))'
  } | tee -a "$EVIDENCE/strip.txt"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/strip.txt" "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" notes.py
seed_notebook "$ROOT/.maestro/fixtures/jd-038-target.ipynb" target
seed_notebook "$ROOT/.maestro/fixtures/jd-038-writer.ipynb" writer
rotate_to 1

run_flow "$ROOT/.maestro/jd-038-tab-reload-t1.yaml" "$EVIDENCE/reload"
measure_strip "two panes, target.ipynb on top"
rotate_to 0
measure_strip "one pane, target.ipynb on top"

notes="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/notes.py'" | tr -d '\r')"
echo "notes.py on disk: $notes" | tee "$EVIDENCE/disk.txt"
[[ "$notes" == 'notes = "after_42"' ]] || fail "expected notes.py as the cell wrote it, found: $notes"
"${adb[@]}" shell "run-as $APP grep -qF reloaded_42 '$NOTEBOOK_DIR/target.ipynb'" ||
  fail "target.ipynb no longer holds the cell the kernel wrote"
echo "target.ipynb on disk: holds reloaded_42" | tee -a "$EVIDENCE/disk.txt"
