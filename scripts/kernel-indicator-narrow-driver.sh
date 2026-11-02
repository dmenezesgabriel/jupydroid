#!/usr/bin/env bash
# The layout half of `kernel-indicator-states`: what the kernel indicator keeps when the row cannot afford the kernel's name.
#
# COMPACT WINDOW (<600dp): the emulator at 320dp. Free width in this action row is 72dp there and a
# status glyph beside "Python 3" wants about 110dp, so 320dp is the width that decides it; the
# tablet has 567dp free and would draw the name whatever the rule said.
#
# **Not NARROW WINDOW**, which this driver declared until the 800dp sweep found otherwise, and
# which means <840dp - so it also admits the tablet in portrait at **800dp**, a *medium* window
# where `kernelNameOnBar` is true and `assertNotVisible: "Python 3"` is false by design. The rule
# is that the name goes *below 600dp*, so the boundary this flow asserts is the medium one and
# never 840dp. It passed for as long as it did only because the sharder had no <840dp device but
# the emulator to give it; the sweep's portrait pass gave it one and it went red immediately.
#
# `cell-type-in-the-overflow-driver.sh` had already written this reasoning down for the sibling
# control on the same row - "a window where the bar *does* carry the control and this flow's first
# assertion would be false". This driver simply had not followed it.
#
# It runs no cell and makes no claim about one: on x86_64 there is no rootfs, and every assertion
# here is about placement and about the resting state of a notebook nobody has run.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/kernel-indicator-narrow"
mkdir -p "$EVIDENCE"

width="$(window_width_dp)"
if [ "${width:-0}" -ge 840 ]; then
  echo "SKIP: this asserts the indicator at a narrow width; this window is ${width}dp"
  exit 0
fi

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/kernel-indicator-states.ipynb" "Kernel Indicator"

run_flow "$ROOT/.maestro/kernel-indicator-narrow.yaml" "$EVIDENCE" || exit 1

echo "kernel-indicator-narrow gate passed at ${width}dp: the status stays on the bar and the name does not."
