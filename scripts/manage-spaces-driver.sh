#!/usr/bin/env bash
# The Spaces home lists, creates, renames and deletes Spaces, and what it shows is
# what `files/spaces/` holds.
#
# Six flows rather than one, because the disk is checked between them: a row on screen does not
# prove what the filesystem holds, and a rename that wrote a second directory, or a cancelled
# delete that removed the first, would look the same as the right thing. The checks use `run-as`,
# the way `explorer-rename-driver.sh` does.
#
# TABLET, LANDSCAPE: the device the rest of the milestone is demonstrated on. Nothing here runs a
# cell. The rotation is locked here, the way `spaces-home-driver.sh` does it.
#
# NOT A SUITE FLOW: it rotates the device and removes every user Space on it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/manage-spaces"
DISK="$EVIDENCE/disk.txt"
FLOWS="$ROOT/.maestro/manage-spaces"
mkdir -p "$EVIDENCE"

# The accelerometer off first, or the device turns itself back the moment it is set down.
rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

# Appends one labelled capture to disk.txt, so the evidence holds what each claim was checked on.
capture() { # capture <label> <remote command>
  { echo "# $1"; "$ADB" shell run-as "$APP" sh -c "'$2'"; echo; } >>"$DISK"
}

on_disk() { "$ADB" shell run-as "$APP" test -e "files/spaces/$1"; }

present() { on_disk "$1" || { echo "never appeared on disk: files/spaces/$1" >&2; exit 1; }; }

gone() { ! on_disk "$1" || { echo "still on disk: files/spaces/$1" >&2; exit 1; }; }

named() { # named <id> <name>: the Space's config.json carries that name
  "$ADB" shell run-as "$APP" cat "files/spaces/$1/config.json" | grep -qF "\"$2\"" ||
    { echo "files/spaces/$1/config.json does not name $2" >&2; exit 1; }
}

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
rotate_to 1 # landscape: two panes
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
: >"$DISK"
capture "before: only base" "ls -la files/spaces"

run_part part1-create
present space-1/config.json
named space-1 Thesis
capture "after create Thesis" "ls -la files/spaces; cat files/spaces/space-1/config.json"

run_part part2-rename
named space-1 Dissertation
capture "after rename to Dissertation" "ls -la files/spaces; cat files/spaces/space-1/config.json"

run_part part3-delete-cancel
present space-1
capture "after cancelling the delete" "ls -la files/spaces"

run_part part4-delete-confirm
gone space-1
capture "after confirming the delete" "ls -la files/spaces"

run_part part5-create-again
named space-1 Scratch
capture "after create Scratch" "ls -la files/spaces; cat files/spaces/space-1/config.json"

"$ADB" shell am force-stop "$APP"
run_part part6-relaunch-open
capture "after relaunch, open base and back" "ls -la files/spaces"
echo "every act on the Spaces home matched files/spaces/ on disk"
