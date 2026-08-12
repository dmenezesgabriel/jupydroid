#!/usr/bin/env bash
# X2b, fixed: the file browser is a surface beside the document it opened - on the tablet.
# WIDE WINDOW (>= 840dp): the driver checks its own width before running.
#
# The tablet's window (>= 840dp) is what makes two panes exist, so this refuses to run anywhere
# narrower - a green run on the 320dp emulator would prove nothing about panes.
#
# The flow ends at the browser alone (step 06). Before that it has already proved the two panes:
# Explorer|Editor and Explorer|FileEditor, with the browser's back arrow hidden while it is the
# list pane. The driver then reads the app's own accessibility tree and asserts the browser is
# NOT paired with Recents - the mirror of x2b-panes-repro's PRESENT check. Scoped to
# com.jupydroid.app because maestro's "Recents" matcher sees a system element on this device.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/evidence/x2b-explorer-beside-the-document"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -lt 840 ]; then
  echo "this flow must run on a wide (>= 840dp) window; $WIDTH_DP is not one" >&2
  exit 1
fi

# A notebook and a plain file at the root: the two kinds of document the browser opens.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/switch-alpha.ipynb" "Switch Alpha"
seed_notebook "$ROOT/.maestro/fixtures/switch-beta.ipynb" "Switch Beta"
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "script.py"

run_flow "$ROOT/.maestro/x2b-explorer-beside-the-document.yaml" "$OUT"

# The flow ended at the browser alone; read the app's own tree once everything has settled.
sleep 3
"$ADB" exec-out uiautomator dump /dev/tty > "$OUT/app-nodes.xml" || true
if python3 - "$OUT/app-nodes.xml" <<'PY'
import re
import sys

xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
app = re.search(
    r'text="Recents"[^>]*package="com\.jupydroid\.app"|package="com\.jupydroid\.app"[^>]*text="Recents"',
    xml,
)
if app is not None:
    sys.exit("the browser is still paired with Recents - the fix is not holding")
PY
then
  echo "fixed: the app's 'Recents' is not drawn beside the file browser"
else
  cat "$OUT/app-nodes.xml" >&2
  exit 1
fi
echo "evidence: $OUT (screenshots + app-nodes.xml)"