#!/usr/bin/env bash
# A notebook opened from another app opens with its own cells. The fixture is pushed to Download/,
# indexed by the media scanner and handed to the app as the file manager hands it: a VIEW intent on
# its `content://media/...` URI, from a cold start. The flows check what is on screen; this reads
# what landed in the workspace, since a copy left as it was served is not something a screen shows.
#
# With --from-my-files no intent is written here: each open goes through Samsung's My Files and the
# "Open with" chooser, so the URI and its grant are the ones a file manager makes.
#
#   scripts/jd-023-shared-notebook-t1-driver.sh [--from-my-files]
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

FROM_MY_FILES="${1:-}"
EVIDENCE="$ROOT/evidence/jd-023-shared-notebook/t1-opened-with-its-cells"
[ "$FROM_MY_FILES" = "--from-my-files" ] && EVIDENCE="$ROOT/evidence/jd-023-shared-notebook/demo-from-my-files"
FLOWS="$ROOT/.maestro/jd-023-shared-notebook-t1"
NAME="Shared analysis.ipynb"
REMOTE="/sdcard/Download/$NAME"

mapfile -t adb < <(device_adb)

# Sends the VIEW intent a file manager sends for the fixture, read-grant flag included - or, with
# --from-my-files, has My Files send its own, into the evidence directory $1 names.
view_shared_notebook() {
  if [ "$FROM_MY_FILES" = "--from-my-files" ]; then
    run_flow "$ROOT/.maestro/jd-023-shared-notebook-open-from-my-files.yaml" "$EVIDENCE/$1"
    return
  fi
  "${adb[@]}" shell "am start -a android.intent.action.VIEW -d 'content://media/external/file/$MEDIA_ID' -t '*/*' -f 0x1 $APP" >/dev/null
}

reset_notebooks
"${adb[@]}" push "$ROOT/.maestro/fixtures/$NAME" "$REMOTE" >/dev/null
# The space is encoded: the push alone leaves a row whose date_modified is NULL, an unencoded URI
# scans nothing, and My Files' search lists no row that was never scanned (measured on SM-X216B).
"${adb[@]}" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://${REMOTE// /%20}" >/dev/null 2>&1 || true
sleep 2
MEDIA_ID="$("${adb[@]}" shell content query --uri content://media/external/file --projection _id:_display_name \
  | grep "$NAME" | grep -m1 -oE '_id=[0-9]+' | cut -d= -f2)"
[ -n "$MEDIA_ID" ] || { echo "no media id for $REMOTE; expected the scanner to have indexed it" >&2; exit 1; }

"${adb[@]}" shell am force-stop "$APP"
view_shared_notebook part1-from-my-files
run_flow "$FLOWS-part1-opened.yaml" "$EVIDENCE/part1-opened"
# A key this app does not model, so only a byte-for-byte copy still has it.
wait_for_workspace_text "$NAME" '"shared_from": "another app"' | tee "$EVIDENCE/part1-opened/workspace.txt"

run_flow "$FLOWS-part2-run.yaml" "$EVIDENCE/part2-run"
wait_for_workspace_text "$NAME" '"45' | tee "$EVIDENCE/part2-run/workspace.txt"

view_shared_notebook part3-from-my-files
run_flow "$FLOWS-part3-again.yaml" "$EVIDENCE/part3-again"
{
  wait_for_workspace_text "Shared analysis1.ipynb" '"shared_from": "another app"'
  # The first copy still holds its run: the second import counted past it rather than over it.
  wait_for_workspace_text "$NAME" '"45'
} | tee "$EVIDENCE/part3-again/workspace.txt"

"${adb[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-part4-relaunch.yaml" "$EVIDENCE/part4-relaunch"
"${adb[@]}" shell rm -f "$REMOTE"
