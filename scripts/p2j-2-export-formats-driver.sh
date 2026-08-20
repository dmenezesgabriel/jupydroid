#!/usr/bin/env bash
# P2j-2: which nbconvert formats can actually convert a notebook on this device.
#
# **This is a measurement first and a gate second.** `GET /api/nbconvert` lists every exporter that
# *constructs* - `get_export_names` builds each one and keeps it if `e.enabled`
# (`nbconvert/exporters/base.py`) - and constructing is not converting. Four of the formats it will
# name route every markdown cell through `convert_pandoc`, and there is no pandoc anywhere in the
# shipped rootfs; three more want playwright or qtpy, which are not there either. So the list the
# server hands out is not the list this app may offer, and the difference has to be read off the
# device rather than reasoned about.
#
# What it does:
#
#   1 run     - one cell, which is what installs the Space and starts `jupyter_server`.
#   2 list    - `GET /api/nbconvert`, kept verbatim.
#   3 convert - `GET /nbconvert/<format>/<path>?download=true` for every format that list named,
#               recording status, Content-Type, Content-Disposition and either the first bytes or
#               nbconvert's own 500 message.
#
# The fixture is built to break the exporters that can break: a markdown cell for the pandoc
# filters, and a stored `image/png` output so `resources['outputs']` is non-empty and `respond_zip`
# (`jupyter_server/nbconvert/handlers.py:38-65`) answers with a **zip** named `<stem>.zip` rather
# than the plain artifact. That last one is why P2j-3's adapter reads the headers instead of
# assuming an extension.
#
# The gate half is [WORKING]: every format named there must convert, or this driver fails. It
# starts as the prediction read out of the shipped templates and becomes the measured set.
set -euo pipefail

# TABLET ONLY (SM-X216B). The whole driver talks to a running `jupyter_server`, and on the x86_64
# emulator `MainActivity` falls back to `FakeKernelGateway` - no Space is installed and no server
# is ever started, so there is nothing to ask.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p2j-2-export-formats.ipynb"
NOTEBOOK="p2j-2-export-formats"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p2j-2-export-formats"
BODIES="$EVIDENCE/bodies"
RUN_DIR="files/spaces/base/run"

# The formats this device is required to convert. Anything else the server lists is measured and
# reported, never required - see the header.
WORKING=(html markdown notebook python script slides)

mapfile -t DEVICE_ADB < <(device_adb)

# The port and token the running server wrote into its own connection file. Both are per launch -
# the port is ephemeral (`--ServerApp.port=0`) and the token is minted by `JupyterServerLauncher` -
# so neither can be guessed. Same read as `p1d-shutdown-kernel-driver.sh`.
server_info() {
  "${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'cat $RUN_DIR/jpserver-*.json'" 2>/dev/null | tr -d '\r'
}

# Opens a forward to the server for the life of this driver.
#
# One forward rather than one per call, because this makes a dozen requests and tearing the
# forward down between each would be a dozen more chances to race a server that is culling itself.
# Removed on exit however the driver ends.
FORWARDED_PORT=""
TOKEN=""
open_forward() {
  local info port
  info="$(server_info)"
  [ -n "$info" ] || { echo "no jpserver-*.json under $RUN_DIR - the server never started" >&2; return 1; }
  printf '%s' "$info" >"$EVIDENCE/jpserver.json"
  port="$(printf '%s' "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["port"])')"
  TOKEN="$(printf '%s' "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"
  FORWARDED_PORT="$("${DEVICE_ADB[@]}" forward tcp:0 "tcp:$port" | tr -d '\r')"
  echo "server on device port $port, forwarded to 127.0.0.1:$FORWARDED_PORT"
}

close_forward() {
  [ -n "$FORWARDED_PORT" ] || return 0
  "${DEVICE_ADB[@]}" forward --remove "tcp:$FORWARDED_PORT" >/dev/null 2>&1 || true
  FORWARDED_PORT=""
}
trap close_forward EXIT

