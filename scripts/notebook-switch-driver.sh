#!/usr/bin/env bash
# Seeds two notebooks and runs the notebook-switch flow.
#
# Two fixtures, not one, and their cell sources share no words: the flow's closing assertion is
# that the *second* notebook's cell is on screen and the first one's is not, which only means
# something if neither string can match the other notebook.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

# Reset once, then seed twice: `seed_notebook` seeds one notebook and clears nothing, which is
# exactly what a flow needing two of them requires.
#
# Written out rather than looped over `$name`, and the gate is the reason: a fixture path with a
# shell variable in it is one `check_maestro_output_assertions.py` can match but not read, and a
# fixture it cannot read makes rule 1 pass without ever being applied. Two lines, no variable.
reset_notebooks
# DiskNotebookRepository derives the notebook id from the filename stem.
seed_notebook "$ROOT/.maestro/fixtures/switch-alpha.ipynb" switch-alpha
seed_notebook "$ROOT/.maestro/fixtures/switch-beta.ipynb" switch-beta

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator and a
# tablet attached it picks one of its own accord and the fixtures are on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/notebook-switch.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/notebook-switch.yaml"
fi
