#!/usr/bin/env bash
# Seeds a markdown cell holding prose, inline code and a fenced block, over a code cell holding a
# saved stream output, then captures the frame where the reference's two code rules meet.
#
# **The evidence is a pair, because the change is a size.** Rendered markdown's code was
# `bodySmall`; it takes the prose size and the code line height now, which is `font-size: inherit`
# (`packages/rendermime/style/base.css:407-417`). Run once on the build before the change and once
# after, into `before/` and `after/`.
#
# **Captured at phone geometry rather than the AVD's own.** `dev36` is 320x640px at density 160, so
# it draws 1px per dp and a type-size judgement made there is made on proportions no reader will
# see (`docs/DECISIONS.md`). The driver overrides the window to 1080x2400 at 420dpi - Pixel-class,
# 411dp - and resets it afterwards. Nothing else about the flow depends on the size.
#
# No cell is run: a markdown cell renders on open and the output is read from the file, so this
# needs no Space runtime and either device will do.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p2l-3b-rendered-code.ipynb"
FLOW="$ROOT/.maestro/p2l-3b-rendered-code.yaml"
# The half of the pair this run is capturing. `after` unless the caller says otherwise, because
# `before` can only be captured deliberately - from a checkout that predates the change.
EVIDENCE="$ROOT/evidence/p2l-3b-rendered-code/${1:-after}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)
reset_window() { "${adb[@]}" shell wm size reset >/dev/null 2>&1 || true; "${adb[@]}" shell wm density reset >/dev/null 2>&1 || true; }
trap reset_window EXIT

"${adb[@]}" shell wm size 1080x2400 >/dev/null
"${adb[@]}" shell wm density 420 >/dev/null

reset_notebooks
seed_notebook "$FIXTURE" "Rendered Code"
run_flow "$FLOW" "$EVIDENCE"

echo
echo "screenshots: $EVIDENCE/"
