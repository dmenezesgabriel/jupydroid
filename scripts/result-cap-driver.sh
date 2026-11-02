#!/usr/bin/env bash
# The result cap drawn rather than silent, against the real adapter and a real cap.
#
# WIDE WINDOW: the panel is drawn only where there is room for two panes. No cell is run, so this
# needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# One match over the cap, so the walk has something left to leave out.
#
# `search.maxResults`' registered default, spelled here because the flow asserts the number it
# produces - if the constant ever moves, this driver and the flow have to move together.
readonly MAX_SEARCH_RESULTS=20000

# A clean workspace: a search reads *everything*, so anything a previous run left would be counted
# into the total this flow asserts.
reset_notebooks

mapfile -t adb < <(device_adb)

# **Built on the device rather than seeded through `seed_file`**, and the reason is a limit rather
# than a preference: `seed_file` passes the fixture base64-encoded *as a shell argument*, and 20001
# lines is far past what an `adb shell` command line will carry. `yes | head` is toybox's own, and
# writes the file in one pass.
"${adb[@]}" shell am force-stop "$APP"
"${adb[@]}" shell "run-as $APP sh -c 'yes cat | head -n $((MAX_SEARCH_RESULTS + 1)) > \"$NOTEBOOK_DIR/many.py\"'"
# The redirect goes **inside** `run-as`'s own `sh -c`, and that is not a style choice: `adb shell`
# runs the whole string in a device shell whose cwd is `/`, so a `<` outside `run-as` is opened by
# that shell against a path that only means anything from the app's data dir - measured, it failed
# with "can't open files/spaces/base/home/jupydroid/many.py" against a file that was right there.
lines="$("${adb[@]}" shell "run-as $APP sh -c 'wc -l < \"$NOTEBOOK_DIR/many.py\"'" | tr -d '\r ')"
[ "$lines" = "$((MAX_SEARCH_RESULTS + 1))" ] ||
  { echo "seeded file has $lines lines, expected $((MAX_SEARCH_RESULTS + 1))"; exit 1; }
echo "seeded many.py with $lines matching lines, one over the cap of $MAX_SEARCH_RESULTS"

run_flow "$ROOT/.maestro/result-cap.yaml" "$ROOT/evidence/result-cap"
