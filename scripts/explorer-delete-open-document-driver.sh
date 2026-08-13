#!/usr/bin/env bash
# X2g: deleting the document that is open beside the browser, which only two panes can reach.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# What this proves, and what it does not. It proves the pane closes with the row and that the file
# is still gone after the app's own teardown - our editor flushes its last edit on the way out,
# where the reference's `SaveHandler.dispose()` writes nothing.
#
# It does **not** gate the write-guard in `persist`. By the time the delete is confirmed the
# debounce has long since fired, so there is no pending write left for the teardown to flush:
# checked by mutation, and a build with that guard removed still passes here. What covers it is
# `EditorRenameTest.aNotebookDeletedElsewhereIsNotWrittenBack`, which types *after* the delete and
# does fail without it. Said out loud because a flow that looks like it covers something it does
# not is worse than no flow at all.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/evidence/x2g-explorer-delete/open-document"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -lt 840 ]; then
  echo "this flow must run on a wide (>= 840dp) window; $WIDTH_DP is not one" >&2
  exit 1
fi

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/switch-alpha.ipynb" "Switch Alpha"

run_flow "$ROOT/.maestro/explorer-delete-open-document.yaml" "$OUT"

# Leaving is what flushes a debounced edit, so the app is stopped the way a user leaves rather
# than force-killed: a kill would take the very write this is looking for with it.
"$ADB" shell input keyevent KEYCODE_BACK
sleep 3
"$ADB" shell input keyevent KEYCODE_BACK
sleep 3

if "$ADB" shell run-as "$APP" "test -e \"$NOTEBOOK_DIR/Switch Alpha.ipynb\""; then
  echo "the notebook came back: the editor wrote it out after it was deleted" >&2
  "$ADB" shell run-as "$APP" ls "$NOTEBOOK_DIR" >&2
  exit 1
fi
echo "the deleted notebook stayed deleted"

remaining="$("$ADB" shell run-as "$APP" ls "$NOTEBOOK_DIR" | tr -d '\r')"
if [ -n "$remaining" ]; then
  echo "the workspace should be empty, but holds:" >&2
  printf '%s\n' "$remaining" >&2
  exit 1
fi
echo "and nothing was written in its place"
