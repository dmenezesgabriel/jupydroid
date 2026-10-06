#!/usr/bin/env bash
# JD-005 T4: terminals and pylsp bind to the entered Space - a shell opened in Thesis lists
# Thesis's own home and writes into it, the diagnostics come from a pylsp under Thesis's root with
# none under base's, and leaving the app idle stops every pylsp.
#
#   scripts/jd-005-enter-a-running-space-t4-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs and pylsp must be real, and on x86_64 the app falls back
# to FakeKernelGateway.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-005-enter-a-running-space/t4-terminals-lsp-bind}"
FLOWS="$ROOT/.maestro/jd-005-enter-a-running-space-t4"
CAPTURES="$EVIDENCE/ps.txt"
THESIS_HOME="files/spaces/space-1/home/jupydroid"
BASE_HOME="files/spaces/base/home/jupydroid"
mkdir -p "$EVIDENCE"

# The proot tracer of each Space's pylsp: the one line per process that names its root.
language_servers() {
  "$ADB" shell "ps -A -o PID,PPID,RSS,CMDLINE" | grep 'libproot.so .*pylsp' || true
}

language_servers_under() { language_servers | grep -c -- "files/spaces/$1 " || true; }

expect_language_servers() { # expect_language_servers <space-id> <count>
  local seen
  seen="$(language_servers_under "$1")"
  [ "$seen" = "$2" ] || { echo "expected $2 pylsp under $1, saw $seen" >&2; exit 1; }
}

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

# The grace is 30 s (AppContainer's IDLE_STOP_GRACE); jd-004's driver measured 45 s as enough.
LEFT_IDLE_WAIT=45

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
"$ADB" shell "run-as $APP rm -f $BASE_HOME/terminal-saw"
reset_notebooks
: >"$CAPTURES"

# Thesis is created exactly as T3 created it, so the flow is T3's own.
run_flow "$ROOT/.maestro/jd-005-enter-a-running-space-t3-part1-create-thesis.yaml" "$EVIDENCE/part1-create-thesis" ||
  exit 1

# Into Thesis's home alone: the marker is what the shell must list, and base's home lacks it. A
# seed force-stops the app, which is why part 2 runs Thesis rather than part 1.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/p5d2-diagnostics.ipynb" "Diagnostics"
NOTEBOOK_DIR="$THESIS_HOME" seed_file "$ROOT/.maestro/fixtures/thesis-only.txt" "thesis-only.txt"
! "$ADB" shell run-as "$APP" test -e "$BASE_HOME/thesis-only.txt" ||
  { echo "base's home holds the marker; the shell's listing would prove nothing" >&2; exit 1; }

run_flow "$FLOWS-part2-shell-and-lsp-in-thesis.yaml" "$EVIDENCE/part2-shell-and-lsp-in-thesis" || exit 1

NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text terminal-saw thesis-only.txt
capture "what the shell opened in Thesis listed in ~" "$ADB" shell "run-as $APP cat $THESIS_HOME/terminal-saw"
! "$ADB" shell run-as "$APP" test -e "$BASE_HOME/terminal-saw" ||
  { echo "the shell opened in Thesis wrote into base's home" >&2; exit 1; }

capture "after the diagnostics in Thesis" language_servers
expect_language_servers space-1 1
expect_language_servers base 0

sleep "$LEFT_IDLE_WAIT"
capture "${LEFT_IDLE_WAIT} s after leaving the app idle" language_servers
expect_language_servers space-1 0
expect_language_servers base 0

echo "Thesis's shell ran in Thesis's home, Thesis's pylsp marked its notebook, and the idle stop left no pylsp"
