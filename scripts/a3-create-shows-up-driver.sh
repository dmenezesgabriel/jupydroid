#!/usr/bin/env bash
# Seeds a Recents list long enough to scroll, then runs the audit-A3 flow.
#
# The crowd is the whole point. A3 only reproduces when the list fills the screen: a short one
# has nothing to anchor to, which is why every test of create-then-appear passed while the bug
# was live. Seeding fewer than a screenful would make this flow prove nothing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"
CROWD=25

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # for reset_notebooks

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"

# Reset first, or the crowd is 25 *plus* whatever earlier flows left, and "a screenful" - the
# whole premise of this flow - stops being a number this driver controls.
reset_notebooks
"$ADB" shell run-as "$APP" mkdir -p "$NOTEBOOK_DIR"
for i in $(seq 1 "$CROWD"); do
  # Distinct, old timestamps so the ordering is deterministic and anything created now is first.
  BODY="{\"metadata\":{\"kernelspec\":{\"name\":\"python3\",\"display_name\":\"Python 3\"},\"jupydroid\":{\"title\":\"Seeded $i\",\"updated_at\":\"2026-08-0${i:0:1}T0$((i % 9)):00:00Z\"}}}"
  PAYLOAD="$(printf '%s' "$BODY" | base64 -w0)"
  "$ADB" shell "run-as $APP sh -c 'echo $PAYLOAD | base64 -d > $NOTEBOOK_DIR/seeded-$i.ipynb'"
done
"$ADB" shell run-as "$APP" ls "$NOTEBOOK_DIR" | wc -l

maestro_test "$ROOT/.maestro/a3-create-shows-up.yaml"
