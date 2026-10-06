#!/usr/bin/env bash
# JD-009 T3: a Space's thread cap - set in Settings with the cap's own names refused as variables,
# stored in the Space's config.json, and written as OPENBLAS_NUM_THREADS and OMP_NUM_THREADS into
# its processes. Measured on the kernel both ways: Thesis capped at 1 starts no OpenBLAS workers,
# base uncapped starts one per core but the caller's.
#
#   scripts/jd-009-configure-a-space-t3-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs and numpy must be real, and on x86_64 the app falls back
# to FakeKernelGateway.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-009-configure-a-space/t3-thread-cap}"
FLOWS="$ROOT/.maestro/jd-009-configure-a-space-t3"
CAPTURES="$EVIDENCE/captures.txt"
THESIS_HOME="files/spaces/space-1/home/jupydroid"
mkdir -p "$EVIDENCE"

# The one running kernel's pid. A kernel is a proot *tracee* - a child of the server inside the
# Space's one proot - so it has no libproot.so line of its own; its cmdline is the interpreter's.
# Each part relaunches the app, which stops every Space process, so one kernel is all there is.
kernel_pid() {
  local pids
  pids=$("$ADB" shell "ps -A -o PID,CMDLINE" | tr -d '\r' |
    grep -- '-m jupydroid_kernel' | grep -v 'libproot.so' | awk '{print $1}')
  [ "$(echo "$pids" | grep -c .)" = 1 ] || { echo "expected one kernel, found: '$pids'" >&2; return 1; }
  echo "$pids"
}

kernel_env() { "$ADB" shell "run-as $APP cat /proc/$1/environ" | tr '\0' '\n'; }

kernel_caps() { kernel_env "$1" | grep '_NUM_THREADS=' || echo none; }

kernel_threads() { "$ADB" shell "run-as $APP cat /proc/$1/status" | tr -d '\r' | grep '^Threads:'; }

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"
capture "the device's cores" "$ADB" shell nproc

run_part part1-thread-cap-in-settings
capture "Thesis's config.json after the save" "$ADB" shell run-as "$APP" cat files/spaces/space-1/config.json

# Into both homes - seeding force-stops the app, which is why part 2 rather than part 1 runs Thesis.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/jd-009-threads.ipynb" "Threads"
seed_notebook "$ROOT/.maestro/fixtures/jd-009-threads.ipynb" "Threads"

run_part part2-thesis-capped
PID=$(kernel_pid)
capture "Thesis's kernel: the cap's names" kernel_caps "$PID"
capture "Thesis's kernel: threads after import numpy" kernel_threads "$PID"
kernel_env "$PID" | grep -qx 'OPENBLAS_NUM_THREADS=1' || { echo "Thesis's kernel lacks OPENBLAS_NUM_THREADS=1" >&2; exit 1; }
kernel_env "$PID" | grep -qx 'OMP_NUM_THREADS=1' || { echo "Thesis's kernel lacks OMP_NUM_THREADS=1" >&2; exit 1; }

run_part part3-base-uncapped
PID=$(kernel_pid)
capture "base's kernel: the cap's names (none expected)" kernel_caps "$PID"
capture "base's kernel: threads after import numpy" kernel_threads "$PID"
if kernel_env "$PID" | grep -q '_NUM_THREADS='; then echo "base's kernel carries a cap it was never given" >&2; exit 1; fi

echo "Thesis's Settings stored a thread cap of 1, OMP_NUM_THREADS was refused as the cap's, Thesis's kernel carries both names and numpy started no workers in it; base, uncapped, started its own"
