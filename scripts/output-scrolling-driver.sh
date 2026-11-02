#!/usr/bin/env bash
# `notebook:enable/disable-output-scrolling`, dispatched over a marked run of two cells -
# `isOutputScrollingEnabled`'s all-of rule, not the head-of rule the other folds use.
#
# No cell is run; both fixtures carry their outputs already saved. Says the same thing on either
# device and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/enable-output-scrolling.ipynb" "Turn Scrolling On"
seed_notebook "$ROOT/.maestro/fixtures/disable-output-scrolling.ipynb" "Turn Scrolling Off"

run_flow "$ROOT/.maestro/output-scrolling.yaml" "$ROOT/evidence/output-scrolling"

# What reached disk, which the screen cannot say: `scrolled` on both cells after enabling from
# the marked run, and neither cell carrying the key at all after the same row disables them.
echo "--- Turn Scrolling On.ipynb, after the flow enabled both cells ---"
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Turn Scrolling On.ipynb\""
echo "--- Turn Scrolling Off.ipynb, after the flow disabled both cells ---"
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Turn Scrolling Off.ipynb\""
