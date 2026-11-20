#!/usr/bin/env bash
# Measures what pip prints in a cell's own output when a Space cannot install a package: `duckdb`
# and `scikit-learn` (the two `GAP-196` names), and a name that exists nowhere, which is the
# control that says whether pip can tell the two cases apart.
#
#   scripts/pip-platform-message-driver.sh
#
# TABLET ONLY (SM-X216B): every part runs a cell, and on x86_64 the app falls back to
# `FakeKernelGateway`, which echoes a cell's source back as its output. Needs the tablet online:
# neither package is in the image, so pip resolves against PyPI.
#
# **A measurement, not a gate.** The flows assert only that pip returned - IPython's own note after
# the subprocess, printed whether it succeeded or failed - because the wording is the thing being
# measured. The driver reads the saved notebook back and records it verbatim, stream name included.
#
# NOT A SUITE FLOW: it clears base's workspace before every part, so the device keeps no notebook of
# an earlier part.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-078-t2b}"
FLOWS="$ROOT/.maestro/pip-platform-message"
CAPTURES="$EVIDENCE/captures.txt"
BASE="files/spaces/base"
BASE_HOME="$BASE/home/jupydroid"
NOTEBOOK="Untitled.ipynb"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

in_base() { # in_base <command>
  "$ADB" shell "run-as $APP sh -c 'cd $BASE && $1'" | tr -d '\r'
}

dump_hierarchy() { # dump_hierarchy <out-dir> - the screen the part ended on, for the record
  local out="$1"
  mkdir -p "$out"
  "$ADB" shell uiautomator dump /sdcard/hierarchy.xml >/dev/null 2>&1 || true
  "$ADB" pull /sdcard/hierarchy.xml "$out/hierarchy.xml" >/dev/null 2>&1 || true
  "$ADB" shell rm -f /sdcard/hierarchy.xml
}

run_part() { # run_part <part> - its flow, then the screen that flow left
  local part="$1"
  # One notebook per part: the workspace is cleared first, so the flow starts from "New" and the
  # screen holds a single cell's output, which is what lets the body wait for the note unscoped.
  reset_notebooks
  run_flow "$FLOWS-$part.yaml" "$EVIDENCE/$part" || exit 1
  dump_hierarchy "$EVIDENCE/$part"
}

saved_outputs() { # saved_outputs <space home> - every output of every cell, as the file kept it
  "$ADB" exec-out run-as "$APP" cat "$1/$NOTEBOOK" | python3 -c '
import json, sys
for index, cell in enumerate(json.load(sys.stdin)["cells"]):
    print("cell " + str(index) + " " + cell["cell_type"] + " source: " + repr(cell["source"]))
    for output in cell["outputs"]:
        kind = output.get("output_type")
        if kind == "stream":
            print("  stream " + output["name"] + ": " + repr(output["text"]))
            continue
        if kind == "error":
            print("  error " + output["ename"] + ": " + output["evalue"])
            continue
        print("  " + str(kind) + ": " + repr(output.get("data", {}).get("text/plain", "")))
'
}

require_stream_output() { # require_stream_output <cell index> <package> - pip printed something
  local index="$1" package="$2"
  saved_outputs "$BASE_HOME" | grep -qE "^  stream (stdout|stderr): " ||
    {
      echo "cell $index of $BASE_HOME/$NOTEBOOK holds no stream output after '$package'" >&2
      saved_outputs "$BASE_HOME" >&2
      exit 1
    }
}

: >"$CAPTURES"
capture "the image base was extracted from" in_base 'cat .jupydroid/rootfs-version'

run_part part1-duckdb
capture "duckdb: base's saved notebook" saved_outputs "$BASE_HOME"
capture "duckdb: base's user base after the failed install" in_base 'ls pkgs/lib/python3.12/site-packages 2>&1'
require_stream_output 0 duckdb

run_part part2-scikit-learn
capture "scikit-learn: base's saved notebook" saved_outputs "$BASE_HOME"
capture "scikit-learn: base's user base after the failed install" in_base 'ls pkgs/lib/python3.12/site-packages 2>&1'
require_stream_output 0 scikit-learn

run_part part3-a-name-that-does-not-exist
capture "duckdbzz: base's saved notebook" saved_outputs "$BASE_HOME"
capture "duckdbzz: base's user base after the failed install" in_base 'ls pkgs/lib/python3.12/site-packages 2>&1'
require_stream_output 0 duckdbzz

echo "pip's own message is recorded for duckdb, scikit-learn and a name that exists nowhere, as the"
echo "notebook saved it: $CAPTURES. Screenshots and UI dumps in $EVIDENCE."
