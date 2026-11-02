#!/usr/bin/env bash
# A settings store and the recents are replaced whole when they change, not rewritten where they
# sit (ADR-0089). Both are seeded, so each has an inode before the flow: the flow picks Dark in
# Settings and opens a notebook, and each file's inode, content and the files beside it are read
# before and after. A relaunch then shows Dark still chosen and the notebook still recent.
#
#   scripts/state-write-settings-driver.sh
#
# A kill is not aimed here: a store is a few hundred bytes, so its write lasts too short a time for
# a stop issued from the device to land inside it. AtomicFileWriteTest's failed write covers that.
#
# TABLET (SM-X216B): the device the rest of this item's evidence runs on.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/atomic-save-kill.sh
. "$ROOT/scripts/atomic-save-kill.sh"
# shellcheck source=scripts/state-write-reads.sh
. "$ROOT/scripts/state-write-reads.sh"

EVIDENCE="$ROOT/evidence/state-write-settings"
NOTEBOOK="state-write"
export MAESTRO_FILE="$NOTEBOOK.ipynb"

# Both stores, appended to the record.
record_stores() {
  record "$1" "$THEME_SETTINGS_STORE"
  record "$1" "$RECENTS_STORE"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/stores.txt"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "$NOTEBOOK"
seed_stores
record_stores before
run_flow "$ROOT/.maestro/state-write-settings-change.yaml" "$EVIDENCE/change"
wait_for_store "$THEME_SETTINGS_STORE" DARK
wait_for_store "$RECENTS_STORE" "$NOTEBOOK"
record_stores after
"$ADB" shell "run-as $APP ls -la files" | tr -d '\r' | grep -E "settings|recent|\.~" >"$EVIDENCE/listing-after.txt" || true
run_flow "$ROOT/.maestro/state-write-settings-relaunch.yaml" "$EVIDENCE/relaunch"
! grep -q '\.~' "$EVIDENCE/listing-after.txt"
