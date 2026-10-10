#!/usr/bin/env bash
# JD-009 T5: a Space's startup script is edited in a code cell's own field - numbered and coloured
# as bash - and stored as typed; a `.sh` file in the file editor takes the same colours.
#
#   scripts/jd-009-configure-a-space-t5-driver.sh
#
# Either device: nothing runs a cell, and the file editor reads the workspace through the adapter
# the device has.
#
# NOT A SUITE FLOW: it removes every user Space on the device, and base's config.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-009-configure-a-space/t5-script-editor}"
FLOWS="$ROOT/.maestro/jd-009-configure-a-space-t5"
CAPTURES="$EVIDENCE/captures.txt"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

base_config() { "$ADB" shell run-as "$APP" cat files/spaces/base/config.json; }

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part part1-script-editor
capture "base's config.json after the save" base_config
# shellcheck disable=SC2016 # the single quotes hold Python source, not shell
base_config | python3 -c '
import json, sys
script = json.load(sys.stdin)["settings"]["startupScript"]
assert script == "# prepare\nexport DATA=$HOME/data\necho \"ready $DATA\"", repr(script)
' || { echo "base's stored script is not the three lines typed" >&2; exit 1; }

# The script would run on base's start; this part is about the file editor, so it goes.
"$ADB" shell "run-as $APP rm -f files/spaces/base/config.json"
seed_file "$ROOT/.maestro/fixtures/jd-009-setup.sh" "setup.sh"
run_part part2-shell-file

echo "base's startup script was typed into a numbered, bash-coloured field and stored verbatim; setup.sh opened coloured the same"
