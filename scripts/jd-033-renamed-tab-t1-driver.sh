#!/usr/bin/env bash
# A file renamed from its own tab while a terminal is on top: does the tab read the new name?
# `second.py` is opened, a terminal opened over it, and the file renamed to `renamed.py` from its
# tab's long press. The strip's titles are read off the window afterwards, and the explorer's
# listing off the disk. `before` records the defect; `after` must read the new name.
#
#   scripts/jd-033-renamed-tab-t1-driver.sh before|after
#
# The terminal runs the Space's real shell, so this goes on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

MODE="${1:?usage: $0 before|after}"
EVIDENCE="$ROOT/evidence/jd-033-renamed-tab/t1/$MODE"
SECOND="second.py"
RENAMED="renamed.py"
export MAESTRO_SECOND="$SECOND" MAESTRO_RENAMED_STEM="${RENAMED%.py}"

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-030-second.py" "$SECOND"
rotate_to 1

run_flow "$ROOT/.maestro/jd-033-renamed-tab-t1.yaml" "$EVIDENCE"
tabs="$(tab_order)"
disk="$("${adb[@]}" shell "run-as $APP ls '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' ')"
{
  echo "tabs after the rename, left to right: $tabs"
  echo "workspace on disk: $disk"
} | tee "$EVIDENCE/strip.txt"
[[ "$disk" == *"$RENAMED"* ]] || { echo "the file was not renamed; nothing was measured" >&2; exit 1; }
[ "$MODE" = after ] || exit 0
[[ "$tabs" == *"$RENAMED"* && "$tabs" != *"$SECOND"* ]] || { echo "the tab kept its old name" >&2; exit 1; }
