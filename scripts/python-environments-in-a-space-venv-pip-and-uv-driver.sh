#!/usr/bin/env bash
# A venv's own pip installs into the venv with no flag, base's %pip still lands in the
# Space's user base, and uv's two routes into a Space - apk and pip - measured under proot.
#
#   scripts/python-environments-in-a-space-venv-pip-and-uv-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs, and on x86_64 the app falls back to FakeKernelGateway.
# Needs the tablet online: pip, apk and uv all download.
#
# NOT A SUITE FLOW: it removes every user Space on the device.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/python-environments-in-a-space/venv-pip-and-uv}"
FLOWS="$ROOT/.maestro/python-environments-in-a-space"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

in_thesis() { "$ADB" shell "run-as $APP sh -c 'cd $THESIS && $1'" | tr -d '\r'; }

# The answer a terminal command wrote, once its last marker is there.
answer() { # answer <file> <last-marker>
  NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text "$1" "$2" 600
  capture "$1, as the terminal wrote it" "$ADB" shell "run-as $APP cat $THESIS_HOME/$1"
}

expect_line() { # expect_line <file> <fixed text>
  "$ADB" shell "run-as $APP cat $THESIS_HOME/$1" | tr -d '\r' | grep -qF -- "$2" ||
    { echo "expected '$2' in $1" >&2; exit 1; }
}

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part part1-create-thesis
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/python-environments-in-a-space-pip.ipynb" "Pip"

run_part part2-base-pip-cell
capture "Thesis's usr/pip.conf, written at its start" in_thesis 'cat usr/pip.conf'
capture "where %pip put tabulate: the user base, not the system site-packages" \
  in_thesis 'ls -d pkgs/lib/python3.12/site-packages/tabulate usr/lib/python3.12/site-packages/tabulate 2>&1'
[ -n "$(in_thesis 'ls -d pkgs/lib/python3.12/site-packages/tabulate 2>/dev/null')" ] ||
  { echo "%pip did not land tabulate in Thesis's pkgs/" >&2; exit 1; }

run_part part3-venv-pip
answer venv-pip.txt version-rc=
expect_line venv-pip.txt install-rc=0
expect_line venv-pip.txt version-rc=0
expect_line venv-pip.txt "from /home/jupydroid/envs/stats/lib/python3.12/site-packages/pip"
capture "six is in the venv alone" \
  in_thesis 'ls -d home/jupydroid/envs/stats/lib/python3.12/site-packages/six.py pkgs/lib/python3.12/site-packages/six.py usr/lib/python3.12/site-packages/six.py 2>&1'

run_flow "$FLOWS-venv-pip-missing-package.yaml" "$EVIDENCE/part4-missing-package" || exit 1
answer missing.txt missing-rc=
expect_line missing.txt missing-rc=1
expect_line missing.txt no-such-package

# uv is measured, not required: each route's status is captured whatever it is.
run_part part5-uv-from-apk
answer uv-apk.txt uv-rc=
run_part part6-uv-from-pip
answer uv-pip.txt uv-rc=
run_part part7-uv-venv
answer uv-venv.txt install-rc=
expect_line uv-venv.txt venv-rc=0
expect_line uv-venv.txt install-rc=0
run_part part8-static-uv-install
answer uv-static.txt install-rc=
run_part part9-static-uv-shown

echo "a venv's plain pip installed into the venv, base's %pip landed in pkgs/, Alpine's uv made and filled a venv; both uv routes are in $CAPTURES"
