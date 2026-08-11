#!/usr/bin/env bash
# Seeds the markdown-heavy notebook, then runs the audit-A5 editor flow.
#
# It had no driver until 2026-08-07 and was listed as needing no seeding, which was never true:
# it asserts "Markdown Render", a notebook only a1c-markdown-render-driver.sh puts on the device.
# So it passed while that driver had run recently and failed the moment anything cleared state -
# a flow whose result depends on what ran before it is not evidence of anything.
#
# Same fixture as a1c on purpose: the cell it needs is the same markdown-heavy one, and a second
# copy would be two things to keep in step.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/a1c-markdown-render.ipynb"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/a1c-markdown-render.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" a1c-markdown-render

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator
# and a phone attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/a5-markdown-editor.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/a5-markdown-editor.yaml"
fi
