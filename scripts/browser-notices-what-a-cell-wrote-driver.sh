#!/usr/bin/env bash
# The browser re-reads the directory it is showing, so a file a *cell* wrote appears without
# anyone touching the screen. This is X1a's promise finally observable from the UI.
#
# Two rules at once, and each is declared on its own line because that is what selects one - see
# scripts/driver-rules.sh.
#
# TABLET ONLY (SM-X216B): it runs a cell, so on the x86_64 emulator `FakeKernelGateway` would echo
# the cell's own source back and no file would ever be written.
#
# WIDE WINDOW (>= 840dp): the browser has to be *beside* the notebook for the appearing row to be
# watchable while the cell runs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/evidence/browser-notices-what-a-cell-wrote"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -lt 840 ]; then
  echo "this flow must run on a wide (>= 840dp) window; $WIDTH_DP is not one" >&2
  exit 1
fi

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/writes-a-file.ipynb" "Writes A File"

# Nothing has written it yet. If a previous run left one behind, the flow's opening
# `assertNotVisible` would fail - but check here too, because a stale file would make the *closing*
# assertion pass without the kernel having done anything.
if "$ADB" shell "run-as $APP sh -c 'test -e \"$NOTEBOOK_DIR/written_by_a_cell.csv\"'" 2>/dev/null; then
  echo "FAIL: written_by_a_cell.csv survived reset_notebooks; this run could not prove anything" >&2
  exit 1
fi

run_flow "$ROOT/.maestro/browser-notices-what-a-cell-wrote.yaml" "$OUT"

# The flow proved the row appeared; this proves the row was telling the truth. A listing that
# invented an entry and a kernel that wrote a file are different claims.
mkdir -p "$OUT"
content="$("$ADB" shell run-as "$APP" cat "$NOTEBOOK_DIR/written_by_a_cell.csv" | tr -d '\r')"
printf '%s\n' "$content" > "$OUT/written-by-a-cell.csv"
if ! grep -q "region,amount" <<<"$content"; then
  echo "the cell's file is not on disk, or does not hold what the cell wrote:" >&2
  printf '%s\n' "$content" >&2
  exit 1
fi
echo "PASS: the cell wrote the file, and the browser listed it without being touched"
