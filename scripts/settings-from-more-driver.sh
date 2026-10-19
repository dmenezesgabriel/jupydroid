#!/usr/bin/env bash
# At one pane: the bottom bar's More opens the modal rail with New terminal in it and Settings at its
# foot, and Settings opens from it with Files, Search and Sessions and Tabs showing (ADR-0109).
# Pinned to portrait, which is one pane on SM-X216B, and restored to auto-rotate on exit. With the
# rail open, every named node's bounds are read from the window's hierarchy, in dp (SM-X216B draws
# 1.5 px to the dp, 240 dpi).
#
# Then both arms of the shared reaches, at both widths. Drives the tablet only: the terminal it
# opens is a real shell there.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"   # rotate_to, restore_rotation, dump_named_bounds

OUT="$ROOT/evidence/settings-from-more"

trap restore_rotation EXIT
rotate_to 0
run_flow "$ROOT/.maestro/settings-from-more-part1-rail-open.yaml" "$OUT/rail-open" || exit 1

# Every node with a name, top to bottom, in dp - the rail's two rows and the bar's five items among
# them - and how many answer to "Settings".
dump_named_bounds "$OUT/rail-open"

run_flow "$ROOT/.maestro/settings-from-more-part2-from-every-panel.yaml" "$OUT/from-every-panel" || exit 1

# Both arms of the shared reaches every other flow now goes through (`open-new-terminal.yaml`,
# `open-settings.yaml`): the terminal at one pane and at two, and Settings from the rail at two.
run_flow "$ROOT/.maestro/p6d-6-terminal-reach.yaml" "$OUT/terminal-reach-one-pane" || exit 1
rotate_to 1
run_flow "$ROOT/.maestro/p6d-6-terminal-reach.yaml" "$OUT/terminal-reach-two-panes" || exit 1
run_flow "$ROOT/.maestro/p6c-1-settings-screen.yaml" "$OUT/settings-two-panes" || exit 1
