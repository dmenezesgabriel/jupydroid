#!/usr/bin/env bash
# A tap on a notebook result opens the notebook **on the cell the match is in**.
#
# The fixture is shaped by what a weak version of this would pass against. The marker is in the
# **fifteenth** cell, so a notebook that opened at the top would not be showing it - and it sits
# under a **collapsed heading**, so a notebook that merely scrolled would not be showing it either.
# The flow opens the notebook from the browser first and asserts the marker is absent, which is
# what makes the second half mean something.
#
# WIDE WINDOW: the panel is drawn only where there is room for two panes, which the tablet's 1280dp
# window has and the emulator's 320dp does not.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# One notebook and one match, so the tap is unambiguous - a search reads *everything*, which makes
# a panel flow the one most exposed to a workspace it did not seed.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/open-at-the-cell.ipynb" "log"

run_flow "$ROOT/.maestro/open-at-the-cell.yaml" "$ROOT/evidence/open-at-the-cell"
