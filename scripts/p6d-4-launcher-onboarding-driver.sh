#!/usr/bin/env bash
# P6d-4 gate, one-pane half: the first run pushes the launcher, and `launcherSeen` is what keeps it
# a once-only act - proven by relaunching onto the browser rather than the launcher again.
# COMPACT WINDOW (<600dp): the push is a one-pane rule; at two panes the empty main area fills
# unconditionally instead, which is what the refill driver proves.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -ge 600 ]; then
  echo "this flow must run on a compact (<600dp) window; $WIDTH_DP is not one" >&2
  exit 1
fi

reset_notebooks
# `reset_notebooks` seeds launcherSeen=true so the suite stays returning-users; this flow wants the
# file absent, which is the state a fresh install actually launches into.
"$ADB" shell "run-as $APP sh -c 'rm -f \"$LAUNCHER_SETTINGS_STORE\"'" || true

run_flow "$ROOT/.maestro/p6d-4-launcher-onboarding.yaml" "$ROOT/evidence/p6d-4-launcher-onboarding"
