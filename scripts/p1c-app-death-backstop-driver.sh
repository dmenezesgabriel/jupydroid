#!/usr/bin/env bash
# P1c gate: the backstop stops the server when the app goes away - but only once nothing is
# still running, which is the rule that keeps a backgrounded computation alive. Four parts on
# one app process:
#
#   1 mid-run  - the cell (30 s sleep) is in flight when the app is backgrounded. The stopper
#                must not touch the server; the cell finishes in the background.
#   2 resume   - back inside the grace. The answer is already there and the pending stop is
#                cancelled, so the server is still alive.
#   3 idle     - backgrounded again with nothing running. Now the grace has nothing to reset it
#                and the jupyter_server process must be gone.
#   4 resume   - run the second cell. A fresh server, a fresh kernel, a new answer - without the
#                app having been restarted.
#
# **The evidence is the process, not the flow.** A green flow proves little here: the app could
# have been restarted between parts and every check would still pass. So the driver pins the app
# pid at part 1 and fails if it ever changes, and reads `ps` for the server itself.
#
# The waits are observed, not slept: part 1's cell is waited for *on disk* (the editor persists a
# run the moment it finishes), which both proves it completed while backgrounded and lands part 2
# inside the grace. The blind 40 s sleep this replaced was shorter than the cold start it had to
# cover, which is how the run of 2026-08-14 measured its own gate wrong.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flows assert `42` and `54`, which only a real kernel produces - on
# the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source
# back as stdout. And the whole point is a process dying and coming back, which the fake does not
# model.
#
# NOT A SUITE FLOW: the waits are ~2 minutes of wall time and are the gate itself.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p1c-backstop.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p1c-app-death-backstop"
mkdir -p "$EVIDENCE"
SERVER_LOG_PATH="files/spaces/base/run/jupyter_server.log"
NOTEBOOK="$NOTEBOOK_DIR/Backstop.ipynb"
# 30 s grace + 1 s poll + slack. `IDLE_STOP_GRACE` in AppContainer is the number this tracks.
IDLE_WAIT=45
CELL_WAIT=120

# The server is a child of the app process running as `libpython3.so -m jupyter_server ...`.
# Grep'd out of `ps -A` rather than counted by name, because `ps` reports the executable,
# not the module.
server_pids() {
  "$ADB" shell "ps -A -o PID,CMDLINE | grep -- '-m jupyter_server' | grep -v grep" 2>/dev/null || true
}

app_pid() { "$ADB" shell pidof "$APP" 2>/dev/null | tr -d '\r'; }

# Fails loudly if the app was restarted. Everything after part 1 is about one process surviving,
# so a new pid does not mean "the check failed" - it means the gate stopped measuring anything.
same_app() {
  local now
  now="$(app_pid)"
  [ "$now" = "$APP_PID" ] && return 0
  echo "the app process changed ($APP_PID -> ${now:-gone}) at: $1" >&2
  echo "every check after part 1 assumes one process; a restart makes them vacuous." >&2
  exit 1
}

# Waits for the cell's answer to reach the notebook on disk, which is what "the run finished"
# means from outside the app: the editor persists as soon as a run completes.
await_persisted_answer() {
  local started elapsed
  started="$SECONDS"
  while [ $((SECONDS - started)) -lt "$CELL_WAIT" ]; do
    if "$ADB" shell "run-as $APP sh -c 'cat \"$NOTEBOOK\"'" 2>/dev/null | grep -q '"42'; then
      elapsed=$((SECONDS - started))
      echo "the cell finished in the background after ${elapsed}s (answer on disk)"
      return 0
    fi
    sleep 2
  done
  echo "the backgrounded cell never wrote its answer within ${CELL_WAIT}s" >&2
  exit 1
}

capture() { # capture <suffix>
  "$ADB" shell run-as "$APP" cat "$SERVER_LOG_PATH" >"$EVIDENCE/jupyter_server-$1.log" 2>/dev/null || true
  "$ADB" shell ps -A -o PID,PPID,RSS,CMDLINE >"$EVIDENCE/ps-$1.txt" 2>/dev/null || true
}

reset_notebooks
seed_notebook "$FIXTURE" "Backstop"

# --- part 1: background the app mid-run.
run_flow "$ROOT/.maestro/p1c-part1-background-midrun.yaml" "$EVIDENCE" || exit 1
APP_PID="$(app_pid)"
[ -n "$APP_PID" ] || { echo "the app is not running after part 1"; exit 1; }
echo "app pid $APP_PID"

await_persisted_answer
capture after-midrun

# The mid-run background must not have stopped the server: a cell was running for the whole of
# it, and the resume in part 2 lands inside the grace.
if ! server_pids | grep -q .; then
  echo "jupyter_server is gone though a cell was mid-run - the backstop fired too early" >&2
  echo "see $EVIDENCE/jupyter_server-after-midrun.log and ps-after-midrun.txt" >&2
  exit 1
fi
echo "server alive after the mid-run background:"
server_pids
same_app "after the mid-run wait"

# --- part 2: resume inside the grace and read the answer the background produced.
run_flow "$ROOT/.maestro/p1c-part2-reopen-after-midrun.yaml" "$EVIDENCE" || exit 1
same_app "after part 2"
server_pids | grep -q . || { echo "the resume did not cancel the pending stop - server gone" >&2; exit 1; }
echo "server survived the mid-run background and the resume cancelled the pending stop"

# --- part 3: background it idle. Nothing runs, so the grace expires and the server stops.
run_flow "$ROOT/.maestro/p1c-part3-background-idle.yaml" "$EVIDENCE" || exit 1
echo "waiting ${IDLE_WAIT}s for the grace to expire with nothing running..."
sleep "$IDLE_WAIT"
capture after-idle
same_app "after the idle wait"

if server_pids | grep -q .; then
  echo "jupyter_server is still alive after the idle background - the backstop never fired:" >&2
  server_pids >&2
  echo "see $EVIDENCE/jupyter_server-after-idle.log and ps-after-idle.txt" >&2
  exit 1
fi
echo "jupyter_server is gone after the idle background, and the app is still the same process"

# --- part 4: run again in that same process. The session it holds points at a server that no
# longer exists, so this is where a stale session shows up if it is not handled.
RESUMED_AT="$SECONDS"
run_flow "$ROOT/.maestro/p1c-part4-reopen-after-stop.yaml" "$EVIDENCE" || exit 1
echo "resume to answer: $((SECONDS - RESUMED_AT))s (the cost of having stopped the server)"
same_app "after part 4"
capture after-reopen

# --- and the one thing no code can cover: Android killing the app outright. Measured rather
# than assumed, because "app-death backstop" is what P1c is called.
"$ADB" shell am force-stop "$APP"
sleep 2
{
  echo "# does the server outlive the app process?"
  echo "app pid before force-stop: $APP_PID"
  echo "jupyter_server after 'am force-stop $APP':"
  server_pids
  echo "(empty above means the server died with its parent and needs no backstop of its own)"
} >"$EVIDENCE/orphan-after-force-stop.txt"
cat "$EVIDENCE/orphan-after-force-stop.txt"

echo "P1c gate passed: a cell mid-run survived the app going away, an idle app's server stopped after the grace, and the same app process got a kernel back."
