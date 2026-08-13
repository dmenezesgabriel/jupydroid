#!/usr/bin/env bash
# X2g evidence: the explorer's row menu deletes what it names - and only after asking.
#
# The emulator is enough: nothing here runs a cell, so `FakeKernelGateway` is never consulted.
#
# The flow proves the question, the cancel and the rows; these checks prove the filesystem. A row
# that leaves a list is not the same claim as a file that leaves the disk, and the refusal of a
# non-empty folder is exactly the case where the two could disagree.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "notes.txt"
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "keep.txt"
# A folder with something in it, so the refusal has something to refuse - and something to empty.
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/explorer-delete.yaml" "$ROOT/evidence/x2g-explorer-delete"

on_disk() { "$ADB" shell run-as "$APP" "test -e \"$NOTEBOOK_DIR/$1\""; }

gone() {
  if on_disk "$1"; then
    echo "still on disk after the delete: $1" >&2
    "$ADB" shell run-as "$APP" ls -R "$NOTEBOOK_DIR" >&2
    exit 1
  fi
}

gone "notes.txt"
echo "the confirmed delete reached the disk"

on_disk "keep.txt" || { echo "keep.txt went with it; the delete took the wrong file" >&2; exit 1; }
echo "the file beside it was left alone"

# The folder was refused while full, emptied through the browser, and only then deleted - so its
# absence here also says the refusal was not silently overridden on the way.
gone "reports"
echo "a folder went only once it was empty"
