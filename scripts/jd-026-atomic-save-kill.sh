#!/usr/bin/env bash
# Sourced by the jd-026-atomic-save drivers. Not executable on its own.
#
# What every kill-mid-save driver does to a document on the device, given the document's path and
# the `.~<name>` temp a save writes beside it (ADR-0089): stop the app the moment a write is under
# way, wait for saves to settle, and read the file's inode.
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/jd-026-atomic-save-kill.sh"
#   kill_on_write "$NOTEBOOK_DIR/notes.txt" "$NOTEBOOK_DIR/.~notes.txt"

# Runs on the device, so no host round trip sits between seeing the write and stopping the app.
# `run-as` may not signal the app (SELinux refuses it), so it only watches; the stop is the shell's
# `cmd activity force-stop`, issued the moment the watch returns. A write is under way once the
# target's size changes or the temp appears. Prints what the watch saw.
kill_on_write() {
  local remote="$1" temp="$2" seeded
  seeded="$("$ADB" shell "run-as $APP stat -c %s \"$remote\"" | tr -d '\r')"
  "$ADB" shell "run-as $APP sh -c 'end=\$((\$(date +%s)+120)); while [ \$(date +%s) -lt \$end ]; do if [ -e \"$temp\" ]; then echo temp \$(stat -c %s \"$temp\"); exit 0; fi; s=\$(stat -c %s \"$remote\"); if [ \"\$s\" != $seeded ]; then echo target \$s; exit 0; fi; done; echo no-write; exit 1' && cmd activity force-stop $APP" | tr -d '\r'
}

# Returns once the file's inode has held for 10 s with no temp beside it: one edit can be followed
# by more than one autosave, and a listing taken during one shows that save's temp.
wait_for_saves_to_settle() {
  local remote="$1" temp="$2"
  "$ADB" shell "run-as $APP sh -c 'last=; quiet=0; end=\$((\$(date +%s)+120)); while [ \$(date +%s) -lt \$end ] && [ \$quiet -lt 10 ]; do i=\$(stat -c %i \"$remote\"); if [ \"\$i\" = \"\$last\" ] && [ ! -e \"$temp\" ]; then quiet=\$((quiet+1)); else quiet=0; fi; last=\$i; sleep 1; done; [ \$quiet -ge 10 ]'"
}

# The file's inode and mode, `<inode> <octal mode>`: a save that replaced it changes the first, and
# one that kept its permissions does not change the second.
inode_and_mode() {
  "$ADB" shell "run-as $APP stat -c '%i %a' \"$1\"" | tr -d '\r'
}

# A write's [inode_and_mode] before and after it: `replaced` when the inode changed and the mode held.
replaced_or_not() {
  local before="$1" after="$2"
  if [ "${before% *}" != "${after% *}" ] && [ "${before#* }" = "${after#* }" ]; then echo replaced; else echo NOT-REPLACED; fi
}
