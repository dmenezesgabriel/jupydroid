#!/usr/bin/env bash
# Sourced by the U-phase drivers. Not executable on its own.
#
# The three things every driver was copying by hand, in one place: seed a notebook into the
# app's storage, run a flow, and promote maestro's artefacts into evidence/. The older drivers
# each carry their own copy; new ones do not have to.
#
#   . "$ROOT/scripts/maestro-run.sh"
#   seed_notebook "$ROOT/.maestro/fixtures/x.ipynb" x
#   run_flow "$ROOT/.maestro/x.yaml" "$ROOT/evidence/x"

# shellcheck disable=SC2034  # the caller uses these
ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }
APP="com.jupydroid.app"

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator and a
# phone attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"

# Puts an .ipynb where DiskNotebookRepository will find it on the next launch.
#
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook), so a
# flow that made its own would only ever prove the empty branch. run-as rather than root: the
# debug build is debuggable, which is enough. The repository scans rootDir in its init block,
# so force-stopping first is what makes the fresh launch pick the file up.
#
# The id is the filename stem, so `seed_notebook fixture.ipynb my-id` lands at my-id.ipynb.
seed_notebook() {
  local fixture="$1" id="$2"
  local remote="files/notebooks/$id.ipynb"
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell run-as "$APP" mkdir -p files/notebooks
  # base64 on the command line rather than piping to `cat`: adb shell can mangle stdin line
  # endings, and a fixture is small enough to pass as an argument.
  local payload
  payload="$(base64 -w0 "$fixture")"
  "$ADB" shell "run-as $APP sh -c 'echo $payload | base64 -d > $remote'"
  "$ADB" shell run-as "$APP" test -s "$remote" ||
    { echo "fixture did not land at $remote"; exit 1; }
}

# Runs a flow and copies its screenshots and logcat into an evidence directory.
#
# Screenshots do not land in the working directory: maestro writes every artefact under
# ~/.maestro/tests/<timestamp>/<flow>/ and never says where. Taking the newest run directory is
# safe only because the copy happens immediately after the run it belongs to.
run_flow() {
  local flow="$1" out="$2"
  mkdir -p "$out"
  if [ -n "$MAESTRO_DEVICE" ]; then
    "$MAESTRO" --device "$MAESTRO_DEVICE" test "$flow" 2>&1 | tee "$out/maestro.log"
  else
    "$MAESTRO" test "$flow" 2>&1 | tee "$out/maestro.log"
  fi

  local run_dir
  run_dir="$(ls -1dt "$HOME/.maestro/tests"/*/"$(basename "$flow" .yaml)" 2>/dev/null | head -1)"
  [ -n "$run_dir" ] || { echo "no maestro run directory found for $flow"; exit 1; }
  cp "$run_dir/takeScreenshot"/*.png "$out/"
  cp "$run_dir/logs/device-logcat.txt" "$out/logcat.txt"
}
