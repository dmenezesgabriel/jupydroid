#!/usr/bin/env bash
# Runs a code cell, then turns it into a raw one from the cell menu.
#
# TABLET ONLY (SM-X216B). The cell has to reach a real kernel or there is no `42` to watch
# disappear; on the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the
# source, and the flow would prove only that an echo can be cleared.
# scripts/run-all-flows.sh reads this marker and skips the flow there rather than failing it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/change-to-raw.ipynb" "Change To Raw"

run_flow "$ROOT/.maestro/change-to-raw.yaml" "$ROOT/evidence/change-to-raw"

# What reached disk, which the screen cannot say: `cell_type` is `raw`, the two keys the schema
# forbids a raw cell are absent, and the cell's `tags` - which this app does not model - are still
# there, because a change of type goes through the same preserved-JSON overlay every other edit does.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Change To Raw.ipynb\""
