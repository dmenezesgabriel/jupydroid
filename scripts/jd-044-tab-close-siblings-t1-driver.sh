#!/usr/bin/env bash
# A tab's menu closes the tabs beside it: with autosave off, a terminal, a.py, b.py (edited) and
# c.py are opened in that order. a.py's Close Tabs to Right closes c.py while dirty b.py asks and
# Cancel keeps it; the terminal's Close All Other Tabs asks again, and Discard leaves the terminal
# alone. b.py on disk still holds what was seeded.
#
#   scripts/jd-044-tab-close-siblings-t1-driver.sh
#
# Runs no cell; driven on the arm64 tablet beside the rest of this item's evidence.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="${EVIDENCE:-$ROOT/evidence/jd-044-tab-close-siblings/t1}"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `p6c-8-close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" a.py
seed_file "$ROOT/.maestro/fixtures/jd-042-other.py" b.py
seed_file "$ROOT/.maestro/fixtures/file-editor.py" c.py
rotate_to 1

run_flow "$ROOT/.maestro/jd-044-tab-close-siblings-t1.yaml" "$EVIDENCE"
on_disk="$("${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/b.py\"" | tr -d '\r')"
[ "$on_disk" = "$(cat "$ROOT/.maestro/fixtures/jd-042-other.py")" ] ||
  fail "expected b.py to hold its seed after Discard, found: $on_disk"
echo "after the flow, b.py on disk is its seed: $on_disk" | tee "$EVIDENCE/disk.txt"
