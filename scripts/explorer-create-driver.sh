#!/usr/bin/env bash
# Evidence: the Files bar's New drop-down creates a notebook, a Python file and a folder
# through the real app, and each lands on disk where the browsed directory says it should.
#
# The emulator is enough: nothing here runs a cell, so `FakeKernelGateway` is never consulted.
#
# The flow proves the menu, the rows and the closed drop-down are on screen; these checks prove
# the creations reached the disk - a menu that renders but never writes would look green.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# The folder to browse into is seeded, so "create at the browsed directory" creates where a
# *user* navigated - not only at the root the flow happened to start from.
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/explorer-create.yaml" "$ROOT/evidence/explorer-create"

# A creation may land a little after the row shows, so poll rather than assert once.
on_disk() { "$ADB" shell run-as "$APP" "test -e \"$NOTEBOOK_DIR/$1\""; }
wait_for_disk() {
  for _ in {1..20}; do
    if on_disk "$1"; then return 0; fi
    sleep 0.25
  done
  echo "never appeared on disk: $1" >&2
  exit 1
}

wait_for_disk "Untitled.ipynb"
nb="$("$ADB" shell run-as "$APP" cat "$NOTEBOOK_DIR/Untitled.ipynb" | tr -d '\r')"
if ! grep -q nbformat <<<"$nb"; then
  echo "Untitled.ipynb is not a notebook; content was:" >&2
  printf '%s\n' "$nb" >&2
  exit 1
fi
echo "notebook created at the root and is a real notebook"

wait_for_disk "reports/untitled.py"
echo "python file created in the browsed directory"

wait_for_disk "Untitled Folder"
if ! "$ADB" shell run-as "$APP" "test -d \"$NOTEBOOK_DIR/Untitled Folder\""; then
  echo "'Untitled Folder' exists but is not a directory" >&2
  exit 1
fi
echo "folder created at the root as a directory"