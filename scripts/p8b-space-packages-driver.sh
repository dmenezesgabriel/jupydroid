#!/usr/bin/env bash
# P8b: `pip install` grounds in the Space's own user base - the user's report, measured.
#
#   scripts/p8b-space-packages-driver.sh
#
# The flow itself proves the user-visible half: `pip install tabulate` unflagged in the
# terminal, then a kernel cell resolving `tabulate` out of `pkgs/`. What this driver adds is
# where it *landed*: rc=0 in the marker file, "Successfully installed" in the log, the package
# under pkgs/, and the system site-packages' top level untouched.
#
# TABLET ONLY: the x86_64 emulator runs no Space, so there is no pip and the kernel cell would
# be the FakeKernelGateway echoing the source.
#
# Needs the tablet online - the same constraint any `pip install` carries.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# EVIDENCE_ROOT lets a later step re-run this gate under its own name, as p7e-1's driver allows.
EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/p8b-space-packages}"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

require_installed_app

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p8b-space-packages.ipynb" "Cold Path"

# A previous run's install must not make this one's "Requirement already satisfied" pass as a
# fresh landing - the assertion is where it lands, so it has to land.
"${DEVICE_ADB[@]}" shell "run-as $APP rm -rf files/spaces/base/pkgs files/spaces/base/home/jupydroid/p8b-install.log files/spaces/base/home/jupydroid/p8b-pip.rc" || true

run_flow "$ROOT/.maestro/p8b-space-packages.yaml" "$EVIDENCE"

# The marker file is what the flow waited on; its contents are the rc pip exited with.
wait_for_workspace_text "p8b-pip.rc" "rc=0" 30
wait_for_workspace_text "p8b-install.log" "Successfully installed tabulate" 10

# Where it landed: the Space's user base, and only there. The system site-packages' top level
# is searched at maxdepth 1 because jedi ships typeshed *stubs* for tabulate deeper down - a
# stub is not an install.
"${DEVICE_ADB[@]}" shell "run-as $APP test -d files/spaces/base/pkgs/lib/python3.12/site-packages/tabulate" ||
  { echo "tabulate is not under pkgs/" >&2; exit 1; }
echo "tabulate landed in files/spaces/base/pkgs/"

leftovers=$("${DEVICE_ADB[@]}" shell "run-as $APP find files/spaces/base/usr/lib/python3.12/site-packages -maxdepth 1 -name 'tabulate*'" | tr -d '\r')
[ -z "$leftovers" ] || { echo "the system site-packages picked up the install: $leftovers" >&2; exit 1; }
echo "usr/lib/python3.12/site-packages untouched at its top level"
