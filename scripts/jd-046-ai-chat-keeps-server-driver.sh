#!/usr/bin/env bash
# An open Chat keeps base's server from stopping itself: with the idle setting at 1 minute, the
# pre-started kernel is culled and the server stays up, because the panel touches `api/me`.
#
#   nice -n 10 timeout 1200 scripts/jd-046-ai-chat-keeps-server-driver.sh [after|before]
#
# Part 1 sets the idle to 1 minute and opens Chat; this driver waits past the kernel's cull and the
# server's next idle check (jupyter_server 2.20.0 `serverapp.py` `shutdown_no_activity`, every
# 60 s); part 2 checks the panel; part 3 puts the setting back. Exits non-zero unless the server
# log holds the cull, at least two `200 GET /api/me` and no "shutting down". `before` names the
# evidence directory for a run on a build without the heartbeat, which is expected to fail.
#
# TABLET ONLY (SM-X216B): only arm64 ships a Space with a server to stop.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"
# shellcheck source=scripts/jd-046-chat-reads.sh
. "$ROOT/scripts/jd-046-chat-reads.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/t5b/${1:-after}"
# The cull at 1 to 2 minutes after the start, then one more 60 s check past a 60 s timeout.
IDLE_WAIT_SECONDS=270

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
mkdir -p "$EVIDENCE"
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $SPACE_RUN_DIR/jupyter_server.log"
rotate_to 1

status=0
# One directory per part, since each run writes its own maestro.log and logcat.txt.
run_part() { run_flow "$ROOT/.maestro/jd-046-ai-chat-keeps-server-part$1.yaml" "$EVIDENCE/part$1"; }

run_part 1 || status=$?
[ "$status" -eq 0 ] && sleep "$IDLE_WAIT_SECONDS"
[ "$status" -eq 0 ] && { run_part 2 || status=$?; }
pull_server_log "$EVIDENCE/jupyter_server.log"
run_part 3 || fail "the idle setting was not put back"

require_server_kept_up "$EVIDENCE" || exit 1
[ "$status" -eq 0 ] || fail "a flow failed ($status)"
