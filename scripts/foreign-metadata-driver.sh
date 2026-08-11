#!/usr/bin/env bash
# Seeds a notebook full of metadata this app does not model, drives a real edit, then checks
# key by key that the saved file still carries all of it.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
#
# The cell has a distinctive first line and a distinctive last one: the placeholder can only
# show the first, so asserting one visible and the other gone is the fold itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/foreign-metadata.ipynb"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="$NOTEBOOK_DIR/foreign-metadata.ipynb"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" foreign-metadata

# The handwriting IME steals focus from text fields and swallows inputText; this flow is
# nothing but typing, so it cannot run with it enabled.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

maestro_test "$ROOT/.maestro/foreign-metadata.yaml"

# The file on disk is the whole assertion here - the screenshots only show that the app was
# driven. Every key below was written by something other than this app and must still be there.
echo "--- notebook as saved after the flow ---"
SAVED="$("$ADB" shell run-as "$APP" cat "$REMOTE")"
echo "$SAVED"

missing=0
for key in \
  '"tags":["setup","slow"]' \
  '"slideshow":{"slide_type":"skip"}' \
  '"scrolled":true' \
  '"nbgrader":{"grade_id":"cell-a1","points":5}' \
  '"outputs_hidden":true' \
  '"attachments":{"shot.png":{"image/png":"iVBORw0KGgo="}}' \
  '"language_info":{"name":"python","version":"3.11.2"}' \
  '"widgets":{"state":{}}' \
  '"authors":[{"name":"Ada"}]' \
  '"display_name":"Python 3.11 (conda)"' \
  '"language":"python"' \
  '"some_future_key":{"seen":true}' \
  '"source_hidden":true'
do
  case "$SAVED" in
    *"$key"*) echo "kept    $key" ;;
    *) echo "LOST    $key"; missing=1 ;;
  esac
done

[ "$missing" -eq 0 ] || { echo "the save destroyed metadata it does not model"; exit 1; }
echo "nothing lost"
