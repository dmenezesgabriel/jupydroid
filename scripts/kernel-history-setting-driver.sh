#!/usr/bin/env bash
# `accessKernelHistory` - the setting that gates the two `access-*-history-entry` commands,
# off by default and surviving the app dying rather than only the ViewModel that reads it.
#
# **The flow can only read a label.** `Disable kernel history access` on a reopened menu is
# satisfied by the in-memory `StateFlow` alone, because `DiskEditorSettingsRepository.update`
# assigns that *before* it writes the file - so a store that never landed would still have shown
# the flipped row after a relaunch, as long as the process happened to survive. The file is the
# claim, and this driver reads it.
#
# No device rule. Nothing here runs a cell, and the menu row is one line at every width the app
# supports, so this is one of the free flows the sharder places by load.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/kernel-history-setting"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/kernel-history-setting.ipynb" "Kernel History Setting"

run_flow "$ROOT/.maestro/kernel-history-setting.yaml" "$EVIDENCE" || exit 1

"${DEVICE_ADB[@]}" shell "run-as $APP cat files/editor-settings.json" |
  tr -d '\r' | tee "$EVIDENCE/editor-settings.json"

# **The store is a diff, not a record**, which this driver learned by asserting otherwise: it read
# `{"accessKernelHistory":true}` and nothing else, because kotlinx.serialization omits a property
# that equals its default unless `encodeDefaults` is set. So there is nothing here to check about
# the settings this flow did not touch - they are absent by design and are filled in on read, which
# is the same mechanism `EditorSettings`' own docstring relies on for a field added later. That
# round trip is `EditorSettingsRepositoryContract`'s to assert, and it does.
grep -q '"accessKernelHistory":true' "$EVIDENCE/editor-settings.json" ||
  { echo "the setting never reached disk - the store says otherwise" >&2; exit 1; }

# Back to the app's own default, so no flow run after this one on the same device inherits a
# setting nobody chose for it. By deleting the store rather than by tapping the row, because the
# tap is what the check above had to be protected from - `EditorSettingsRepository` reads its
# default the moment this file is gone, which is what `reset_notebooks` relies on too.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f files/editor-settings.json"
