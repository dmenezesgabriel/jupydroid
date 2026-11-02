#!/usr/bin/env bash
# At one pane: the bottom bar's More opens the modal rail with every contributed panel between New
# terminal and Settings, each row fills the pane with its panel, the showing panel's row is the
# selected one, and Back from it selects Files (ADR-0109, `MoreRail`).
# Pinned to portrait, which is one pane on SM-X216B, and restored to auto-rotate on exit. With the
# rail open, over Files and then over Source Control, every named node's bounds are read from the
# window's hierarchy, in dp (SM-X216B draws 1.5 px to the dp, 240 dpi), and the hierarchy is kept,
# since it carries each row's `selected`.
#
# Drives the tablet only: Source Control asks base's real `jupyter_server`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"   # rotate_to, restore_rotation, dump_named_bounds

OUT="$ROOT/evidence/contributed-panels-from-more"

trap restore_rotation EXIT
rotate_to 0
run_flow "$ROOT/.maestro/contributed-panels-from-more-part1-rail-rows.yaml" "$OUT/rail-rows" || exit 1
dump_named_bounds "$OUT/rail-rows"
run_flow "$ROOT/.maestro/contributed-panels-from-more-part2-source-control.yaml" "$OUT/source-control" || exit 1
dump_named_bounds "$OUT/source-control"
run_flow "$ROOT/.maestro/contributed-panels-from-more-part3-chat-and-back.yaml" "$OUT/chat-and-back" || exit 1
