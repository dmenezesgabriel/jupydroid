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

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"

"$ADB" shell am force-stop "$APP"
"$ADB" shell run-as "$APP" mkdir -p files/notebooks
for i in $(seq 1 "$CROWD"); do
  # Distinct, old timestamps so the ordering is deterministic and anything created now is first.
  BODY="{\"metadata\":{\"kernelspec\":{\"name\":\"python3\",\"display_name\":\"Python 3\"},\"jupydroid\":{\"title\":\"Seeded $i\",\"updated_at\":\"2026-08-0${i:0:1}T0$((i % 9)):00:00Z\"}}}"
  PAYLOAD="$(printf '%s' "$BODY" | base64 -w0)"
  "$ADB" shell "run-as $APP sh -c 'echo $PAYLOAD | base64 -d > files/notebooks/seeded-$i.ipynb'"
done
"$ADB" shell run-as "$APP" ls files/notebooks | wc -l

MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/a3-create-shows-up.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/a3-create-shows-up.yaml"
fi
