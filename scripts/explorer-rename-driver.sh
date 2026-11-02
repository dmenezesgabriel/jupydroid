#!/usr/bin/env bash
# Evidence: the explorer's row menu renames a file and a folder through the real app, and the
# rename is a *move* on disk - the old path is gone, not merely hidden.
#
# The emulator is enough: nothing here runs a cell, so `FakeKernelGateway` is never consulted.
#
# The flow proves the menu, the dialog and the rows; these checks prove the filesystem. A rename
# that copied, or that wrote a second file and left the first, would look identical on screen -
# which is the whole reason `assertNotVisible` in the flow is not the gate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "notes.txt"
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "taken.txt"
# A folder with something in it, so "the contents came along" is a claim the flow can make.
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/explorer-rename.yaml" "$ROOT/evidence/explorer-rename"

on_disk() { "$ADB" shell run-as "$APP" "test -e \"$NOTEBOOK_DIR/$1\""; }

gone() {
  if on_disk "$1"; then
    echo "still on disk after the rename: $1" >&2
    exit 1
  fi
}

present() {
  if ! on_disk "$1"; then
    echo "never appeared on disk: $1" >&2
    exit 1
  fi
}

present "kept.txt"
gone "notes.txt"
echo "the file moved: notes.txt -> kept.txt"

present "archive/data.csv"
gone "reports"
echo "the folder moved with its contents: reports/ -> archive/"

# The overwrite question was answered with Cancel, so both files are still there - the one that
# would have been replaced most of all.
present "taken.txt"
present "kept.txt"
echo "a cancelled overwrite left both files"
