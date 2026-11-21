#!/usr/bin/env bash
# The Source Control panel says what is happening in every state on a real Space: not a
# repository with a Retry that reads again, a repository's branch once the explorer browses
# into it, and a failed read - named, with a Retry that recovers.
#
#   nice -n 10 timeout 1800 scripts/git-panel-states-driver.sh
#
# The failed read is made by SIGSTOPping base's proot, not by `POST /api/shutdown`: shutdown
# exits the process, and `SpaceServer.connection` starts a dead server again on the next use
# (scripts/ai-chat-reconnects-driver.sh measures the new pid), so the panel's read would meet
# a restart, not a failure. A signal to a tracee is mediated by its tracer and never lands -
# SIGSTOP on the python left it `S` - but stopping the tracer itself freezes every tracee at
# its next ptrace stop: the server, its kernels and shells all stop together while
# `Process.isAlive` still says the Space runs, so `connection` hands out the cached port and
# the panel's POST waits out OkHttp's read timeout into a GitStatusException.
# SIGCONT then resumes the same server, and the Retry reads `demo`'s branch back - nothing
# restarted.
#
# Exits non-zero if any flow fails or the Space's proot cannot be found or signalled.
#
# TABLET ONLY (SM-X216B): the panel asks a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/git-panel-states"

mapfile -t DEVICE_ADB < <(device_adb)

# The pid of the proot tracing base's jupyter_server - the one process whose stop freezes the
# whole Space, every tracee blocking at its next ptrace stop until the tracer runs again.
# pylsp's proot is a different tracee command, so it does not match.
proot_pid() {
  "${adb[@]}" shell "ps -A -o PID,CMDLINE | tr -d '\r' |
    grep 'libproot.so .*-m jupyter_server' | grep -- 'files/spaces/base ' | grep -v grep |
    awk '{print \$1; exit}'"
}

# The Space's processes with their states, for the evidence a freeze and a resume leave:
# the tracer, the server it traces and the kernels under it.
space_processes() {
  {
    "${adb[@]}" shell "ps -A -o PID,STAT,CMDLINE | tr -d '\r' |
      grep 'libproot.so .*-m jupyter_server' | grep -- 'files/spaces/base ' | grep -v grep"
    "${adb[@]}" shell "ps -A -o PID,STAT,CMDLINE | tr -d '\r' |
      grep -- '-m jupyter_server ' | grep -v 'libproot.so' | grep -v grep"
    "${adb[@]}" shell "ps -A -o PID,STAT,CMDLINE | tr -d '\r' |
      grep -- '-m jupydroid_kernel' | grep -v 'libproot.so' | grep -v grep"
  }
}

# Stops base's proot and proves the freeze took: the server reads `t` - a tracee stopped
# inside ptrace waits on its tracer - while the Space still counts as running.
freeze_space() {
  local pid
  pid="$(proot_pid)"
  [ -n "$pid" ] || { echo "no proot tracing base's jupyter_server - the Space never started" >&2; return 1; }
  echo "base's proot pid $pid"
  space_processes | tee "$EVIDENCE/ps-before-freeze.txt"
  "${adb[@]}" shell "run-as $APP kill -STOP $pid" || return 1
  sleep 2
  space_processes | tee "$EVIDENCE/ps-frozen.txt"
  space_processes | grep -- '-m jupyter_server ' | grep -v 'libproot.so' | grep -Eq ' [Tt] ' ||
    { echo "jupyter_server did not freeze under its stopped proot" >&2; return 1; }
}

# CONTs the proot freeze_space stopped, so the panel's Retry meets the same live server.
resume_space() {
  local pid
  pid="$(proot_pid)"
  [ -n "$pid" ] || { echo "base's proot exited while frozen - nothing to resume" >&2; return 1; }
  "${adb[@]}" shell "run-as $APP kill -CONT $pid" || return 1
  sleep 2
  space_processes | tee "$EVIDENCE/ps-resumed.txt"
}

# What the screen holds after each part, kept beside the flows' own screenshots.
dump_screen() { # dump_screen <name>
  "${adb[@]}" shell "uiautomator dump /sdcard/git-panel-states-$1.xml" >/dev/null
  "${adb[@]}" exec-out cat "/sdcard/git-panel-states-$1.xml" >"$EVIDENCE/$1.xml"
  "${adb[@]}" shell rm "/sdcard/git-panel-states-$1.xml"
}

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
# Empties the workspace, `demo/` with it, so `git init` makes the repository afresh.
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/git-panel-states-part1-root-and-demo.yaml" "$EVIDENCE" || exit 1
dump_screen part1-root-and-demo

freeze_space || exit 1
run_flow "$ROOT/.maestro/git-panel-states-part2-server-frozen.yaml" "$EVIDENCE" || exit 1
dump_screen part2-server-frozen

resume_space || exit 1
run_flow "$ROOT/.maestro/git-panel-states-part3-retry.yaml" "$EVIDENCE" || exit 1
dump_screen part3-retry

# The repository the terminal made, read back off the disk rather than believed from the echo.
"${adb[@]}" shell "run-as $APP cat $NOTEBOOK_DIR/demo/.git/HEAD" >"$EVIDENCE/demo-git-head.txt" 2>/dev/null ||
  echo "demo/.git/HEAD unreadable" >"$EVIDENCE/demo-git-head.txt"
pull_server_log "$EVIDENCE/jupyter_server.log"

echo "git-panel-states passed: not-a-repository, the repository's branch, the failed read and its Retry, all read on the tablet"
