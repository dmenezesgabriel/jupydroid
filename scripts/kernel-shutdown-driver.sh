#!/usr/bin/env bash
# The gate: `kernelShutdown` decides what *leaving a notebook* does to its kernel, on a
# real interpreter - `SessionContext.dispose()`'s shutdownOnDispose branch
# (`packages/apputils/src/sessioncontext.tsx:690-698`), reachable by a Settings row instead of
# the ⋮ menu alone.
#
# Four parts on **one app process**, `shutdown-kernel-driver.sh`'s own shape and for its own
# reason: the claim is about a server that stays up while the kernel under it dies or survives by
# the setting, so between parts this driver pins the app pid and asks the running
# `jupyter_server` what sessions it has.
#
#   1 settings-row        the row exists, toggles on, and `"shutdownKernelOnClose":true` lands on
#                         disk while the process that will close the notebook is already running
#   2 close-shuts-down    run `carried = 42`, dismiss, server holds 0 sessions and `ps` holds no
#                         kernel - the close itself ended it, not a menu command
#   3 reopen-forgot       the same increment p1d asserts: `.*NameError.*`, a kernel that never
#                         saw the assignment - where `notebook-session-reattaches` prints 44
#   4 setting-off         the row toggled off in the same process, then the reattach arm: the
#                         second visit's increment prints 44, which only a kernel that survived
#                         the close can compute
set -euo pipefail

# TABLET ONLY (SM-X216B). Every part needs a real kernel: part 3 asserts a NameError and part 4
# asserts 44, and on the x86_64 emulator MainActivity falls back to FakeKernelGateway, which
# echoes the cell's source back and holds no session for `GET /api/sessions` to list.
# NOT A SUITE FLOW: the four parts share one app process and one server, so they cannot be
# shuffled into a sweep that force-stops the app between flows. It is a step-level gate, run and
# evidenced explicitly.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/session-reattaches.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/kernel-shutdown"
mkdir -p "$EVIDENCE"
RUN_DIR="files/spaces/base/run"

mapfile -t DEVICE_ADB < <(device_adb)

# The port and token the running server wrote into its own connection file - p1d's own reader:
# the port is ephemeral (`--ServerApp.port=0`) and the token is minted per launch, so both are
# read off the device rather than guessed.
server_info() {
  "${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cat $RUN_DIR/jpserver-[0-9]*.json'" 2>/dev/null | tr -d '\r'
}

# The server's session list as JSON, fetched through a forwarded port.
#
# `adb forward` rather than anything on the device: the server binds 127.0.0.1 inside the device's
# network namespace and Android ships no HTTP client on the command line. The forward is torn down
# after every call so a stale one cannot answer for a server that has since been replaced.
sessions_json() {
  local info port token local_port
  info="$(server_info)"
  [ -n "$info" ] || { echo "no jpserver-*.json under $RUN_DIR - the server never started" >&2; return 1; }
  port="$(printf '%s' "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["port"])')"
  token="$(printf '%s' "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"

  local_port="$("${DEVICE_ADB[@]}" forward tcp:0 "tcp:$port" | tr -d '\r')"
  local body
  body="$(curl -sS --max-time 20 -H "Authorization: token $token" "http://127.0.0.1:$local_port/api/sessions")"
  "${DEVICE_ADB[@]}" forward --remove "tcp:$local_port" >/dev/null 2>&1 || true
  printf '%s' "$body"
}

# Fails loudly unless the server is holding exactly [want] sessions, and files what it saw.
require_sessions() { # require_sessions <want> <label>
  local want="$1" label="$2" body count
  body="$(sessions_json)"
  printf '%s' "$body" >"$EVIDENCE/sessions-$label.json"
  count="$(printf '%s' "$body" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')"
  [ "$count" = "$want" ] && { echo "sessions after $label: $count (expected $want)"; return 0; }
  echo "sessions after $label: $count, expected $want - see $EVIDENCE/sessions-$label.json" >&2
  return 1
}

# The kernel runs under the app as a proot tracee, the image's own python3 carrying
# `-m jupydroid_kernel ...` on its command line; `ps` reports the executable, not the module,
# so it is grep'd out of the command line.
kernel_pids() {
  "${DEVICE_ADB[@]}" shell "ps -A -o PID,CMDLINE | grep -- '-m jupydroid_kernel' | grep -v grep" 2>/dev/null || true
}

