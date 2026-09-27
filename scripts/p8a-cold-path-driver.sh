#!/usr/bin/env bash
# P8a: the cold path, measured rather than asserted. One run is one app launch under a known
# initial state, and the measurement is the `JupydroidSpace` timeline in the promoted logcat -
# `cold_path_report.py` reads it into a stage-duration table.
#
#   scripts/p8a-cold-path-driver.sh cold r1   # `pm clear` first: the rootfs extract is in the path
#   scripts/p8a-cold-path-driver.sh warm r1   # force-stop only: install is the stamp-check no-op
#
# The label is the run's name, so a mode's repetitions land beside each other in evidence/ and
# never overwrite: `evidence/p8a-cold-path/<mode>-<label>/`.
#
# TABLET ONLY: the emulator's FakeKernelGateway answers without a Space, so its "timings" would be
# of a path that cannot exist on that device at all (`SpaceAbi` - arm64 only, and deliberately).
# NOT A SUITE FLOW: it is a measurement driver, and `pm clear` mid-sweep would empty the workspace
# every other flow was about to read.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODE="${1:-}"
LABEL="${2:-}"
case "$MODE" in
  cold|warm) ;;
  *) echo "usage: p8a-cold-path-driver.sh <cold|warm> <label>" >&2; exit 2 ;;
esac
[ -n "$LABEL" ] || { echo "usage: p8a-cold-path-driver.sh $MODE <label>" >&2; exit 2; }

# The evidence root is a knob rather than a constant so the same harness re-measures a
# repaired stage under its own step's directory (P8c-1 reran this under
# evidence/p8c-1-extraction/).
EVIDENCE="${P8A_EVIDENCE_ROOT:-$ROOT/evidence/p8a-cold-path}/$MODE-$LABEL"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

require_installed_app

# The two initial states the question splits into. `cold` clears the package, so the extracted
# rootfs goes with it and `BaseSpaceInstaller` runs the real extraction inside the measured
# launch; `warm` stops the process only, so install is the version-stamp no-op and what is
# measured is process + server + kernel + terminal on a Space that already exists.
if [ "$MODE" = "cold" ]; then
  "${DEVICE_ADB[@]}" shell pm clear "$APP"
fi

# The fixture the flow opens and runs. `reset_notebooks` before the seed in both modes - a cold
# `pm clear` already emptied everything but writes no `launcherSeen`, and a warm run on an unknown
# workspace should not depend on what a previous step happened to leave. The reset seeds
# `launcherSeen` itself: first-run chrome is a presentation question, not the runtime path this
# measures.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p8a-cold-path.ipynb" "Cold Path"

# A cleared buffer, so the promoted logcat is *this* run's timeline and nothing before it.
"${DEVICE_ADB[@]}" logcat -c

run_flow "$ROOT/.maestro/p8a-cold-path.yaml" "$EVIDENCE"

# The logcat's `terminal.ready` is the socket-side mark; this is the end-to-end one - the shell
# actually executed a command in the workspace.
wait_for_workspace_text "cp-term.txt" "coldpath-ready" 90
