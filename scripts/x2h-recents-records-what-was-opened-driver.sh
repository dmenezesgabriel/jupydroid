#!/usr/bin/env bash
# Seeds a notebook inside a folder, opens it through the explorer, and reads the recents store
# back off the device.
#
# The emulator is enough: nothing here runs a cell, so `FakeKernelGateway` is never consulted. At
# 320dp it is also the narrowest window the app supports, which is where the explorer's rows have
# the least room.
#
# **The disk check is the point of this driver.** X2h-1 changes nothing on screen - Home still
# renders the old list until X2h-2 swaps the surface - so the flow proves the route and this proves
# the write. A recents list that looked right and was never persisted would pass a screen-only
# assertion, which is the same trap `home-new-python-file` avoids by asserting its file is not
# nbformat.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/x2h-1-recents-source"

reset_notebooks
# A notebook at the root, one inside a folder, and a file that is neither. The nested pair is the
# defect: before X2h neither could reach Recents at all.
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "At Root"
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"

# Nothing has been opened yet, so there must be no store at all - otherwise a stale one from an
# earlier run could carry this flow, and the assertions below would prove nothing.
if "$ADB" shell "run-as $APP sh -c 'test -f \"$RECENTS_STORE\"'" 2>/dev/null; then
  echo "FAIL: a recents store survived reset_notebooks; this run could not prove anything" >&2
  exit 1
fi

run_flow "$ROOT/.maestro/x2h-recents-records-what-was-opened.yaml" "$EVIDENCE"

mkdir -p "$EVIDENCE"
STORE="$EVIDENCE/recents-store.json"
"$ADB" shell "run-as $APP sh -c 'cat \"$RECENTS_STORE\"'" > "$STORE"
echo "recents store:"; cat "$STORE"

# The notebook inside the folder, which is the defect X2b recorded.
grep -q '"path":"reports/Nested.ipynb"' "$STORE" ||
  { echo "FAIL: the notebook opened from a folder is not in Recents" >&2; exit 1; }
# The folder it was opened from, recorded as a folder - the reference records the containing
# directory alongside the document (`docmanager/src/widgetmanager.ts:418-422`).
grep -q '"path":"reports","directory":true' "$STORE" ||
  { echo "FAIL: the containing folder was not recorded as a directory" >&2; exit 1; }
# A plain file is a document too, since X2c gave it an editor.
grep -q '"path":"reports/data.csv"' "$STORE" ||
  { echo "FAIL: the plain file opened is not in Recents" >&2; exit 1; }
# Never opened, so it must not be listed: this is what separates "a record of what was opened"
# from "a scan of the workspace", and without it every assertion above would also pass against the
# old behaviour.
grep -q '"path":"At Root.ipynb"' "$STORE" &&
  { echo "FAIL: a notebook that was never opened is in Recents - this is still a scan" >&2; exit 1; }

echo "PASS: recents records what was opened, folder included, and nothing else"
