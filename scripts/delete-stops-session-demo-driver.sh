#!/usr/bin/env bash
# The PRD's demo: deleting a notebook stops the kernel session it was running. From a cold start,
# `a.ipynb` is created, runs a cell and is deleted with its tab open; `b.ipynb` the same with its tab
# closed; `c.ipynb` is replaced by `d.ipynb` renamed over it. Each time the Running panel's kernel
# row leaves with the file. Then `e.ipynb` is deleted beside `keep.ipynb`, whose row stays and
# whose second cell `x + 1` prints `42` on the kernel it started on.
#
#   scripts/delete-stops-session-demo-driver.sh
#
# Exits non-zero if a flow fails, the process changes, or the workspace afterwards holds anything
# but c.ipynb and keep.ipynb.
#
# Runs cells, so it belongs on the arm64 tablet: on x86_64 the fake gateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/delete-stops-session-demo"
FLOWS="$ROOT/.maestro/delete-stops-session-demo"

trap restore_rotation EXIT

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }
record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
rotate_to 1

run_part part1-delete
started_pid="$(app_pid)"
run_part part2-overwrite
[ "$(app_pid)" = "$started_pid" ] || fail "the process was restarted; keep.ipynb's kernel measured nothing"
disk="$("${adb[@]}" shell "run-as $APP ls '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' ')"
record "workspace on disk: $disk"
[ "$disk" = "c.ipynb keep.ipynb " ] || fail "expected c.ipynb and keep.ipynb alone on disk, found: $disk"
record "app pid throughout: $started_pid"
