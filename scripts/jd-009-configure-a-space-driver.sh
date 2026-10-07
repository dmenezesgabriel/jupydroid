#!/usr/bin/env bash
# JD-009: the item's whole demo on one tablet run. Thesis is created and both rows offer Settings
# until base runs; Thesis's Settings takes idle 2 min, GREETING=hello, a refused HOME, a thread cap
# of 1 and a startup script, and reads them back after a kill. Run, Thesis's explorer lists what
# the script wrote, a kernel reads GREETING, numpy starts no workers, and a terminal reads GREETING
# too. Its terminal shut down and the app left on the home, Thesis stops itself while base runs
# on; base, uncapped, starts its own workers; and a script that exits 3 refuses Thesis's start.
#
#   scripts/jd-009-configure-a-space-driver.sh
#
# TABLET ONLY (SM-X216B): cells run, the script runs under the real proot, and on x86_64 the app
# falls back to FakeKernelGateway, which echoes the cell's source back.
#
# NOT A SUITE FLOW: it removes every user Space on the device, and base's config.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-009-configure-a-space/demo}"
FLOWS="$ROOT/.maestro/jd-009-configure-a-space"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

thesis_file() { "$ADB" shell run-as "$APP" cat "$THESIS/$1"; }

# The two idle timers of the server under files/spaces/<id>, and nothing else of its command line:
# the line also carries the server's token (JupyterServerLauncher), which must not reach evidence.
timers_of() {
  "$ADB" shell "ps -A -o CMDLINE" | grep 'libproot.so .*-m jupyter_server' | grep -- "files/spaces/$1 " |
    grep -o -- '--ServerApp.shutdown_no_activity_timeout=[0-9]*\|--MappingKernelManager.cull_idle_timeout=[0-9]*' || true
}

expect_timers() { # expect_timers <space-id> <seconds>
  local expected seen
  expected="$(printf -- '--ServerApp.shutdown_no_activity_timeout=%s\n--MappingKernelManager.cull_idle_timeout=%s' "$2" "$2")"
  seen="$(timers_of "$1")"
  [ "$seen" = "$expected" ] || { echo "expected $1's timers at $2 s, saw: $seen" >&2; exit 1; }
}

# The environment the Space's server was launched with, one NAME=value per line: the proot tracer
# is the app's own uid, so run-as reads its environ, which kernels and terminals inherit.
server_env() { # server_env <space-id>
  local pid
  pid=$("$ADB" shell "ps -A -o PID,CMDLINE" | tr -d '\r' |
    grep 'libproot.so .*-m jupyter_server' | grep -- "files/spaces/$1 " | awk '{print $1}' | sed -n 1p)
  [ -n "$pid" ] || { echo "no server under $1" >&2; return 1; }
  "$ADB" shell "run-as $APP cat /proc/$pid/environ" | tr '\0' '\n'
}

expect_server_env() { # expect_server_env <space-id> <NAME=value>
  server_env "$1" | grep -qx "$2" || { echo "expected '$2' in $1's server environment" >&2; exit 1; }
}

# The one running kernel's pid. A kernel is a proot tracee, so it has no libproot.so line of its
# own; its cmdline is the interpreter's. Called after a relaunch, when one kernel is all there is.
kernel_pid() {
  local pids
  pids=$("$ADB" shell "ps -A -o PID,CMDLINE" | tr -d '\r' |
    grep -- '-m jupydroid_kernel' | grep -v 'libproot.so' | awk '{print $1}')
  [ "$(echo "$pids" | grep -c .)" = 1 ] || { echo "expected one kernel, found: '$pids'" >&2; return 1; }
  echo "$pids"
}

kernel_caps() { "$ADB" shell "run-as $APP cat /proc/$1/environ" | tr '\0' '\n' | grep '_NUM_THREADS=' || echo none; }

kernel_threads() { "$ADB" shell "run-as $APP cat /proc/$1/status" | tr -d '\r' | grep '^Threads:'; }

