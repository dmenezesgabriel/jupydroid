#!/usr/bin/env bash
# The demo, on one tablet run: a cold install of the build, a Space made and entered, a notebook
# opened; `%pip install duckdb` fetches PyPI's manylinux wheel into the glibc kernel's user base;
# duckdb's own quickstart answers `[(42,)]`; and after the app is killed and relaunched the same
# cell answers again with no second install.
#
#   scripts/published-wheel-in-a-space-driver.sh
#
# TABLET ONLY (SM-X216B): every part runs a cell, and on x86_64 the app falls back to
# `FakeKernelGateway`, which echoes a cell's source back as its output. Needs the tablet online:
# pip downloads duckdb.
#
# NOT A SUITE FLOW: it uninstalls the app, so the device keeps none of an earlier run's state.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-096-t7-maestro}"
FLOWS="$ROOT/.maestro/published-wheel-in-a-space"
CAPTURES="$EVIDENCE/captures.txt"
DEMO="files/spaces/space-1"
DEMO_HOME="$DEMO/home/jupydroid"
NOTEBOOK="Untitled.ipynb"
# The glibc kernel's user base, where its pip's --user installs land - not the musl `pkgs/`.
USER_SITE="pkgs-gnu/lib/python3.12/site-packages"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

in_space() { # in_space <space dir> <command>
  "$ADB" shell "run-as $APP sh -c 'cd $1 && $2'" | tr -d '\r'
}

in_demo() { in_space "$DEMO" "$1"; }

dump_hierarchy() { # dump_hierarchy <out-dir> - the screen the part ended on, for the record
  local out="$1"
  mkdir -p "$out"
  "$ADB" shell uiautomator dump /sdcard/hierarchy.xml >/dev/null 2>&1 || true
  "$ADB" pull /sdcard/hierarchy.xml "$out/hierarchy.xml" >/dev/null 2>&1 || true
  "$ADB" shell rm -f /sdcard/hierarchy.xml
}

run_part() { # run_part <part> - its flow, then the screen that flow left
  local part="$1"
  run_flow "$FLOWS-$part.yaml" "$EVIDENCE/$part" || exit 1
  dump_hierarchy "$EVIDENCE/$part"
}

saved_outputs() { # saved_outputs <space home> - every cell's output as the file kept it
  "$ADB" exec-out run-as "$APP" cat "$1/$NOTEBOOK" | python3 -c '
import json, sys
for index, cell in enumerate(json.load(sys.stdin)["cells"]):
    print("cell", index, cell["cell_type"])
    for output in cell["outputs"]:
        if output.get("output_type") == "error":
            print("  " + output["ename"] + ": " + output["evalue"])
            continue
        text = output.get("data", {}).get("text/plain", "".join(output.get("text", [])))
        print("  " + text.rstrip())
'
}

cell_outputs() { # cell_outputs <space home> <index> - one cell's output, as the file kept it
  "$ADB" exec-out run-as "$APP" cat "$1/$NOTEBOOK" | python3 -c '
import json, sys
cell = json.load(sys.stdin)["cells"][int(sys.argv[1])]
for output in cell["outputs"]:
    if output.get("output_type") == "error":
        print(output["ename"] + ": " + output["evalue"])
        continue
    text = output.get("data", {}).get("text/plain", "".join(output.get("text", [])))
    print(text.rstrip())
' "$2"
}

expect_cell() { # expect_cell <space home> <index> <fixed text>
  cell_outputs "$1" "$2" | grep -qF -- "$3" ||
    { echo "expected '$3' in cell $2 of $1/$NOTEBOOK, found:" >&2; cell_outputs "$1" "$2" >&2; exit 1; }
}

fresh_install "$EVIDENCE/00-cold-install"
reset_notebooks
: >"$CAPTURES"

run_part part1-create
capture "the kernelspec the Demo Space's start wrote" in_demo 'cat kernels/python3/kernel.json 2>/dev/null || find . -name kernel.json | head -3'
capture "Demo's user bases before anything is installed" in_demo 'ls -d pkgs pkgs-gnu 2>&1'

run_part part2-install
expect_cell "$DEMO_HOME" 0 "Successfully installed duckdb"
capture "where pip put duckdb" in_demo "ls -d $USER_SITE/duckdb pkgs/lib/python3.12/site-packages/duckdb 2>&1"
[ -n "$(in_demo "ls -d $USER_SITE/duckdb 2>/dev/null")" ] ||
  { echo "pip did not put duckdb in $DEMO/$USER_SITE" >&2; exit 1; }
capture "Demo's saved notebook after the install" saved_outputs "$DEMO_HOME"

run_part part3-snippet
expect_cell "$DEMO_HOME" 0 "[(42,)]"
capture "Demo's saved notebook after the snippet" saved_outputs "$DEMO_HOME"

# The last step: nothing of the app survives, so the next launch reads only what is on the Space's
# disk. The flow re-runs the snippet cell, in a kernel started after the install.
"$ADB" shell am force-stop "$APP"
run_part part4-after-relaunch
expect_cell "$DEMO_HOME" 0 "[(42,)]"
capture "Demo's saved notebook after the relaunch" saved_outputs "$DEMO_HOME"

echo "A Space's glibc kernel installed duckdb from PyPI's manylinux wheel and ran its quickstart,"
echo "and after a relaunch the same cell answers again. Captures in $CAPTURES; screenshots in $EVIDENCE."
