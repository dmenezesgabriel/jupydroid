#!/usr/bin/env bash
# Seeds a notebook, backdates it, and runs the edit-updates-recents flow.
#
# The backdating is the whole setup. `updatedAt` is now the file's mtime, and a fixture this
# driver has just pushed carries this moment's - so without `touch` there is no stale row to
# watch become fresh. 2020 renders as "1 Jan" (relativeTime's `d MMM` branch).
#
# The fixture also still carries the old `metadata.jupydroid.updated_at`, deliberately: the
# readback at the end proves the app ignores it on read and drops it on write.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/updated-at.ipynb"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # for reset_notebooks

# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="$NOTEBOOK_DIR/Updated At.ipynb"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

# This flow is the one that breaks first as Home accumulates: backdating to 2020 is what makes
# the row stale, and a stale row sorts to the bottom of a list ordered by recency. At 64
# notebooks the flow's `scrollUntilVisible` stopped reaching it.
reset_notebooks
# The last driver that still seeded by hand, and the last to be bitten by it: the file
# name is the notebook's title, so the path is `Updated At.ipynb` and the unquoted redirect split
# it into a file called `Updated`. The helper quotes; backdating is the only step this driver ever
# needed of its own.
seed_notebook "$FIXTURE" "Updated At"
"$ADB" shell "run-as $APP touch -t 202001010000 \"$REMOTE\""

# The handwriting IME steals focus from text fields and swallows inputText, and this flow types.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

maestro_test "$ROOT/.maestro/edit-updates-recents.yaml"

# The file is the other half of the assertion: mtime moved, and the stale `updated_at` the
# fixture carried is gone rather than merely ignored.
echo "--- mtime after the edit ---"
"$ADB" shell "run-as $APP ls -l \"$REMOTE\""
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as $APP cat \"$REMOTE\""