# Every field the dialog saved, read off the disk rather than the screen.
expect_saved_settings() {
  thesis_file config.json | python3 -c '
import json, sys
s = json.load(sys.stdin)["settings"]
assert s == {"idleShutdownMinutes": 2, "environmentVariables": {"GREETING": "hello"},
             "threadCap": 1, "startupScript": "echo started > ~/startup.txt"}, s
' || { echo "Thesis's config.json does not hold the four saved settings" >&2; exit 1; }
}

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"
capture "the device's cores" "$ADB" shell nproc

# Step 1 is T1's own flow: Thesis created, both Stopped with Settings, base run and its held.
run_flow "$ROOT/.maestro/jd-009-configure-a-space-t1-part1-settings-follow-stopped.yaml" \
  "$EVIDENCE/part1-settings-follow-stopped" || exit 1

run_part part2-configure-thesis
"$ADB" shell am force-stop "$APP"
capture "Thesis's config.json after the save and a kill" thesis_file config.json
expect_saved_settings

# Seeding force-stops the app, which is why part 3 relaunches rather than part 2 running on.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/jd-009-env.ipynb" "Env"
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/jd-009-threads.ipynb" "Threads"
seed_notebook "$ROOT/.maestro/fixtures/jd-009-threads.ipynb" "Threads"

run_part part3-relaunch-reads-back

run_part part4-run-thesis
capture "what the script wrote: home/jupydroid/startup.txt" thesis_file home/jupydroid/startup.txt
thesis_file home/jupydroid/startup.txt | tr -d '\r' | grep -qx started ||
  { echo "Thesis's startup.txt does not read 'started'" >&2; exit 1; }
capture "Thesis's server timers" timers_of space-1
expect_timers space-1 120
capture "Thesis's server environment" server_env space-1
expect_server_env space-1 'GREETING=hello'
expect_server_env space-1 'OPENBLAS_NUM_THREADS=1'
expect_server_env space-1 'OMP_NUM_THREADS=1'
# The derived set is still the one written: HOME is the Space's own, whatever the dialog was given.
expect_server_env space-1 'HOME=/home/jupydroid'

# Step 7 is T2's own flow: `printenv GREETING | tee greeting-env.txt` in a terminal.
run_flow "$ROOT/.maestro/jd-009-configure-a-space-t2-part3-terminal-sees-it.yaml" \
  "$EVIDENCE/part5-terminal-sees-it" || exit 1
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text greeting-env.txt hello
capture "what the terminal in Thesis read GREETING as" thesis_file home/jupydroid/greeting-env.txt

started=$(date +%s)
run_part part6-thesis-idles-out
capture "seconds from the terminal's flow ending to Thesis read Stopped (upper bound, includes maestro start-up)" \
  echo $(($(date +%s) - started))
capture "Thesis's server timers after it read Stopped (expected none)" timers_of space-1
capture "base's server timers then" timers_of base
[ -z "$(timers_of space-1)" ] || { echo "Thesis's server is still up" >&2; exit 1; }
expect_timers base 300

# Step 6's other half is T3's own flow, after a relaunch: base, uncapped, starts its own workers.
run_flow "$ROOT/.maestro/jd-009-configure-a-space-t3-part3-base-uncapped.yaml" \
  "$EVIDENCE/part7-base-uncapped" || exit 1
PID=$(kernel_pid)
capture "base's kernel: the cap's names (none expected)" kernel_caps "$PID"
capture "base's kernel: threads after import numpy" kernel_threads "$PID"
[ "$(kernel_caps "$PID")" = none ] || { echo "base's kernel carries a cap it was never given" >&2; exit 1; }

# Step 9 is T4's own flow: the script becomes `echo nope` / `exit 3`, and Run is refused.
run_flow "$ROOT/.maestro/jd-009-configure-a-space-t4-part2-failing-script.yaml" \
  "$EVIDENCE/part8-failing-script" || exit 1
capture "the failed script's run/startup.log" thesis_file run/startup.log
capture "Thesis's run/ after the refused start (no jpserver-*.json expected)" \
  "$ADB" shell run-as "$APP" ls "$THESIS/run"
thesis_file run/startup.log | tr -d '\r' | grep -qx nope || { echo "startup.log lacks 'nope'" >&2; exit 1; }

echo "Thesis's Settings stored all four, every process honoured them on its next start, it idled out"
echo "while base ran on, base stayed uncapped, and 'exit 3' refused the start with its status and output"
