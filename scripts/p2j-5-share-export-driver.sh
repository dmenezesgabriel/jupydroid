#!/usr/bin/env bash
# P2j-5 gate: an exported file leaves the app, through a URI another app can actually read.
#
# **What this can check, and what it deliberately leaves to a test.**
#
# The chooser coming up is itself the strongest thing on screen, and it is not nothing:
# `IntentDocumentSharer.share` calls `getUriForFile` *before* `startActivity`, and that throws
# `IllegalArgumentException` for a file under no configured root - so a chooser on top proves the
# provider's `<files-path>` covers the workspace and the export is inside it.
#
# **`adb shell content read` cannot check the rest, and finding that out is the note worth keeping.**
# The provider is `exported="false"` with `grantUriPermissions="true"`, so a read is permitted only
# to whoever the intent granted - never to the shell user, whatever the configuration says:
#
#     SecurityException: Permission Denial: opening provider androidx.core.content.FileProvider
#     from (null) (pid=…, uid=2000) that is not exported from UID 13219
#
# That is the provider being correct, not broken, and a driver check that fails identically either
# way is a check that measures nothing. Whether a recipient can read the bytes is
# `IntentDocumentSharerTest` (androidTest), which asks in-process.
#
# So the driver asks the platform two things it can actually answer:
#
#   - `dumpsys activity activities` - a chooser is on top;
#   - `dumpsys activity providers` - the provider is registered under this app and is not exported.
#
# TABLET ONLY (SM-X216B): the file has to be a real conversion, and on x86_64 the exporter is the
# fake. `p2j-4-export-menu` is the emulator's flow for this feature's UI.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p2j-4-export.ipynb"
NOTEBOOK="p2j-5-share"
AUTHORITY="com.jupydroid.app.exports"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p2j-5-share-export"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

reset_notebooks
seed_notebook "$FIXTURE" "$NOTEBOOK"

run_flow "$ROOT/.maestro/p2j-5-share-export.yaml" "$EVIDENCE" || exit 1

# --- 1: something other than this app is on top, and it is a chooser.
"${DEVICE_ADB[@]}" shell dumpsys activity activities | tr -d '\r' >"$EVIDENCE/activities.txt"
grep -qiE 'ResolverActivity|ChooserActivity|IntentResolver' "$EVIDENCE/activities.txt" || {
  echo "no chooser in the activity stack - the share intent never left the app" >&2
  grep -i "topResumedActivity\|mResumedActivity" "$EVIDENCE/activities.txt" >&2 || true
  exit 1
}
grep -oiE '[A-Za-z.]*(ResolverActivity|ChooserActivity|IntentResolver)[A-Za-z.]*' "$EVIDENCE/activities.txt" |
  sort -u | tee "$EVIDENCE/chooser.txt"

# --- 2: the provider behind that chooser is this app's, and is not exported.
#
# The security half, and the only half `dumpsys` can answer: anything but `exported=false` here
# would put the whole workspace within reach of every app on the device, grant or no grant.
"${DEVICE_ADB[@]}" shell dumpsys activity providers | tr -d '\r' >"$EVIDENCE/providers.txt"
grep -q "authority=$AUTHORITY" "$EVIDENCE/providers.txt" ||
  { echo "$AUTHORITY is not a registered provider" >&2; exit 1; }
# And *not exported*, asserted by being refused. The shell user holds no URI grant, so a read that
# succeeded here would mean the provider was open to everything on the device - which is what the
# workspace must never be. The refusal is the proof, and it says so in as many words.
#
# Read by what it *wrote*, not by its exit status: `content` catches the SecurityException, prints
# it on stderr and still exits 0, so `|| fail` here would never fire and the check would be one
# that cannot fail. Measured, twice - the first version of this passed for the wrong reason.
"${DEVICE_ADB[@]}" shell "content read --uri content://$AUTHORITY/workspace/$NOTEBOOK.html" \
  >"$EVIDENCE/what-the-shell-user-got.bin" 2>"$EVIDENCE/refused-without-a-grant.txt" || true
if [ -s "$EVIDENCE/what-the-shell-user-got.bin" ]; then
  echo "the exports provider served the shell user - it is open to every app on the device" >&2
  exit 1
fi
grep -q "not exported from UID" "$EVIDENCE/refused-without-a-grant.txt" || {
  echo "the provider refused for some other reason than not being exported:" >&2
  cat "$EVIDENCE/refused-without-a-grant.txt" >&2
  exit 1
}
echo "$AUTHORITY is registered, and refuses anyone the intent did not grant"

# The file the chooser is offering, as it is on disk - so the evidence holds what was shared.
"${DEVICE_ADB[@]}" shell "run-as $APP base64 '$NOTEBOOK_DIR/$NOTEBOOK.html'" |
  tr -d '\r' | base64 -d >"$EVIDENCE/$NOTEBOOK.html"
grep -q "<title>$NOTEBOOK</title>" "$EVIDENCE/$NOTEBOOK.html" ||
  { echo "the file offered to the chooser is not the exported notebook" >&2; exit 1; }
echo "the shared file is the export: $(wc -c <"$EVIDENCE/$NOTEBOOK.html") bytes"

# Leave the device on the app rather than on a chooser, for whatever runs next.
"${DEVICE_ADB[@]}" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true

echo "P2j-5 gate passed: the chooser came up over the export, through a provider only a grant opens."
