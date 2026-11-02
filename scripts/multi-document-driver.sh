#!/usr/bin/env bash
# Two open notebooks hold two live kernel sessions - the multi-document model on a real
# Space. The flow opens one notebook, runs its cell, opens a second beside it, runs its cell,
# and taps the first again; this driver then counts the distinct session ids the promoted logcat
# reports on `kernel.session.opened` - two, not one swapped and not a third minted on reveal.
#
#   scripts/multi-document-driver.sh <label>   # evidence/multi-document/<label>/
#
# TABLET ONLY: on the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes
# the cell's source - there are no real sessions to count (`SpaceAbi` - arm64 only).
# NOT A SUITE FLOW: measurement driver; it reseeds the workspace itself.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

LABEL="${1:-}"
[ -n "$LABEL" ] || { echo "usage: multi-document-driver.sh <label>" >&2; exit 2; }

EVIDENCE="$ROOT/evidence/multi-document/$LABEL"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

require_installed_app

# `reset_notebooks` before the seeds - the driver-isolation gate's rule, and `launcherSeen` comes
# with the reset, which is also what keeps the launcher fill out of the measurement.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/multi-document-first.ipynb" "multi-document-first"
seed_notebook "$ROOT/.maestro/fixtures/multi-document-second.ipynb" "multi-document-second"

"${DEVICE_ADB[@]}" logcat -c

run_flow "$ROOT/.maestro/multi-document.yaml" "$EVIDENCE"

# The count that is the point: two `kernel.session.opened` lines, two distinct session ids. A
# single session for both notebooks - or a third minted when the first was revealed - fails here.
sessions="$(
  grep -o '"kernel\.session\.opened"[^}]*' "$EVIDENCE/logcat.txt" |
    grep -o '"sessionId":"[^"]*"' |
    sort -u
)"
count="$(printf '%s\n' "$sessions" | grep -c .)"
printf 'kernel.session.opened distinct sessionIds: %s\n%s\n' "$count" "$sessions" | tee "$EVIDENCE/sessions.txt"
[ "$count" = "2" ] || { echo "expected 2 kernel sessions, got $count" >&2; exit 1; }
