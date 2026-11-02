#!/usr/bin/env bash
# The tab strip draws what it drew before the device turned: the order the tabs were opened in, the
# name a tab was renamed to, and the title a terminal's shell reported. `first.py` and `second.py`
# are opened, a terminal reports a title, `second.py` is renamed from its tab and `first.py` is
# tapped back on top; the device is turned to portrait and back, which relaunches the activity twice
# in the same process. The tab order is read off the window's own bounds before and after, and the
# two must match.
#
#   scripts/rotation-tab-strip-driver.sh
#
# The terminal runs the Space's real shell, so this goes on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/rotation-tab-strip"
FLOWS="$ROOT/.maestro/rotation-tab-strip"
FIRST="first.py"
SECOND="second.py"
export MAESTRO_FIRST="$FIRST" MAESTRO_SECOND="$SECOND" MAESTRO_RENAMED_STEM="renamed" MAESTRO_TITLE="reports"

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/first.py" "$FIRST"
seed_file "$ROOT/.maestro/fixtures/second.py" "$SECOND"
rotate_to 1

run_flow "$FLOWS-before.yaml" "$EVIDENCE/before"
before_pid="$(app_pid)"
before_order="$(tab_order)"
rotate_to 0
# The middle of the turn is the same one-pane frame T1 took; its flow names nothing of T1's.
run_flow "$ROOT/.maestro/rotation-dirty-tab-portrait.yaml" "$EVIDENCE/portrait"
rotate_to 1
run_flow "$FLOWS-after.yaml" "$EVIDENCE/after"
after_order="$(tab_order)"

# A relaunch of the activity, not of the process: a new process would start the strip empty.
after_pid="$(app_pid)"
{
  echo "app pid before the turn: $before_pid, after: $after_pid"
  echo "tabs before the turn, left to right: $before_order"
  echo "tabs after the turn, left to right:  $after_order"
} | tee "$EVIDENCE/strip.txt"
[ "$before_pid" = "$after_pid" ] || { echo "the process was restarted; the turn measured nothing" >&2; exit 1; }
[ "$before_order" = "$after_order" ] || { echo "the strip came back in another order" >&2; exit 1; }
