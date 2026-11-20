#!/usr/bin/env bash
# The demo a `%pip install` of a package this Space's platform cannot have owes: the cell says why,
# in its own output, and a name that exists nowhere says nothing (ADR-0165).
#
#   scripts/pip-platform-explanation-driver.sh
#
# TABLET ONLY (SM-X216B): every part runs a cell, and the note is produced from pip's own text - on
# x86_64 the app falls back to `FakeKernelGateway`, which echoes the cell's source back as its
# output, so a green run there would measure nothing. Needs the tablet online: neither package is in
# the image, so pip resolves against PyPI and the note is checked against PyPI's release files.
#
# **What it asserts.** The saved notebook, read back: the note is its own `display_data` output,
# naming the platform, and pip's own stream does not carry it. The typo's notebook holds no
# platform at all - asserted after a real sleep, because the note a confirmed miss earns lands a
# moment after pip's text.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-078-t3}"
FLOWS="$ROOT/.maestro/pip-platform-explanation"
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
  # screen holds a single cell's output, which is what lets the parts assert unscoped.
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

require_explained() { # require_explained <package> - the note is its own output and pip's stream is pip's
  local package="$1" outputs
  outputs="$(saved_outputs "$BASE_HOME")"
  grep -qE "^  display_data: .*${package} ships no musllinux aarch64 wheel for Python 3\.12" <<<"$outputs" ||
    {
      echo "the saved notebook holds no platform note for $package" >&2
      echo "$outputs" >&2
      exit 1
    }
  if grep -E "^  stream stdout: " <<<"$outputs" | grep -q "musllinux"; then
    echo "the note folded into pip's own stream for $package, so the app's words read as pip's" >&2
    echo "$outputs" >&2
    exit 1
  fi
}

require_silent() { # require_silent - a name that exists nowhere earned no platform claim
  local outputs
  outputs="$(saved_outputs "$BASE_HOME")"
  if grep -q "musllinux" <<<"$outputs"; then
    echo "a name that exists nowhere earned a platform note" >&2
    echo "$outputs" >&2
    exit 1
  fi
}

: >"$CAPTURES"
capture "the image base was extracted from" in_base 'cat .jupydroid/rootfs-version'

run_part part1-duckdb
capture "duckdb: base's saved notebook" saved_outputs "$BASE_HOME"
require_explained duckdb

run_part part2-a-name-that-exists-nowhere
capture "duckdbzz: base's saved notebook" saved_outputs "$BASE_HOME"
require_silent

# The note a confirmed miss earns arrives a moment after pip's text, so the control's silence is
# only worth asserting once that moment is spent - a Maestro wait would return at once here.
sleep 10
run_flow "$FLOWS-part3-the-typo-stays-silent.yaml" "$EVIDENCE/part3-the-typo-stays-silent" || exit 1
dump_hierarchy "$EVIDENCE/part3-the-typo-stays-silent"

echo "duckdb's cell ends with the platform's reason as its own output, and a name that exists nowhere"
echo "ends with pip's text alone: $CAPTURES. Screenshots, UI dumps and logcat in $EVIDENCE."
