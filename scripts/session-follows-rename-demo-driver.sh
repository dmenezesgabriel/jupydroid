#!/usr/bin/env bash
# The demo: a notebook's kernel session follows its rename. From a cold start, `a.ipynb` is
# created, runs `x = 41` and is renamed to `b.ipynb` from the strip; the Running panel's kernel row
# reads `b.ipynb`, reveals the renamed tab, and `x + 1` prints `42` there. Then a notebook created
# inside a folder `f` runs a cell, `f` is renamed to `g` from the explorer, and its row reveals the
# live tab too.
#
#   scripts/session-follows-rename-demo-driver.sh
#
# Exits non-zero if a flow fails, a row's tap leaves a second tab for its notebook, an old path is
# still on disk, or the process changes.
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

EVIDENCE="$ROOT/evidence/session-follows-rename-demo"
FLOWS="$ROOT/.maestro/session-follows-rename-demo"

trap restore_rotation EXIT

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }
record() { echo "$1" | tee -a "$EVIDENCE/strip.txt"; }
fail() { echo "$1" >&2; exit 1; }
# Dotfiles are left out: the workspace is the kernel's HOME, and IPython keeps its profile there.
tree() {
  "${adb[@]}" shell "run-as $APP sh -c 'cd $NOTEBOOK_DIR && find . -mindepth 1 -not -path \"./.*\"'" |
    tr -d '\r' | sort | tr '\n' ' '
}

# The row's tap revealed the notebook's one tab: a row that opened a dead path would push a second.
require_one_tab() { # require_one_tab <tab title> <when>
  local tabs
  tabs="$(tab_order)"
  record "tabs $2, left to right: $tabs"
  [ "$(grep -oF "$1" <<<"$tabs" | wc -l)" -eq 1 ] || fail "expected one '$1' tab $2, got: $tabs"
}

require_same_pid() {
  [ "$(app_pid)" = "$started_pid" ] || fail "the process was restarted; the kernel measured nothing"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/strip.txt"
reset_notebooks
rotate_to 1

run_part part1-tab
started_pid="$(app_pid)"
require_one_tab b.ipynb "after b.ipynb's row was tapped"
run_part part2-folder
require_one_tab Untitled.ipynb "after the row was tapped beneath the renamed folder"
require_one_tab b.ipynb "at the end"
require_same_pid
disk="$(tree)"
record "workspace on disk: $disk"
[ "$disk" = "./b.ipynb ./g ./g/Untitled.ipynb " ] || fail "expected b.ipynb and g/Untitled.ipynb alone, got: $disk"
record "app pid throughout: $started_pid"
