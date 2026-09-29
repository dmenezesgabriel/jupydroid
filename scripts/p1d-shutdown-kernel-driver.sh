#!/usr/bin/env bash
# P1d gate: a kernel can be ended on purpose, and closing a notebook still does not end one.
#
# Four parts on **one app process**, because the whole claim is about a server that stays up
# while the kernel under it changes:
#
#   1 run     - two cells, 42 then 43. A kernel exists and holds a variable.
#   2 shutdown- the kernel bar's menu, "Shut Down Kernel". No dialog: the reference's
#               `notebook:shutdown-kernel` executes on the spot.
#   3 rerun   - the same increment again. **NameError**, because the kernel that knew `carried`
#               is gone and the one that answers now never saw it.
#   4 close   - "Close and Shut Down Notebook…", which asks first and then leaves the editor.
#
# **The evidence is the server's session list, not the flow.** A green flow here would prove very
# little on its own - the app could have been restarted between parts and every screen assertion
# would still pass. So the driver pins the app pid, and between every part it asks the running
# `jupyter_server` what sessions it has, over `adb forward` and the token the server wrote into
# its own connection file (`server_info()`, `serverapp.py:2930-2944`).
#
# The counts it requires are the step's whole point:
#
#   after part 1   1 session   a kernel exists
#   after part 2   0 sessions  `DELETE api/sessions/<id>` reached the server
#   after part 3   1 session   a *different* kernel, which NameError already said
#   after part 4   0 sessions  and the notebook is closed as well
#
# What it deliberately does **not** assert is a session disappearing when the notebook is merely
# left. That is `notebook-session-reattaches`, which asserts the opposite and stays green: closing
# a document calls `disconnect`, and JupyterLab's `kernelShutdown` setting defaults to false
# (`packages/notebook-extension/schema/tracker.json:741-745`).
set -euo pipefail

# TABLET ONLY (SM-X216B). Every part needs a real kernel: part 1 asserts `42` and `43`, part 3
# asserts a `NameError`, and on the x86_64 emulator MainActivity falls back to FakeKernelGateway,
# which echoes the cell's source back and holds no session for `GET /api/sessions` to list.
# NOT A SUITE FLOW: the four parts share one app process and one server, so they cannot be
# shuffled into a sweep that force-stops the app between flows. It is a step-level gate, run and
# evidenced explicitly.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p1d-shutdown.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# EVIDENCE_ROOT lets a later step re-run this gate and keep the artefacts under its own
# name, which is how a step that changes no pixel evidences itself (`p7a-1b`'s own note).
EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/p1d-shutdown-kernel}"
mkdir -p "$EVIDENCE"
RUN_DIR="files/spaces/base/run"

mapfile -t DEVICE_ADB < <(device_adb)

# The port and token the running server wrote into its own connection file.
#
# Read off the device rather than guessed: the port is ephemeral (`--ServerApp.port=0`) and the
# token is minted per launch by `JupyterServerLauncher`. `jpserver-<pid>.json` carries both -
# `ServerApp.server_info()` puts `token` in it, verified in the shipped rootfs.
server_info() {
  "${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cat $RUN_DIR/jpserver-*.json'" 2>/dev/null | tr -d '\r'
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

# The kernel is a child of the app running as `libpython3.so -m jupydroid_kernel ...`; `ps`
# reports the executable, not the module, so it is grep'd out of the command line.
kernel_pids() {
  "${DEVICE_ADB[@]}" shell "ps -A -o PID,CMDLINE | grep -- '-m jupydroid_kernel' | grep -v grep" 2>/dev/null || true
}

app_pid() { "${DEVICE_ADB[@]}" shell pidof "$APP" 2>/dev/null | tr -d '\r'; }

# `run_flow` writes one `logcat.txt`, and four parts would overwrite it three times - leaving an
# evidence directory whose only log covers the last minute of a four-part gate, and reads as if it
# covered all of it. One per part instead.
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
seed_notebook "$FIXTURE" "Shutdown"

# --- part 1: a kernel, holding a variable only it knows.
run_flow "$ROOT/.maestro/p1d-part1-run-a-cell.yaml" "$EVIDENCE" || exit 1
keep_logcat "a-cell-has-run"
APP_PID="$(app_pid)"
[ -n "$APP_PID" ] || { echo "the app is not running after part 1" >&2; exit 1; }
require_sessions 1 "a-cell-has-run" || exit 1
kernel_pids >"$EVIDENCE/ps-kernel-before-shutdown.txt"
grep -q . "$EVIDENCE/ps-kernel-before-shutdown.txt" ||
  { echo "no kernel process after part 1 - there is nothing for a shutdown to end" >&2; exit 1; }

# --- part 2: shut it down from the menu. The document stays; the process does not.
run_flow "$ROOT/.maestro/p1d-part2-shut-the-kernel-down.yaml" "$EVIDENCE" || exit 1
keep_logcat "shut-down"
require_same_app || exit 1
require_sessions 0 "shut-down" || exit 1
kernel_pids >"$EVIDENCE/ps-kernel-after-shutdown.txt"
if grep -q . "$EVIDENCE/ps-kernel-after-shutdown.txt"; then
  echo "the kernel process is still alive after the shutdown:" >&2
  cat "$EVIDENCE/ps-kernel-after-shutdown.txt" >&2
  exit 1
fi
echo "the kernel process is gone, and the server still has it in no session"

# --- part 3: the notebook still runs, on a kernel that never saw the variable.
run_flow "$ROOT/.maestro/p1d-part3-a-fresh-kernel-forgot.yaml" "$EVIDENCE" || exit 1
keep_logcat "a-fresh-kernel"
require_same_app || exit 1
require_sessions 1 "a-fresh-kernel" || exit 1

# --- part 4: close and shut down. The dialog is the flow's assertion; the empty list is ours.
run_flow "$ROOT/.maestro/p1d-part4-close-and-shut-down.yaml" "$EVIDENCE" || exit 1
keep_logcat "close-and-shutdown"
require_same_app || exit 1
require_sessions 0 "close-and-shutdown" || exit 1

"${DEVICE_ADB[@]}" shell "run-as $APP cat $RUN_DIR/jupyter_server.log" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

echo "P1d gate passed: a kernel was ended on purpose, twice, and the notebook ran on afterwards."
