#!/usr/bin/env bash
# A Space's server starts with an empty run/. Stale runtime files a killed server could have left
# (an -open.html, an MCP file, a kernel connection file) are seeded into base's run/ with the app
# stopped; a cell then runs on the real kernel; the listing of run/ afterwards holds none of them
# and the live server's own files.
#
#   nice -n 10 timeout 2400 scripts/run-dir-empty-driver.sh
#
# Exits non-zero if the flow fails, if a stale file survives, if more than the live -open.html is there, or if a live
# file is missing (then the server never started and an empty run/ would prove nothing).
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and the demo runs a cell.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/run-dir-empty"
FIXTURE="$ROOT/.maestro/fixtures/run-dir-empty.ipynb"
STALE=(jpserver-1-open.html jpserver-mcp-1.json kernel-stale.json)

mapfile -t DEVICE_ADB < <(device_adb)

fail() { echo "$1" >&2; exit 1; }

MODEL="$("${DEVICE_ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B | SM-X216B) ;;
  *) fail "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'" ;;
esac

list_run_dir() { "${DEVICE_ADB[@]}" shell "run-as $APP ls $SPACE_RUN_DIR" | tr -d '\r'; }

seed_stale_runtime_files() {
  local name
  "${DEVICE_ADB[@]}" shell "run-as $APP mkdir -p $SPACE_RUN_DIR"
  for name in "${STALE[@]}"; do
    "${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'echo stale > $SPACE_RUN_DIR/$name'"
  done
}

# Each live file is a prefix and a suffix, because the pid and the kernel id are per launch.
require_listed() { # require_listed <regex> <what>
  grep -Eq "$1" "$EVIDENCE/run-dir.txt" || fail "run/ holds no $2 ($1): the server did not start as claimed"
}

# upstream writes the live server's page whatever we pass (no_browser_open_file is not configurable),
# so exactly one is expected: the running pid's.
only_live_open_html() {
  local pages
  pages="$(grep -E '^jpserver-.*-open\.html$' "$EVIDENCE/run-dir.txt" || true)"
  [ "$(echo "$pages" | grep -c .)" = 1 ] || fail "expected one -open.html, the live server's; got: $pages"
}

reset_notebooks
seed_notebook "$FIXTURE" run-dir-empty
mkdir -p "$EVIDENCE"
seed_stale_runtime_files
list_run_dir >"$EVIDENCE/run-dir-before.txt"

run_flow "$ROOT/.maestro/run-dir-empty.yaml" "$EVIDENCE" || fail "the flow failed"

list_run_dir >"$EVIDENCE/run-dir.txt"
cat "$EVIDENCE/run-dir.txt"
for name in "${STALE[@]}"; do
  ! grep -qx "$name" "$EVIDENCE/run-dir.txt" || fail "stale $name survived the start"
done
only_live_open_html
require_listed '^jpserver-[0-9]+\.json$' "connection file"
require_listed '^jpserver-mcp-[0-9]+\.json$' "MCP file"
require_listed '^kernel-.*\.json$' "kernel file"
require_listed '^jupyter_server\.log$' "server log"
