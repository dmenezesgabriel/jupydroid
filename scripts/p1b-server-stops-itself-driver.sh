#!/usr/bin/env bash
# P1b gate: the server stops itself. A notebook is opened, a cell runs against the real
# kernel, and the notebook is closed again - leaving a live server and an idle kernel whose
# session was never deleted (that is P1d). The server's own machinery must then tear both
# down: MappingKernelManager culls the idle kernel after cull_idle_timeout (300 s, polled
# every cull_interval=60 s), and once no kernels exist the server stops after
# shutdown_no_activity_timeout (300 s, checked every 60 s). The driver waits through the
# whole thing, asserts the process is gone, and then reopens the notebook and runs a cell
# again - a cold server, a fresh kernel, the same answer.
#
# Evidence is the server's own account (evidence/p1b-server-stops-itself/jupyter_server.log):
# a "Culling ... kernel" warning and a "No kernels for N seconds; shutting down" info line.
# On a passing run both must be present; without them the process could have died for any
# reason and the gate would be green for the wrong one.
set -euo pipefail

# TABLET ONLY (SM-X216B). Both flows assert `42`, which only a real kernel produces - on the
# x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source
# back as stdout. The whole point of the step is a process dying and coming back, which the
# fake does not model.
# NOT A SUITE FLOW: an idle wait of 840 s is the gate itself, and a sweep that ran it every
# time would add sixteen minutes to the tablet shard for a behaviour (server config) that
# changes once a phase. It is a step-level gate, run and evidenced explicitly.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p1-lifecycle.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p1b-server-stops-itself"
mkdir -p "$EVIDENCE"
SERVER_LOG_PATH="files/spaces/base/run/jupyter_server.log"

# The server is a child of the app process running as `libpython3.so -m jupyter_server ...`;
# the kernel is `libpython3.so -m jupydroid_kernel ...`. Both are grep'd out of `ps -A`
# rather than counted by name, because `ps` reports the executable, not the module.
server_pids() {
  "$ADB" shell "ps -A -o PID,CMDLINE | grep -- '-m jupyter_server' | grep -v grep" 2>/dev/null || true
}

reset_notebooks
seed_notebook "$FIXTURE" "Lifecycle"

# --- part 1: open, run, close. The kernel socket is closed by the back press, the session
# stays on the server, and the idle clock starts at the cell's completion.
run_flow "$ROOT/.maestro/p1b-part1-open-and-close.yaml" "$EVIDENCE" || exit 1
"$ADB" shell run-as "$APP" cat "$SERVER_LOG_PATH" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true
"$ADB" shell ps -A -o PID,USER,NAME,CMDLINE >"$EVIDENCE/ps-after-close.txt" 2>/dev/null || true

# The flow went green, so a server must be up now. Fail loudly if it is not - a gate that
# asserts nothing was running would prove nothing about stopping.
if ! server_pids | grep -q .; then
  echo "no jupyter_server process after part 1 - the flow never started one" >&2
  exit 1
fi
echo "server alive after closing the notebook (cull_idle_timeout + shutdown_no_activity_timeout counting down)"
server_pids

# --- the wait. Worst case from the cell's completion: 300 s idle + 60 s cull poll + 300 s
# shutdown + 60 s shutdown poll = 720 s; 840 s leaves a minute of slack and a readable log.
echo "waiting 840 s for the server to cull its idle kernel and stop itself..."
sleep 840

"$ADB" shell run-as "$APP" cat "$SERVER_LOG_PATH" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true
"$ADB" shell ps -A -o PID,USER,NAME,CMDLINE >"$EVIDENCE/ps-after-idle.txt" 2>/dev/null || true

# The process must be gone.
if server_pids | grep -q .; then
  echo "jupyter_server is still alive after the idle wait:" >&2
  server_pids >&2
  echo "see $EVIDENCE/jupyter_server.log and ps-after-idle.txt" >&2
  exit 1
fi
echo "jupyter_server is gone after the idle wait"

# And the server must say it culled the kernel and stopped for inactivity - the two lines
# that distinguish "stopped itself" from "died for another reason".
if ! grep -q "Culling '" "$EVIDENCE/jupyter_server.log"; then
  echo "the server never logged culling its idle kernel - see jupyter_server.log" >&2
  exit 1
fi
if ! grep -q "No kernels for .* seconds; shutting down" "$EVIDENCE/jupyter_server.log"; then
  echo "the server never logged a no-activity shutdown - see jupyter_server.log" >&2
  exit 1
fi

# --- part 2: reopen and run again. The answer must come back, from a cold server.
run_flow "$ROOT/.maestro/p1b-part2-reopen.yaml" "$EVIDENCE" || exit 1
"$ADB" shell run-as "$APP" cat "$SERVER_LOG_PATH" >"$EVIDENCE/jupyter_server-after-reopen.log" 2>/dev/null || true
"$ADB" shell ps -A -o PID,USER,NAME,CMDLINE >"$EVIDENCE/ps-after-reopen.txt" 2>/dev/null || true

echo "P1b gate passed: the server stopped itself after an idle with no kernels, and a reopened notebook got a kernel back."
