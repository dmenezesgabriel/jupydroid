#!/usr/bin/env bash
# JD-009 T2: a Space's own environment variables - added in Settings with a derived name refused
# by name, stored in the Space's config.json, and laid under the derived set so the server, its
# kernels and terminals, and pylsp all carry them.
#
#   scripts/jd-009-configure-a-space-t2-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs and pylsp must be real, and on x86_64 the app falls back
# to FakeKernelGateway.
#
# NOT A SUITE FLOW: it removes every user Space on the device, and base's config.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-009-configure-a-space/t2-environment-variables}"
FLOWS="$ROOT/.maestro/jd-009-configure-a-space-t2"
CAPTURES="$EVIDENCE/captures.txt"
THESIS_HOME="files/spaces/space-1/home/jupydroid"
mkdir -p "$EVIDENCE"

# The environment a Space's process was launched with, one NAME=value per line. environ is
# NUL-separated, and the proot tracer is the app's own uid, so run-as reads it - the tracer's copy
# is exactly what ProcessBuilder set, user variables and the derived set together.
env_of() { # env_of <space-id> <cmdline-pattern>
  local pid
  pid=$("$ADB" shell "ps -A -o PID,CMDLINE" | tr -d '\r' |
    grep 'libproot.so' | grep -- "files/spaces/$1 " | grep "$2" | awk '{print $1}' | sed -n 1p)
  [ -n "$pid" ] || { echo "no proot process matching '$2' under $1" >&2; return 1; }
  "$ADB" shell "run-as $APP cat /proc/$pid/environ" | tr '\0' '\n'
}

expect_env() { # expect_env <space-id> <cmdline-pattern> <NAME=value>
  env_of "$1" "$2" | grep -qx "$3" || { echo "expected '$3' in the $2 environment of $1" >&2; exit 1; }
}

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part part1-variables-in-settings
capture "Thesis's config.json after the save" "$ADB" shell run-as "$APP" cat files/spaces/space-1/config.json

# Into Thesis's home alone, where the kernel's HOME is: seeding force-stops the app, which is why
# part 2 rather than part 1 runs Thesis.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/jd-009-env.ipynb" "Env"

run_part part2-kernel-and-pylsp
# Both processes still live - the flow ends with the document open and the app in front, and a
# Space's processes die with the app, so they are captured before anything can leave.
capture "the server's environment" env_of space-1 'jupyter_server'
capture "pylsp's environment" env_of space-1 'pylsp'
expect_env space-1 'jupyter_server' 'GREETING=hello'
expect_env space-1 'pylsp' 'GREETING=hello'
# The derived set is still the one written: HOME is the Space's own workspace, and PATH is the
# Space's, not Android's.
expect_env space-1 'jupyter_server' 'HOME=/home/jupydroid'

run_part part3-terminal-sees-it
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text greeting-env.txt hello
capture "what the shell opened in Thesis read GREETING as" \
  "$ADB" shell "run-as $APP cat $THESIS_HOME/greeting-env.txt"

echo "Thesis's Settings stored GREETING=hello, a HOME row was refused by name, and the server, a kernel, a terminal and pylsp all carry it"
