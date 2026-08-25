#!/usr/bin/env bash
# P2w-2's layout half: what the kernel indicator keeps when the row cannot afford the kernel's name.
#
# NARROW WINDOW (<840dp): the emulator at 320dp. Free width in this action row is 72dp there and a
# status glyph beside "Python 3" wants about 110dp, so 320dp is the width that decides it; the
# tablet has 567dp free and would draw the name whatever the rule said. The mirror of `WIDE WINDOW`,
# and the rule docs/DECISIONS.md records.
#
# It runs no cell and makes no claim about one: on x86_64 there is no rootfs, and every assertion
# here is about placement and about the resting state of a notebook nobody has run.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p2w-2-kernel-indicator-narrow"
mkdir -p "$EVIDENCE"

width="$(window_width_dp)"
if [ "${width:-0}" -ge 840 ]; then
  echo "SKIP: this asserts the indicator at a narrow width; this window is ${width}dp"
  exit 0
fi

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2w-2-kernel-indicator.ipynb" "Kernel Indicator"

run_flow "$ROOT/.maestro/p2w-2-kernel-indicator-narrow.yaml" "$EVIDENCE" || exit 1

echo "P2w-2 layout gate passed at ${width}dp: the status stays on the bar and the name does not."
