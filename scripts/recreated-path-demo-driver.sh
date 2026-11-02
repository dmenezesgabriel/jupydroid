#!/usr/bin/env bash
# The PRD's demo: a document created at the name an open tab was renamed away from opens in a tab
# of its own. From a cold start with autosave off, `a.ipynb` is created, typed into and renamed to
# `b.ipynb` from the strip; a new `a.ipynb` is created and opened beside it. The same with `a.py`
# and `b.py` in the file editor. Then `b.ipynb`'s row reveals the renamed tab rather than opening a
# third.
#
#   scripts/recreated-path-demo-driver.sh
#
# Exits non-zero if a flow fails, a name lacks its tab or its file, a typed line reached the disk
# (the dot would then be lying), the reveal changes the strip, or the process changes.
#
# Autosave is off because it writes a typed line within a second, and the dot the demo looks for
# would be gone. Run on the arm64 tablet, where every demo of the tab strip ran.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/recreated-path-demo"
FLOWS="$ROOT/.maestro/recreated-path-demo"
TYPED="typed_before_the_rename"
export MAESTRO_TYPED="$TYPED"

trap restore_rotation EXIT

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }
record() { echo "$1" | tee -a "$EVIDENCE/strip.txt"; }
fail() { echo "$1" >&2; exit 1; }
listing() { "${adb[@]}" shell "run-as $APP ls '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' '; }
holds() { "${adb[@]}" shell "run-as $APP grep -qF '$2' '$NOTEBOOK_DIR/$1'"; }

# Both names have a tab and a file, and the renamed one's typed line is still owed.
require_both_open() { # require_both_open <recreated name> <renamed name> <when>
  local tabs disk
  tabs="$(tab_order)"
  disk="$(listing)"
  record "tabs $3, left to right: $tabs"
  record "workspace on disk $3: $disk"
  [[ "$tabs" == *"$1"* && "$tabs" == *"$2"* ]] || fail "'$1' and '$2' do not have a tab each: $tabs"
  [[ " $disk " == *" $1 "* && " $disk " == *" $2 "* ]] || fail "the workspace lacks '$1' or '$2': $disk"
  if holds "$2" "$TYPED"; then fail "the typed line reached $2, so its dot measured nothing"; fi
}

require_same_pid() {
  [ "$(app_pid)" = "$started_pid" ] || fail "the process was restarted; the tabs measured nothing"
}

# Step 1: an empty workspace, autosave off, landscape.
cold_start() {
  reset_notebooks
  # Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
  local payload
  payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
  "${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
  rotate_to 1
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/strip.txt"
cold_start
run_part part1-notebook
started_pid="$(app_pid)"
require_both_open a.ipynb b.ipynb "after the notebook was recreated"
run_part part2-file
require_both_open a.py b.py "after the file was recreated"
require_same_pid
before_reveal="$(tab_order)"
run_part part3-reveal
after_reveal="$(tab_order)"
record "tabs after b.ipynb's row was tapped, left to right: $after_reveal"
[ "$after_reveal" = "$before_reveal" ] || fail "the reveal changed the strip: $before_reveal -> $after_reveal"
require_same_pid
record "app pid throughout: $started_pid"