app_pid() { "${DEVICE_ADB[@]}" shell pidof "$APP" 2>/dev/null | tr -d '\r'; }

# `run_flow` writes one `logcat.txt`, and four parts would overwrite it three times - leaving an
# evidence directory whose only log covers the last minute of a four-part gate, and reads as if it
# covered all of it. One per part instead, `shutdown-kernel-driver.sh`'s own fix.
keep_logcat() { mv "$EVIDENCE/logcat.txt" "$EVIDENCE/logcat-$1.txt"; }

# Everything after part 1 is about one process surviving, so a new pid does not mean "the check
# failed" - it means the gate has stopped measuring anything, which is worse.
require_same_app() {
  local now
  now="$(app_pid)"
  [ "$now" = "$APP_PID" ] && return 0
  echo "the app was restarted ($APP_PID -> $now); every check after this measures nothing" >&2
  return 1
}

reset_notebooks
# One fixture, two paths: each arm needs its own notebook because `POST /api/sessions` finds a
# kernel by the document's path - closing "Kernel Shutdown" must not be able to lie about what
# "Session Reattaches" still holds.
seed_notebook "$FIXTURE" "Kernel Shutdown"
seed_notebook "$FIXTURE" "Session Reattaches"

# --- part 1: the row itself. Toggling it in the UI is both halves of the row's proof at once:
# the switch exists and moves, and the driver below reads the write off disk.
run_flow "$ROOT/.maestro/settings-row.yaml" "$EVIDENCE" || exit 1
keep_logcat "settings-row"
APP_PID="$(app_pid)"
[ -n "$APP_PID" ] || { echo "the app is not running after part 1" >&2; exit 1; }
"${DEVICE_ADB[@]}" shell "run-as $APP cat $EDITOR_SETTINGS_STORE" |
  tr -d '\r' | tee "$EVIDENCE/editor-settings-on.json"
grep -q '"shutdownKernelOnClose":true' "$EVIDENCE/editor-settings-on.json" ||
  { echo "the row never reached disk - the store says otherwise" >&2; exit 1; }

# --- part 2: kernel up, notebook left, kernel gone - and the app never restarted.
run_flow "$ROOT/.maestro/close-shuts-down.yaml" "$EVIDENCE" || exit 1
keep_logcat "close-shuts-down"
require_same_app || exit 1
require_sessions 0 "close-shuts-down" || exit 1
kernel_pids >"$EVIDENCE/ps-kernel-after-close.txt"
if grep -q . "$EVIDENCE/ps-kernel-after-close.txt"; then
  echo "the kernel process is still alive after the close:" >&2
  cat "$EVIDENCE/ps-kernel-after-close.txt" >&2
  exit 1
fi
echo "the close itself ended the kernel - no session, no process"

# --- part 3: the fresh kernel never saw `carried` - NameError on screen, and the session that
# answers now is a different one. Its own dismiss then ends it too (the setting is still on).
run_flow "$ROOT/.maestro/reopen-forgot.yaml" "$EVIDENCE" || exit 1
keep_logcat "a-fresh-kernel"
require_same_app || exit 1
require_sessions 0 "a-fresh-kernel" || exit 1

# --- part 4: the control arm. The row flips off inside the same process - the file can be a lie
# a running repo never re-reads, so the toggle must go through the app's own write path - and the
# close afterwards is the plain disconnect it always was: the reopened increment prints 44.
run_flow "$ROOT/.maestro/setting-off-reattaches.yaml" "$EVIDENCE" || exit 1
keep_logcat "setting-off-reattaches"
require_same_app || exit 1
require_sessions 1 "setting-off-reattaches" || exit 1
"${DEVICE_ADB[@]}" shell "run-as $APP cat $EDITOR_SETTINGS_STORE" |
  tr -d '\r' | tee "$EVIDENCE/editor-settings-off.json"
if grep -q '"shutdownKernelOnClose":true' "$EVIDENCE/editor-settings-off.json"; then
  echo "the row says off but the store still says on" >&2
  exit 1
fi

"${DEVICE_ADB[@]}" shell "run-as $APP cat $RUN_DIR/jupyter_server.log" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

# Leave the device the way the suite found it, `symbol-toggle-setting-driver.sh`'s own rule.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $EDITOR_SETTINGS_STORE"

echo "kernel-shutdown gate passed: one process, one server - the setting decided what two closes did."
