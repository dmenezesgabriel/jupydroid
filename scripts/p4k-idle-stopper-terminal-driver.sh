#!/usr/bin/env bash
# P4k gate: a live terminal keeps the server up through the idle stopper's grace, exactly as an
# in-flight cell already does (`p1c-app-death-backstop-driver.sh`'s own gate, the other half of
# the same `IdleServerStopper`).
#
#   1 background - a terminal is opened, a marker written to disk, then the app is backgrounded
#                  with nothing else running.
#   2 resume      - after the driver's own wait past the grace, a second marker proves the shell
#                   still answers, and a pixel comparison proves its scrollback is intact.
#
# **The evidence is the process and the disk, not the flow.** A green flow proves little on its
# own - the server could have already died and come back cold by the resume, and a fresh shell
# answers "echo" fine. Two things the driver reads directly, neither by asking Maestro to read
# this terminal's own text: `ps`, for `jupyter_server` itself (the same check
# `p1c-app-death-backstop-driver.sh` established, inverted - there the point was the server dying
# once idle, here it is the server *surviving* idle because a terminal is open); and each marker
# file, straight off the device's disk, the same move that driver makes for a notebook's answer.
#
# **Not `visible:`/`extendedWaitUntil` on this terminal's own content, anywhere in this driver's
# flows.** `architecture/cross-cutting.md` §8.6 forbids it: accessibility polling of this terminal's
# per-row text proved unreliable independent of anything `TerminalCanvas` draws, reproduced against
# an untouched build. A direct pixel-crop comparison (`terminal_content_extent.py`'s
# `terminal_top_strip_differs`) is what that entry names as the pattern the next terminal-content
# gate should follow - used here for *sameness* rather than difference (P4j-3d-3's own use of it),
# which needs neither a filter nor a bound either, and so is not thrown off by
# `terminal_content_bottom_y`'s own thin-glyph-stroke assumption: that oracle is tuned to `vi`'s
# isolated `~` fill lines, and this flow's own wrapped echo output instead stacks several rows with
# no vertical gap between them, which is a plain digit row's own shape that file already documents
# as taller than its filter allows.
#
# Portrait, forced - `p4i-terminal-resize-driver.sh`'s own `rotate_to`, for a measured reason: a
# device left in landscape puts `Terminal` behind `ListDetailSceneStrategy`'s two-pane layout, and
# every pixel bound below is measured against the single-pane, full-width shape portrait guarantees.
#
# The wait is a fixed sleep, not an observed condition - unlike P1c's cell, which persists an answer
# to disk the driver can poll for, nothing else here changes state while backgrounded to wait on.
# the gate text calls for 60s; `IDLE_STOP_GRACE` in `AppContainer` is 30s, so this clears it with
# margin rather than racing it.
set -euo pipefail

# TABLET ONLY (SM-X216B). The check below greps `ps -A` for a real `jupyter_server` process - on
# the x86_64 emulator MainActivity falls back to FakeTerminalGateway, which has no such process to
# find, so the gate would pass by having nothing to check rather than by the fix working.
#
# NOT A SUITE FLOW: the wait is ~60s of wall time and is the gate itself.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}
restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

EVIDENCE="$ROOT/evidence/p4k-idle-stopper-terminal"
mkdir -p "$EVIDENCE"
SERVER_LOG_PATH="files/spaces/base/run/jupyter_server.log"
# 30 s grace, and the gate's own wording ("background 60 s") rather than the grace plus slack -
# more margin than P1c's cell gate needed, on purpose: this step has no persisted-answer poll to
# fall back on if the timing were ever close.
IDLE_WAIT=60

# The server runs under the app as a proot tracee, the image's own python3 carrying
# `-m jupyter_server ...` on its command line. Grep'd out of `ps -A` rather than counted by name,
# because `ps` reports the executable, not the module - the same helper
# `p1c-app-death-backstop-driver.sh` uses.
server_pids() {
  "$ADB" shell "ps -A -o PID,CMDLINE | grep -- '-m jupyter_server' | grep -v grep" 2>/dev/null || true
}

app_pid() { "$ADB" shell pidof "$APP" 2>/dev/null | tr -d '\r'; }

# Fails loudly if the app was restarted - a new pid would make the `ps` check meaningless, exactly
# as it would for P1c's own gate.
same_app() {
  local now
  now="$(app_pid)"
  [ "$now" = "$APP_PID" ] && return 0
  echo "the app process changed ($APP_PID -> ${now:-gone}) at: $1" >&2
  exit 1
}

