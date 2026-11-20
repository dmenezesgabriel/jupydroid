#!/usr/bin/env bash
# The demo, on one tablet run: a cold install of the build, base entered and a notebook made;
# `import tabulate` fails; `%pip install tabulate`; the cell below imports it out of the Space's
# own user base; a Space made afterwards installs and imports pandas; and after the app is killed
# and relaunched the same cell imports tabulate with no second install.
#
#   scripts/pip-import-in-the-next-cell-driver.sh
#
# TABLET ONLY (SM-X216B): every part runs a cell, and on x86_64 the app falls back to
# `FakeKernelGateway`, which echoes a cell's source back as its output. Needs the tablet online:
# pip downloads tabulate and pandas.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-077-t3}"
FLOWS="$ROOT/.maestro/pip-import-in-the-next-cell"
CAPTURES="$EVIDENCE/captures.txt"
BASE="files/spaces/base"
BASE_HOME="$BASE/home/jupydroid"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
NOTEBOOK="Untitled.ipynb"
USER_SITE="pkgs/lib/python3.12/site-packages"
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

in_base() { in_space "$BASE" "$1"; }

in_thesis() { in_space "$THESIS" "$1"; }

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

require_user_site() { # require_user_site <space dir> <package> - pip's --user target, not the image
  [ -n "$(in_space "$1" "ls -d $USER_SITE/$2 2>/dev/null")" ] ||
    { echo "pip did not put $2 in $1/$USER_SITE" >&2; exit 1; }
  [ -z "$(in_space "$1" "ls -d usr/lib/python3.12/site-packages/$2 2>/dev/null")" ] ||
    { echo "$1/usr/lib/python3.12/site-packages holds $2: the install did not land in the user base" >&2; exit 1; }
}

fresh_install "$EVIDENCE/00-cold-install"
reset_notebooks
: >"$CAPTURES"

run_part part1-create
# The repair is in the image, and a build from before it would make every frame below green
# against nothing: the hook is what creates the user site an install lands in (ADR-0164).
capture "the image base was extracted from" in_base 'cat .jupydroid/rootfs-version'
capture "the hook that image ships" in_base "ls -l usr/lib/python3.12/site-packages/jupydroid_usersite.pth"
[ "$(in_base 'cat .jupydroid/rootfs-version')" = "22" ] ||
  { echo "base was extracted from an image other than 22: the hook below is not the repair" >&2; exit 1; }
[ -n "$(in_base 'ls usr/lib/python3.12/site-packages/jupydroid_usersite.pth 2>/dev/null')" ] ||
  { echo "the image base carries has no jupydroid_usersite.pth" >&2; exit 1; }
capture "base's user base before anything is installed" in_base 'ls -R pkgs'

run_part part2-import-fails
expect_cell "$BASE_HOME" 0 "ModuleNotFoundError: No module named 'tabulate'"
capture "base's saved notebook after the failed import" saved_outputs "$BASE_HOME"
capture "base's user base after the failed import" in_base 'ls -R pkgs'

run_part part3-install
capture "where pip put tabulate" in_base "ls -d $USER_SITE/tabulate usr/lib/python3.12/site-packages/tabulate 2>&1"
require_user_site "$BASE" tabulate
expect_cell "$BASE_HOME" 1 "Successfully installed tabulate"

run_part part4-import-resolves
expect_cell "$BASE_HOME" 2 "/pkgs/lib/python3.12/site-packages/tabulate/__init__.py"
capture "base's saved notebook after the cell below the install" saved_outputs "$BASE_HOME"

run_part part5-fresh-space-install
expect_cell "$THESIS_HOME" 0 "Successfully installed pandas"

run_part part6-fresh-space-import
expect_cell "$THESIS_HOME" 1 "/pkgs/lib/python3.12/site-packages/pandas/__init__.py"
capture "Thesis's saved notebook" saved_outputs "$THESIS_HOME"
capture "where pip put pandas in Thesis" in_thesis "ls -d $USER_SITE/pandas 2>&1"
require_user_site "$THESIS" pandas

# The last step: nothing of the app survives, so the next launch reads only what is on the Space's
# disk. The flow re-runs the cell that raised, in a kernel started after the install.
"$ADB" shell am force-stop "$APP"
run_part part7-after-relaunch
expect_cell "$BASE_HOME" 0 "/pkgs/lib/python3.12/site-packages/tabulate/__init__.py"
capture "base's saved notebook after the relaunch" saved_outputs "$BASE_HOME"

echo "The cell that raised before the install imports tabulate out of the Space's user base in the"
echo "kernel below it and again after a relaunch, and a Space made afterwards installs and imports"
echo "pandas. Captures in $CAPTURES; screenshots in $EVIDENCE."
