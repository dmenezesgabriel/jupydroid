#!/usr/bin/env bash
# Sourced by the jd-027-state-write drivers. Not executable on its own.
#
# How each driver seeds the app's own state files and reads back what a write left: the file's
# inode, mode and content, and the files beside it, where a `.~<name>` temp would sit if a write had
# not renamed it over the file (ADR-0089). Each writes into "$EVIDENCE".
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/jd-026-atomic-save-kill.sh"
#   . "$ROOT/scripts/jd-027-state-write-reads.sh"
#   seed_stores
#   record before "$THEME_SETTINGS_STORE"

# The theme store and the recents, each given a file before the flow, so each has an inode to change.
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

# `<label> <file>: <inode> <mode> <content>`, appended to the record.
record() {
  local label="$1" file="$2"
  echo "$label $file: $(inode_and_mode "$file") $("$ADB" shell "run-as $APP cat $file" | tr -d '\r')" |
    tee -a "$EVIDENCE/stores.txt"
}

# Every file in [dir], appended to the listing; a driver fails at the end if any is a `.~` temp.
list_beside() {
  { echo "# $1"; "$ADB" shell "run-as $APP ls -la $1" | tr -d '\r'; echo; } >>"$EVIDENCE/listing-after.txt"
}