# One conversion, recorded whatever it does. Prints `<status> <format>` and never fails the driver;
# [require_working] is what decides which failures matter.
convert() { # convert <format>
  local format="$1" status
  status="$(curl -sS --max-time 120 -o "$BODIES/$format.bin" -D "$BODIES/$format.headers" \
    -w '%{http_code}' -H "Authorization: token $TOKEN" \
    "http://127.0.0.1:$FORWARDED_PORT/nbconvert/$format/$NOTEBOOK.ipynb?download=true" || echo 000)"
  echo "$status"
}

# Everything one conversion said, as a section in the summary.
#
# The body is read three ways because it arrives three ways, and each of the other two was tried
# first and produced nothing legible: a **zip** dumped as bytes is noise, and a 500 is a whole
# tornado error page whose one useful line is HTML-escaped inside a `<pre>`.
report() { # report <format> <status>
  local format="$1" status="$2"
  {
    echo "## \`$format\` — HTTP $status"
    echo
    sed 's/\r$//' "$BODIES/$format.headers" | sed -n '/^[Cc]ontent-\(Type\|Disposition\|Length\):/p'
    echo
    echo '```'
    python3 "$ROOT/scripts/p2j_export_body.py" "$BODIES/$format.bin" "$status"
    echo '```'
    echo
  } >>"$EVIDENCE/formats.md"
}

reset_notebooks
seed_notebook "$FIXTURE" "$NOTEBOOK"

rm -rf "$EVIDENCE"
mkdir -p "$BODIES"

# --- 1: a cell runs, which is what brings the Space and the server up.
run_flow "$ROOT/.maestro/p2j-2-export-formats.yaml" "$EVIDENCE" || exit 1

open_forward || exit 1

# --- 2: what the server says it can export to.
curl -sS --max-time 20 -H "Authorization: token $TOKEN" \
  "http://127.0.0.1:$FORWARDED_PORT/api/nbconvert" >"$EVIDENCE/api-nbconvert.json"
python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$EVIDENCE/api-nbconvert.json" ||
  { echo "GET /api/nbconvert did not answer JSON:" >&2; cat "$EVIDENCE/api-nbconvert.json" >&2; exit 1; }
mapfile -t FORMATS < <(python3 -c 'import json,sys; print("\n".join(sorted(json.load(open(sys.argv[1])))))' "$EVIDENCE/api-nbconvert.json")
[ "${#FORMATS[@]}" -gt 0 ] ||
  { echo "GET /api/nbconvert listed no exporters at all - nbconvert is not importable" >&2; exit 1; }
echo "the server lists ${#FORMATS[@]} exporters: ${FORMATS[*]}"

# --- 3: convert to every one of them, and record what happened.
{
  echo "# What nbconvert can convert on this device"
  echo
  echo "Captured by \`scripts/p2j-2-export-formats-driver.sh\` against the running server."
  echo "Notebook: \`$NOTEBOOK.ipynb\` — a markdown cell, a stored \`image/png\` output, a stored"
  echo "\`text/html\` output, and one cell the flow ran."
  echo
} >"$EVIDENCE/formats.md"

declare -A STATUS
for format in "${FORMATS[@]}"; do
  STATUS[$format]="$(convert "$format")"
  printf '  %-10s %s\n' "$format" "${STATUS[$format]}"
  report "$format" "${STATUS[$format]}"
done

{
  echo "| format | status |"
  echo "|---|---|"
  for format in "${FORMATS[@]}"; do echo "| \`$format\` | ${STATUS[$format]} |"; done
  echo
} >"$EVIDENCE/summary.md"
cat "$EVIDENCE/summary.md"

"${DEVICE_ADB[@]}" shell "run-as $APP cat $RUN_DIR/jupyter_server.log" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true

# The gate: everything [WORKING] names must have converted.
broken=()
for format in "${WORKING[@]}"; do
  [ "${STATUS[$format]:-missing}" = "200" ] || broken+=("$format (${STATUS[$format]:-not listed})")
done
if [ "${#broken[@]}" -gt 0 ]; then
  echo "these formats are required to convert and did not: ${broken[*]}" >&2
  echo "see $EVIDENCE/formats.md for what the server said" >&2
  exit 1
fi

echo "P2j-2: ${#WORKING[@]} of ${#FORMATS[@]} listed formats convert on this device."
