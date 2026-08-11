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

# Where notebooks live on the device, which X1a moved.
#
# It is the kernel's HOME, so a notebook can now `open()` a file the user can see and the user can
# see what a notebook wrote (`notebookWorkspaceIn`). Written down once here because twenty-nine
# drivers used to spell `files/notebooks` out by hand, and a driver seeding the old path would
# still "work" - the app would simply show an empty Home, which is a green flow proving nothing.
NOTEBOOK_DIR="files/spaces/base/home/jupydroid"

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator and a
# phone attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"

# Empties the notebooks directory, so a flow sees the Home list it seeded and no other.
#
# Flows were not independent, and it took a whole-suite sweep to show it. Every run leaves
# notebooks behind - `a3-create-shows-up` seeds a crowd of 25 on purpose, and every flow that
# taps the FAB adds a UUID that nothing ever removes - so Home grows by a couple of rows per
# sweep, forever. `a6-edit-updates-recents` is the one that breaks first: it backdates its
# fixture to 2020 precisely so the row is stale, which pins it to the *bottom* of a list sorted
# by recency, and at 64 notebooks a 20-second `scrollUntilVisible` no longer reaches it.
reset_notebooks() {
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell "run-as $APP sh -c 'rm -f $NOTEBOOK_DIR/*.ipynb'"
}

# Puts an .ipynb where DiskNotebookRepository will find it on the next launch.
#
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook), so a
# flow that made its own would only ever prove the empty branch. run-as rather than root: the
# debug build is debuggable, which is enough. The repository scans rootDir in its init block,
# so force-stopping first is what makes the fresh launch pick the file up.
#
# The id is the filename stem, so `seed_notebook fixture.ipynb my-id` lands at my-id.ipynb.
#
# It seeds one notebook and does not clear the others, so a driver that needs two can call it
# twice - `notebook-switch` does. Isolation is [reset_notebooks]'s job and every driver states
# it, which `scripts/check_driver_isolation.py` enforces so that stating it cannot be forgotten.
seed_notebook() {
  local fixture="$1" id="$2"
  local remote="$NOTEBOOK_DIR/$id.ipynb"
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell run-as "$APP" mkdir -p "$NOTEBOOK_DIR"
  # base64 on the command line rather than piping to `cat`: adb shell can mangle stdin line
  # endings, and a fixture is small enough to pass as an argument.
  local payload
  payload="$(base64 -w0 "$fixture")"
  "$ADB" shell "run-as $APP sh -c 'echo $payload | base64 -d > $remote'"
  "$ADB" shell run-as "$APP" test -s "$remote" ||
    { echo "fixture did not land at $remote"; exit 1; }
}

# The width the app actually runs at, in dp - the same number `WindowSizeClass` reads.
#
# Asked of the framework rather than computed, and that is the point. Every attempt to derive it
# got it wrong in a different way: `wm size` reports the *natural* size, so "the larger edge" is
# the width on a landscape device and the height on a portrait one, and a run in the wrong size
# class still looks green (docs/PLAN.md, U5b).
window_width_dp() {
  # `sed -n 1p` rather than `head -1`, for the reason `newest_run_dir` explains at length.
  "$ADB" shell am get-config | tr '-' '\n' | sed -n 's/^w\([0-9]\+\)dp$/\1/p' | sed -n '1p'
}

# Stamps an evidence directory with the device that produced it.
#
# An evidence directory that does not say where it came from is a trap. Ten drivers promote
# artefacts into evidence/ and only three are marked TABLET ONLY, so a sweep run on the emulator
# overwrites tablet captures with x86_64 ones and nothing on disk says so - and the difference
# matters: on x86_64 `MainActivity` falls back to `FakeKernelGateway`. The width is here for the
# same reason U5b needed it: a screenshot at one width is a layout with an untested branch.
record_device() {
  local out="$1"
  local adb=("$ADB")
  if [ -n "$MAESTRO_DEVICE" ]; then adb+=(-s "$MAESTRO_DEVICE"); fi
  {
    echo "serial   $("${adb[@]}" get-serialno | tr -d '\r')"
    echo "model    $("${adb[@]}" shell getprop ro.product.model | tr -d '\r')"
    echo "abi      $("${adb[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
    # `am get-config` rather than `wm size`, which is pixels and three wrong derivations away
    # from the dp the window actually reports.
    echo "window   $("${adb[@]}" shell am get-config | tr -d '\r' | grep -m1 -oE 'w[0-9]+dp-h[0-9]+dp')"
    echo "captured $(date -Iseconds)"
  } >"$out/device.txt"
}

# The directory maestro just wrote for [flow_name], newest first.
#
# Maestro writes every artefact under ~/.maestro/tests/<timestamp>/<flow>/ and never says where,
# so the newest is the only handle there is. Safe only because the caller asks immediately after
# the run it belongs to.
#
# `sed -n 1p` and not `head -1`, which is the bug this function exists to stop repeating: `head`
# exits after the first line and the still-writing `ls` upstream takes SIGPIPE, which under
# `set -o pipefail` fails the driver with 141. It is a *race*, so it was invisible until the
# directory grew - at 629 runs `ls` finally wrote more than a pipe buffer before `head` left,
# and `a4-markdown-contrast` began failing after every flow in it had passed. `sed` reads its
# input to the end, so there is nothing to race.
newest_run_dir() {
  local flow_name="$1"
  ls -1dt "$HOME/.maestro/tests"/*/"$flow_name" 2>/dev/null | sed -n '1p'
}

# Runs a flow on the device this driver seeded, and nowhere else.
#
# `adb` honours `ANDROID_SERIAL`; **maestro does not**. With two devices attached it picks one of
# its own accord, so a driver that seeds through `adb` and then runs `maestro test` bare drives
# the other device - which `step2-import` and `step4-editor` did, and the first whole-suite run
# on the emulator ran their flows against the tablet against a Home that had never been seeded.
#
# Twenty-five drivers carried this same six-line if/else by hand. `scripts/check_driver_isolation.py`
# requires going through here now, so it cannot be half-copied into the twenty-sixth.
maestro_test() {
  local flow="$1"
  if [ -n "$MAESTRO_DEVICE" ]; then
    "$MAESTRO" --device "$MAESTRO_DEVICE" test "$flow"
  else
    "$MAESTRO" test "$flow"
  fi
}

# Runs a flow and copies its screenshots and logcat into an evidence directory.
run_flow() {
  local flow="$1" out="$2" status=0
  mkdir -p "$out"
  # The status is captured rather than allowed to abort, because the evidence for a *failure* is
  # the evidence that matters and this used to throw it away: `set -e` killed the driver at the
  # failed assertion, before any of the copying below, so `evidence/<step>/` kept the artefacts
  # of the last run that passed. Chasing the third sighting of the execution stall, the logcat in
  # evidence/ turned out to be from a different run 84 minutes earlier.
  maestro_test "$flow" 2>&1 | tee "$out/maestro.log" || status=$?

  local run_dir
  run_dir="$(newest_run_dir "$(basename "$flow" .yaml)")"
  [ -n "$run_dir" ] || { echo "no maestro run directory found for $flow"; exit 1; }
  # A failed flow has fewer screenshots than it planned, and may have none.
  cp "$run_dir/takeScreenshot"/*.png "$out/" 2>/dev/null || true
  cp "$run_dir/logs/device-logcat.txt" "$out/logcat.txt"
  record_device "$out"
  return "$status"
}
