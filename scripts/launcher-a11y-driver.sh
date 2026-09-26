#!/usr/bin/env bash
# Launcher accessibility gate: the cards, borders and cwd line in both themes - kernel-free,
# so either device can take it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks

# `reports/` with one notebook inside is what makes the subdir-cwd shots possible.
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"

run_flow "$ROOT/.maestro/launcher-a11y.yaml" "$ROOT/evidence/launcher-a11y"