# The marker a terminal command wrote, read straight off the device's disk - not off the
# accessibility tree, for the reason both flows' own headers give.
marker_text() { # marker_text <filename>
  "$ADB" shell "run-as $APP sh -c 'cat \"$NOTEBOOK_DIR/$1\" 2>/dev/null'" | tr -d '\r\n'
}

# Whether the terminal's own content area differs between two captures, full-width and deep
# enough to hold every row this flow's marker commands draw, comfortably above where a soft
# keyboard or the extra-keys row would ever intrude - `terminal_content_extent.py`'s
# `terminal_top_strip_differs`, reused for *sameness* rather than difference.
content_unchanged() { # content_unchanged <before.png> <after.png>
  python3 -c "
import sys
sys.path.insert(0, '$ROOT/scripts')
from terminal_content_extent import terminal_top_strip_differs
changed = terminal_top_strip_differs(sys.argv[1], sys.argv[2], x_range=(0, 2000), strip_height=400)
print('changed' if changed else 'unchanged')
" "$1" "$2"
}

capture() { # capture <suffix>
  "$ADB" shell run-as "$APP" cat "$SERVER_LOG_PATH" >"$EVIDENCE/jupyter_server-$1.log" 2>/dev/null || true
  "$ADB" shell ps -A -o PID,PPID,RSS,CMDLINE >"$EVIDENCE/ps-$1.txt" 2>/dev/null || true
}

# The same clean base every sibling driver takes - an emptied workspace (part 1's own marker is
# the only file it should find) and a seeded `launcherSeen`, since at the portrait forced below a
# first run would land on the Launcher rather than the `Files` the flow asserts.
reset_notebooks

rotate_to 0 # portrait: single-pane, full-width, the shape every pixel bound above assumes.

# --- part 1: open a terminal, write a marker to disk, then background the app idle.
run_flow "$ROOT/.maestro/p4k-part1-terminal-background-idle.yaml" "$EVIDENCE" || exit 1
APP_PID="$(app_pid)"
[ -n "$APP_PID" ] || { echo "the app is not running after part 1"; exit 1; }
echo "app pid $APP_PID"

server_pids | grep -q . || { echo "jupyter_server is not running right after opening a terminal" >&2; exit 1; }
capture before-idle

BEFORE_MARKER="$(marker_text p4k-marker-before.txt)"
[ "$BEFORE_MARKER" = "p4k-before-idle" ] ||
  { echo "the pre-background marker never reached disk (read: '$BEFORE_MARKER')" >&2; exit 1; }
echo "marker on disk before backgrounding"

echo "waiting ${IDLE_WAIT}s for the grace to elapse with a terminal open and nothing else running..."
sleep "$IDLE_WAIT"
capture after-idle
same_app "after the idle wait"

if ! server_pids | grep -q .; then
  echo "jupyter_server is gone though a terminal was open - the idle stopper killed a live terminal" >&2
  echo "see $EVIDENCE/jupyter_server-after-idle.log and ps-after-idle.txt" >&2
  exit 1
fi
echo "jupyter_server survived the idle background with a terminal open:"
server_pids

# --- part 2: resume and prove the shell is still live, with its scrollback intact.
run_flow "$ROOT/.maestro/p4k-part2-terminal-reopen-after-idle.yaml" "$EVIDENCE" || exit 1
same_app "after part 2"
capture after-reopen

# Scrollback intact: the `read_buffer` replay must reproduce exactly what was on screen before
# backgrounding - the same pixels, since nothing has been typed yet in part 2 when this screenshot
# was taken.
SCROLLBACK="$(content_unchanged "$EVIDENCE/01-terminal-open-before-idle.png" "$EVIDENCE/03-resumed-before-typing.png")"
echo "terminal content area across the idle background: $SCROLLBACK"
[ "$SCROLLBACK" = "unchanged" ] || {
  echo "the terminal's own content changed across the idle background - the read_buffer replay" >&2
  echo "did not restore what was on screen before backgrounding" >&2
  exit 1
}

# Live shell: a *new* command, typed only now, must still be answered - stale pixels alone cannot
# prove that, which is why this is read off disk rather than off the screen.
AFTER_MARKER="$(marker_text p4k-marker-after.txt)"
[ "$AFTER_MARKER" = "p4k-after-idle" ] ||
  { echo "the post-resume marker never reached disk (read: '$AFTER_MARKER') - the shell is not live" >&2; exit 1; }

echo "P4k gate passed: a live terminal kept the server up through the idle stopper's grace, and the shell answered again with its scrollback intact."
