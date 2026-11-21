#!/usr/bin/env bash
# A contributed surface's read against a stalled Space server used to kill the app: Chat's
# list asks the server for the workspace's files inside a Compose `LaunchedEffect`, a `.chat`
# document asks it for the transcript, and the raw `SocketTimeoutException` that `callWithToken`
# lets escape was nobody's to catch - it reached the main thread's uncaught handler and the
# process died. This drive reproduces that on the real Space, once per surface, and leaves the
# evidence that names it: the app's pid after each drive, the platform's exit record, and the
# app's own error log.
#
#   nice -n 10 timeout 1800 scripts/stalled-server-surfaces-driver.sh
#
# The stall is the one git-panel-states-driver.sh uses: SIGSTOP on base's proot freezes every
# tracee at its next ptrace stop while `Process.isAlive` still says the Space runs, so
# `SpaceServer.connection` hands out the cached port and each request waits out OkHttp's read
# timeout. Source Control's panel lands on its failed state with a Retry - proof the freeze
# took and that surface's port wraps its failures - while the chat surfaces used to take the
# process down instead.
#
# Two scenarios, one install: the chat panel first (a fresh install, so its error log holds
# only this run), then a `.chat` document seeded into the same workspace - the panel's crash
# leaves the workspace untouched, so the second scenario needs no reinstall and the pulled log
# ends up holding both crashes, which is the point.
#
# **The app dying is the measurement, not a harness failure.** Each scenario's second flow
# status is recorded rather than gated on: against the unfixed app maestro reports it failed
# because the process is gone, and the pid and log pulls are what say so. The same drive after
# the repair must end with the app alive and the surface saying its read failed - so this
# exits 0 whichever way the app went, and the evidence directories are the verdict.
#
# Exits non-zero on a harness failure: no tablet, no APK, no Space server to freeze, or a
# scenario's healthy baseline not showing.
#
# TABLET ONLY (SM-X216B): the surfaces ask a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/stalled-server-surfaces"

mapfile -t DEVICE_ADB < <(device_adb)

# pull-app-logs.sh reads ADB and ANDROID_SERIAL from the environment, not the arrays above.
export ADB
if [ -n "${MAESTRO_DEVICE:-}" ]; then export ANDROID_SERIAL="$MAESTRO_DEVICE"; fi

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
rotate_to 1

# What a scenario leaves behind, in the order a person would read it: is the process there,
# and what does the platform say killed the last one. `pidof` exits 1 on a dead app, which
# pipefail would read as the driver's own failure.
scenario_evidence() { # scenario_evidence <dir>
  local dir="$1"
  { app_pid || true; } | tee "$dir/app-pid-after.txt"
  "${DEVICE_ADB[@]}" shell "dumpsys activity exit-info $APP" >"$dir/exit-info.txt" 2>/dev/null || true
}

verdict() { # verdict <dir> <surface>
  local dir="$1" surface="$2"
  if [ -s "$dir/app-pid-after.txt" ]; then
    echo "stalled-server-surfaces: $surface survived - pid $(cat "$dir/app-pid-after.txt")"
  else
    echo "stalled-server-surfaces: $surface died - the crash reproduced; see $dir"
  fi
}

chat_panel_scenario() {
  local out="$EVIDENCE/chat-panel"
  mkdir -p "$out"
  fresh_install "$out"
  # The healthy baseline: Chat's list answers against the live server, so `New chat` shows.
  run_flow "$ROOT/.maestro/stalled-server-surfaces-chat-panel-part1-healthy-chat.yaml" "$out" || return 1
  # The stall. Everything part 2 asks of the server now waits out OkHttp's read timeout.
  freeze_space "$out" || return 1
  local status=0
  run_flow "$ROOT/.maestro/stalled-server-surfaces-chat-panel-part2-chat-again.yaml" "$out" || status=$?
  echo "chat panel part 2 flow status: $status" | tee "$out/part2-status.txt"
  scenario_evidence "$out"
  # Let the orphaned Space finish rather than leaving it stopped on the tablet.
  resume_space "$out" || true
  verdict "$out" "the chat panel"
}

chat_document_scenario() {
  local out="$EVIDENCE/chat-document"
  mkdir -p "$out"
  # The panel's crash left the install as it was; the `.chat` is the only addition the second
  # scenario needs, on a workspace cleared first so a leftover file cannot crowd the listing.
  reset_notebooks
  seed_file "$ROOT/.maestro/fixtures/talk.chat" "talk.chat"
  run_flow "$ROOT/.maestro/stalled-server-surfaces-chat-document-part1-files-listed.yaml" "$out" || return 1
  freeze_space "$out" || return 1
  local status=0
  run_flow "$ROOT/.maestro/stalled-server-surfaces-chat-document-part2-open-doc.yaml" "$out" || status=$?
  echo "chat document part 2 flow status: $status" | tee "$out/part2-status.txt"
  scenario_evidence "$out"
  resume_space "$out" || true
  verdict "$out" "the chat document"
}

chat_panel_scenario
chat_document_scenario

# One pull for both scenarios: the day file accumulates each crash, so the summary counts them.
app_log_dir="$("$ROOT/scripts/pull-app-logs.sh" "$EVIDENCE/app-log" 2>"$EVIDENCE/pull-app-logs.err" || true)"
if [ -n "$app_log_dir" ]; then
  python3 -I "$ROOT/scripts/read_app_log.py" "$app_log_dir" >"$EVIDENCE/app-log-summary.txt" 2>&1 || true
fi
