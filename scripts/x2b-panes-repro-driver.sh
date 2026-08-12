#!/usr/bin/env bash
# X2b: reproducible evidence of the two-pane defect - run against the PRE-fix build.
# NOT A SUITE FLOW: it asserts the defect (Recents beside the browser), which a fixed build no
# longer shows - it is a repro tool, not a regression check, and it refuses narrow windows itself.
#
# After "Browse files" on the tablet's wide window, the browser opens. The defect pairs it with
# Recents, which draws the app's own "Recents" bar-title beside it. `assertAppText "Recents"
# PRESENT` is the driver's check, read from the app's accessibility tree once the screen has
# settled - a green run means the defect reproduced.
#
# The check is scoped to com.jupydroid.app on purpose: maestro's plain "Recents" text matcher also
# sees a system "Recents" element on this device, which would make a "visible" assertion pass for
# the wrong reason.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/evidence/x2b-panes-repro"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# No seeds: the defect is a property of the stack shape (Home then Browse files). force-stop puts
# the app where every other driver's flows start.
"$ADB" shell am force-stop "$APP"
run_flow "$ROOT/.maestro/x2b-panes-repro.yaml" "$OUT"

# Let the screen settle past the navigation animation, then read the app's own hierarchy.
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
if app is None:
    sys.exit(f"expected the app's 'Recents' title beside 'Files', but no such node exists")
PY
then
  echo "reproduced: the app drew Recents beside the file browser"
else
  echo "NOT reproduced on this build - stop and investigate, do not paper over it" >&2
  exit 1
fi
echo "evidence: $OUT (screenshots + app-nodes.xml)"