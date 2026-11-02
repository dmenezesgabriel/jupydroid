#!/usr/bin/env bash
# The demo: a rename refused from a document's tab says why. From a cold start with nothing
# seeded, `a.py`, a folder `full.py` holding `untitled.py`, and `b.py` are made from the explorer;
# `a.py`'s tab is renamed over the folder and `b.py`'s after the Space's shell removed it, and each
# draws "Rename Error" with the disk's reason.
#
#   scripts/tab-rename-refusal-demo-driver.sh
#
# Exits non-zero if the flow fails, or the workspace afterwards holds anything but a.py and
# full.py/untitled.py: a refusal that had moved or deleted something on its way out would still
# have drawn the same dialog.
#
# TABLET ONLY (SM-X216B): `b.py` is removed by a real shell in the Space.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/tab-rename-refusal-demo"

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/tab-rename-refusal-demo.yaml" "$EVIDENCE/refusal"
disk="$("${adb[@]}" shell "run-as $APP sh -c 'cd \"$NOTEBOOK_DIR\" && ls -R'" | tr -d '\r' | tr '\n' ' ')"
echo "workspace on disk: $disk" | tee "$EVIDENCE/disk.txt"
[[ "$disk" == ".: a.py full.py  ./full.py: untitled.py " ]] ||
  { echo "expected a.py and full.py/untitled.py alone on disk, found: $disk" >&2; exit 1; }
