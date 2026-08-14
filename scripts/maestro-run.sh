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

# The recents list, which is app state and so deliberately *not* under NOTEBOOK_DIR - the
# workspace is shared with the kernel, and bookkeeping there would show up in the explorer's own
# listing (DiskRecentDocumentsRepository). It is cleared by [reset_notebooks] for the same reason
# the workspace is: since X2h it survives a wipe of the workspace, so a path a previous run opened
# and this one re-seeds under the same name would still be listed.
RECENTS_STORE="files/recent-documents.json"

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator and a
# phone attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"

# Empties the workspace, so a flow sees the state it seeded and no other.
#
# Everything, not just `*.ipynb`: since X2 a driver can seed a folder and a data file too, and a
# `reports/` left behind by the previous run is a row the next flow did not put there. `cd` first
# so a mistyped path removes nothing rather than the wrong thing.
#
# Flows were not independent, and it took a whole-suite sweep to show it. Every run leaves
# notebooks behind - `a3-create-shows-up` seeds a crowd of 25 on purpose, and every flow that
# taps the FAB adds a UUID that nothing ever removes - so Home grows by a couple of rows per
# sweep, forever. `a6-edit-updates-recents` is the one that breaks first: it backdates its
# fixture to 2020 precisely so the row is stale, which pins it to the *bottom* of a list sorted
# by recency, and at 64 notebooks a 20-second `scrollUntilVisible` no longer reaches it.
reset_notebooks() {
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell "run-as $APP sh -c 'cd \"$NOTEBOOK_DIR\" 2>/dev/null && rm -rf ./*' || true"
  "$ADB" shell "run-as $APP sh -c 'rm -f \"$RECENTS_STORE\"' || true"
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
  seed_file "$fixture" "$id.ipynb"
}

# Puts any file into the workspace at [path], relative to the workspace root.
#
# The explorer lists the tree as it is, so a flow about browsing needs a folder and something in
# it that is not a notebook. Notebooks go through [seed_notebook], which only knows that the id
# is the filename stem.
#
# ```
# seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
# ```
seed_file() {
  local fixture="$1" path="$2"
  local remote="$NOTEBOOK_DIR/$path"
  "$ADB" shell am force-stop "$APP"
  # Parents included: a seeded path may name a folder the workspace does not have yet.
  "$ADB" shell run-as "$APP" mkdir -p "$(dirname "$remote")"
  # base64 on the command line rather than piping to `cat`: adb shell can mangle stdin line
  # endings, and a fixture is small enough to pass as an argument.
  local payload
  payload="$(base64 -w0 "$fixture")"
  # The path is quoted, because X1b made the file name the notebook's title and titles have
  # spaces in them - `Markdown Render.ipynb`. Unquoted, the redirect target was two words and the
  # fixture landed in a file called `Markdown`.
  "$ADB" shell "run-as $APP sh -c 'echo $payload | base64 -d > \"$remote\"'"
  "$ADB" shell "run-as $APP sh -c 'test -s \"$remote\"'" ||
    { echo "fixture did not land at $remote"; exit 1; }
}

# The width the app actually runs at, in dp - the same number `WindowSizeClass` reads.
#
# Asked of the framework rather than computed, and that is the point. Every attempt to derive it
# got it wrong in a different way: `wm size` reports the *natural* size, so "the larger edge" is
# the width on a landscape device and the height on a portrait one, and a run in the wrong size
# class still looks green.
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
  local adb
  mapfile -t adb < <(device_adb)
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

# The device this driver is driving, as an `adb` invocation.
#
# `$ADB` alone honours `ANDROID_SERIAL`, but a driver run with two devices attached and no serial
# set would ask whichever adb picks - so the serial is passed when there is one, exactly as
# `record_device` does.
device_adb() {
  local adb=("$ADB")
  if [ -n "$MAESTRO_DEVICE" ]; then adb+=(-s "$MAESTRO_DEVICE"); fi
  printf '%s\n' "${adb[@]}"
}

# A flow drives whatever is on the screen, and a locked device shows a lock screen.
#
# **This has already cost a measurement.** A fifty-run sweep of `step5-completion` on the tablet
# passed 31 and then failed 19 in a row; the app was blameless - `stay_on_while_plugged_in` is 0
# on this device, the screen went off, and every run after that drove the lock screen. The
# screen-hierarchy captures are the proof: at the failing step they hold the clock digits
# `0 9 : 2 0`, and after a `KEYCODE_WAKEUP`, the PIN pad's `1 ABC 2 DEF ... Emergency call`.
# A run against a lock screen is not a red result, it is *no* result, and nothing in the harness
# said which it was.
#
# So the harness sets the condition rather than hoping for it, and then checks it. Two signals,
# because they fail apart: `mWakefulness` says the screen is on, and a device woken by
# `KEYCODE_WAKEUP` reports `Awake` **while still behind its PIN** - which is exactly how the
# first repair of this function still lost two runs. `mDreamingLockscreen` is the one that says
# the app is reachable.
require_unlocked_device() {
  local adb
  mapfile -t adb < <(device_adb)
  # `usb` rather than `true`: the flows run on a plugged-in device, and this removes the
  # 30-minute fuse under every long sweep. It survives a reboot, which is why it is set here
  # once rather than saved and restored around each flow.
  "${adb[@]}" shell svc power stayon usb >/dev/null 2>&1 || true
  "${adb[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  # Dismisses a *non-secure* keyguard - a swipe-to-unlock. A PIN, pattern or password cannot be
  # cleared from adb at all, and is what the check below is here to report.
  "${adb[@]}" shell input keyevent KEYCODE_MENU >/dev/null 2>&1 || true

  local wakefulness locked
  wakefulness="$("${adb[@]}" shell dumpsys power |
    sed -n 's/.*mWakefulness=\([A-Za-z]*\).*/\1/p' | sed -n '1p' | tr -d '\r')"
  locked="$("${adb[@]}" shell dumpsys window |
    sed -n 's/.*mDreamingLockscreen=\([a-z]*\).*/\1/p' | sed -n '1p' | tr -d '\r')"
  [ "$wakefulness" = "Awake" ] && [ "$locked" = "false" ] && return 0

  echo "device is not usable: mWakefulness=$wakefulness mDreamingLockscreen=$locked" >&2
  echo "a flow run now would drive the lock screen and report it as an app failure." >&2
  echo "unlock the device by hand - a secure keyguard cannot be dismissed over adb." >&2
  return 1
}

# Runs a flow on the device this driver seeded, and nowhere else.
#
# `adb` honours `ANDROID_SERIAL`; **maestro does not**. With two devices attached it picks one of
# its own accord, so a driver that seeds through `adb` and then runs `maestro test` bare drives
# the other device - which `step2-import` and `step4-editor` did, and the first whole-suite run
# on the emulator ran their flows against the tablet against a Home that had never been seeded.
#
# Twenty-five drivers carried this same six-line if/else by hand. `scripts/check_driver_isolation.py`
# requires going through here now, so it cannot be half-copied into the twenty-sixth - which is
# also what makes this the one place [require_unlocked_device] has to be called from.
maestro_test() {
  local flow="$1"
  require_unlocked_device || return 1
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
