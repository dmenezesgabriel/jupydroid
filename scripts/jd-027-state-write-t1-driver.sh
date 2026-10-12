#!/usr/bin/env bash
# A settings store and the recents are replaced whole when they change, not rewritten where they
# sit (ADR-0089). Both are seeded, so each has an inode before the flow: the flow picks Dark in
# Settings and opens a notebook, and each file's inode, content and the files beside it are read
# before and after. A relaunch then shows Dark still chosen and the notebook still recent.
#
#   scripts/jd-027-state-write-t1-driver.sh
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
# shellcheck source=scripts/jd-026-atomic-save-kill.sh
. "$ROOT/scripts/jd-026-atomic-save-kill.sh"

EVIDENCE="$ROOT/evidence/jd-027-state-write/t1"
NOTEBOOK="jd-027-state-write"
export MAESTRO_FILE="$NOTEBOOK.ipynb"

seed_stores() {
  "$ADB" shell "run-as $APP sh -c 'echo {\\\"override\\\":\\\"LIGHT\\\"} > $THEME_SETTINGS_STORE'"
  "$ADB" shell "run-as $APP sh -c 'echo {\\\"documents\\\":[]} > $RECENTS_STORE'"
}

# Returns once [store] holds [needle], or fails after 30 s: the write runs on the app's own scope,
# after the tap that asked for it.
wait_for_store() {
  local store="$1" needle="$2"
  "$ADB" shell "run-as $APP sh -c 'end=\$((\$(date +%s)+30)); while [ \$(date +%s) -lt \$end ]; do grep -q \"$needle\" $store && exit 0; sleep 1; done; exit 1'"
}

# `<label> <inode> <mode> <content>` for each store, appended to the record.
record_stores() {
  local label="$1" store
  for store in "$THEME_SETTINGS_STORE" "$RECENTS_STORE"; do
    echo "$label $store: $(inode_and_mode "$store") $("$ADB" shell "run-as $APP cat $store" | tr -d '\r')"
  done | tee -a "$EVIDENCE/stores.txt"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/stores.txt"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "$NOTEBOOK"
seed_stores
record_stores before
run_flow "$ROOT/.maestro/jd-027-state-write-t1-change.yaml" "$EVIDENCE/change"
wait_for_store "$THEME_SETTINGS_STORE" DARK
wait_for_store "$RECENTS_STORE" "$NOTEBOOK"
record_stores after
"$ADB" shell "run-as $APP ls -la files" | tr -d '\r' | grep -E "settings|recent|\.~" >"$EVIDENCE/listing-after.txt" || true
run_flow "$ROOT/.maestro/jd-027-state-write-t1-relaunch.yaml" "$EVIDENCE/relaunch"
! grep -q '\.~' "$EVIDENCE/listing-after.txt"
