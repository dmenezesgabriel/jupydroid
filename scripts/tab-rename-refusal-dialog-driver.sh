#!/usr/bin/env bash
# A rename refused from a document's tab says why: does a "Rename Error" dialog carry the disk's
# refusal, where the dialog used to close in silence? `a.py` is renamed over the folder `full.py`,
# which holds `keep.txt` and so cannot be replaced; `b.py` is removed by the Space's shell and its
# tab then renamed. The workspace is read off the disk afterwards, because a refusal that had
# moved or deleted something on its way out would still have drawn the same dialog.
#
#   scripts/tab-rename-refusal-dialog-driver.sh
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

EVIDENCE="$ROOT/evidence/tab-rename-refusal-dialog"

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/first.py" "a.py"
seed_file "$ROOT/.maestro/fixtures/second.py" "b.py"
seed_file "$ROOT/.maestro/fixtures/save-conflict-notes.txt" "full.py/keep.txt"
rotate_to 1

run_flow "$ROOT/.maestro/tab-rename-refusal.yaml" "$EVIDENCE/refusal"
disk="$("${adb[@]}" shell "run-as $APP sh -c 'cd \"$NOTEBOOK_DIR\" && ls -R'" | tr -d '\r' | tr '\n' ' ')"
echo "workspace on disk: $disk" | tee "$EVIDENCE/disk.txt"
[[ "$disk" == ".: a.py full.py  ./full.py: keep.txt " ]] ||
  { echo "expected a.py and full.py/keep.txt alone on disk, found: $disk" >&2; exit 1; }
