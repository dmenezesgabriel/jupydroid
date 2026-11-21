#!/usr/bin/env bash
# The demo `%pip install` in a Space owes: pandas installs and imports (the control that always
# worked), duckdb and scikit-learn install from PyPI's manylinux wheels on the glibc kernel and
# answer, a package no release wheels for the kernel's platform gets the cell's own note saying
# why, a name that exists nowhere gets pip's text alone, and after the app is killed and
# relaunched the same packages still import from `pkgs-gnu`.
#
#   scripts/pip-install-in-a-space-driver.sh
#
# TABLET ONLY (SM-X216B): every part runs a cell, and on x86_64 the app falls back to
# `FakeKernelGateway`, which echoes the cell's source back as its output, so a green run there
# would measure nothing. Needs the tablet online: pip resolves against PyPI.
#
# **What it asserts.** The saved notebook, read back after each part: pip's own "Successfully
# installed" line and the cell's printed marker in the stream, the refusal as its own
# `display_data` output that never folds into pip's stream, pip's two ERROR lines for the typo
# with no platform claim anywhere, and the three versions printing again after a relaunch. The
# gnu user site's listing is captured beside it.
#
# NOT A SUITE FLOW: it clears base's workspace before every part, so the device keeps no notebook
# of an earlier part.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-078-t4}"
FLOWS="$ROOT/.maestro/pip-install-in-a-space"
CAPTURES="$EVIDENCE/captures.txt"
BASE="files/spaces/base"
BASE_HOME="$BASE/home/jupydroid"
NOTEBOOK="Untitled.ipynb"
# The glibc kernel's user base, where its pip's --user installs land - not the musl `pkgs/`.
GNU_SITE="pkgs-gnu/lib/python3.12/site-packages"
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

require_saved() { # require_saved <pattern> <what> - the saved notebook holds it
  local pattern="$1" what="$2" outputs
  outputs="$(saved_outputs "$BASE_HOME")"
  grep -qE "$pattern" <<<"$outputs" ||
    {
      echo "the saved notebook holds no $what" >&2
      echo "$outputs" >&2
      exit 1
    }
}

require_saved_absent() { # require_saved_absent <pattern> <what> - and nothing like it
  local pattern="$1" what="$2" outputs
  outputs="$(saved_outputs "$BASE_HOME")"
  if grep -qE "$pattern" <<<"$outputs"; then
    echo "the saved notebook holds $what, which it must not" >&2
    echo "$outputs" >&2
    exit 1
  fi
}

require_refusal() { # require_refusal <package> - the note is its own output and pip's stream is pip's
  local package="$1" outputs
  outputs="$(saved_outputs "$BASE_HOME")"
  grep -qF "${package} ships no manylinux aarch64 wheel for Python 3.12" <<<"$outputs" ||
    {
      echo "the saved notebook holds no platform note for $package" >&2
      echo "$outputs" >&2
      exit 1
    }
  if grep -E "^  stream " <<<"$outputs" | grep -qF "ships no manylinux"; then
    echo "the note folded into pip's own stream for $package, so the app's words read as pip's" >&2
    echo "$outputs" >&2
    exit 1
  fi
}

: >"$CAPTURES"
fresh_install "$EVIDENCE/00-cold-install"
reset_notebooks

# The control, and the two packages the gap named: each installs from PyPI and imports.
run_part part1-pandas
capture "the image base was extracted from" in_base 'cat .jupydroid/rootfs-version'
capture "pandas: base's saved notebook" saved_outputs "$BASE_HOME"
require_saved "Successfully installed.*pandas" "the control's install"
require_saved "PANDAS [0-9]" "the control's import marker"
require_saved_absent "ships no" "a refusal note for a package that installs"

run_part part2-duckdb
capture "duckdb: base's saved notebook" saved_outputs "$BASE_HOME"
require_saved "Successfully installed duckdb" "duckdb's install"
require_saved "\[\(42,\)\]" "duckdb's quickstart answer"

run_part part3-scikit-learn
capture "scikit-learn: base's saved notebook" saved_outputs "$BASE_HOME"
require_saved "Successfully installed.*scikit-learn" "scikit-learn's install"
require_saved "SKLEARN [0-9]" "scikit-learn's import marker"

# The other half of the outcome: pip finds no distribution and the cell says why, as its own
# output, only because PyPI's release files confirm no release wheels for the kernel's platform.
run_part part4-pywin32
capture "pywin32: base's saved notebook" saved_outputs "$BASE_HOME"
require_refusal pywin32

# A name that exists nowhere earns pip's own text and no platform claim. The note a confirmed
# miss would earn lands a moment after pip's text, so the silence is read once that moment is
# spent - a Maestro wait would return at once here.
run_part part5-a-typo
sleep 10
capture "duckdbzz: base's saved notebook" saved_outputs "$BASE_HOME"
require_saved "No matching distribution found for duckdbzz" "the typo's pip error"
require_saved_absent "ships no|manylinux aarch64 wheel" "a platform claim for a name that exists nowhere"

# Kill and relaunch: the packages are on the Space's disk, so a kernel started after it imports
# them with no second install.
"$ADB" shell am force-stop "$APP"
run_part part6-after-relaunch
capture "after the relaunch: base's saved notebook" saved_outputs "$BASE_HOME"
require_saved "already satisfied" "pip seeing pandas installed"
require_saved "VERSIONS [0-9]" "all three packages importing after the relaunch"

# The inner commands quote with double quotes only: `in_base` already wraps them in `sh -c '…'`,
# so a single quote or a bare `(` inside one ends the device shell's parsing early.
capture "the gnu user site after the installs" in_base "ls $GNU_SITE 2>/dev/null | grep -E \"^(pandas|duckdb|sklearn|scipy|numpy|joblib|threadpoolctl)\" | head -20"
capture "the musl user site" in_base "ls pkgs/lib/python3.12/site-packages 2>/dev/null || echo \"(absent)\""

echo "pandas, duckdb and scikit-learn installed from PyPI's manylinux wheels and import, pywin32's"
echo "cell says why in its own output, a typo gets pip's text alone, and after a relaunch the same"
echo "packages import from pkgs-gnu. Captures in $CAPTURES; screenshots, UI dumps and logcat in"
echo "$EVIDENCE."
