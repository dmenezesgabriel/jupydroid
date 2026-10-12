#!/usr/bin/env bash
# The PRD's demo: a setting, a recent and a Space's config and kernel are each replaced whole when
# they are written, not rewritten where they sit (ADR-0089), and all four survive a relaunch. From a
# cold start the theme is changed and a notebook opened, Thesis is created and given a venv that is
# registered as a kernel, and Thesis is renamed to Dissertation. Each file's inode, mode and content,
# and the files beside it, are read along the way. A relaunch then shows all four still there and
# runs a cell on the registered kernel.
#
#   scripts/jd-027-state-write-demo-driver.sh
#
# Exits non-zero if a written file kept its inode or changed its mode, a `.~` file is left beside
# one, or a file lost what was written to it by the end.
#
# A kill is not aimed here: each file is a few hundred bytes, so its write lasts too short a time for
# a stop issued from the device to land inside it. AtomicFileWriteTest's failed write covers that.
#
# TABLET ONLY (SM-X216B): a cell runs. Needs the tablet online: pip downloads ipykernel.
# NOT A SUITE FLOW: it removes every user Space on the device.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-026-atomic-save-kill.sh
. "$ROOT/scripts/jd-026-atomic-save-kill.sh"
# shellcheck source=scripts/jd-027-state-write-reads.sh
. "$ROOT/scripts/jd-027-state-write-reads.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="$ROOT/evidence/jd-027-state-write/demo"
FLOWS="$ROOT/.maestro"
NOTEBOOK="jd-027-state-write"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
CONFIG="$THESIS/config.json"
STATS_SPEC="$THESIS/usr/local/share/jupyter/kernels/stats/kernel.json"
BASE_SPEC="files/spaces/base/usr/share/jupyter/kernels/python3/kernel.json"
export MAESTRO_FILE="$NOTEBOOK.ipynb"

report() { echo "$1" | tee -a "$EVIDENCE/demo.txt"; }

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

# [file]'s inode and mode from [before] to now, and whether the write replaced it.
report_replaced() {
  local file="$1" before="$2" after
  after="$(inode_and_mode "$file")"
  report "$file: inode mode $before -> $after, $(replaced_or_not "$before" "$after")"
}

# After the relaunch, [file] still holds [needle].
report_kept() {
  local file="$1" needle="$2"
  if "$ADB" shell "run-as $APP grep -q '$needle' $file"; then report "$file kept $needle"; else report "$file LOST $needle"; fi
}

# Step 1: no user Space, no base rename, no notebook and no store from an earlier run.
cold_start() {
  require_installed_app
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
  reset_notebooks
}

# Steps 2-3: the theme store and the recents, seeded so each has an inode before the flow.
change_theme_and_recent() {
  local theme_before recents_before
  seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "$NOTEBOOK"
  seed_stores
  theme_before="$(inode_and_mode "$THEME_SETTINGS_STORE")"
  recents_before="$(inode_and_mode "$RECENTS_STORE")"
  record before "$THEME_SETTINGS_STORE"
  record before "$RECENTS_STORE"
  run_part "$FLOWS/jd-027-state-write-t1-change.yaml" part1-theme-and-recent
  wait_for_store "$THEME_SETTINGS_STORE" DARK
  wait_for_store "$RECENTS_STORE" "$NOTEBOOK"
  record after "$THEME_SETTINGS_STORE"
  record after "$RECENTS_STORE"
  report_replaced "$THEME_SETTINGS_STORE" "$theme_before"
  report_replaced "$RECENTS_STORE" "$recents_before"
}

# Step 4: Thesis, a venv with ipykernel in its terminal, and that venv registered as a kernel.
register_a_kernel() {
  run_part "$FLOWS/jd-003-python-environments-in-a-space-t1-part1-create-thesis.yaml" part2-create-thesis
  # Seeded before the venv because seeding force-stops the app, which would take Thesis's server.
  NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/jd-003-registered-kernel.ipynb" "Registered"
  run_part "$FLOWS/jd-003-python-environments-in-a-space-t3-part2-make-envs.yaml" part3-make-envs
  NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text envs.txt deep-rc= 600
  "$ADB" shell "run-as $APP cat $THESIS_HOME/envs.txt" | tr -d '\r' | grep -qxF "stats-rc=0" ||
    { echo "envs/stats was not made with ipykernel" >&2; exit 1; }
  run_part "$FLOWS/jd-003-python-environments-in-a-space-t4-part3-register.yaml" part4-register
  record registered "$STATS_SPEC"
}

# Step 5: Thesis renamed to Dissertation from the home.
rename_the_space() {
  local before
  before="$(inode_and_mode "$CONFIG")"
  record before-rename "$CONFIG"
  run_part "$FLOWS/jd-002-manage-spaces-part2-rename.yaml" part5-rename
  record after-rename "$CONFIG"
  report_replaced "$CONFIG" "$before"
}

# Step 6: force-stop and relaunch. Dissertation is still named and its kernel runs a cell, then the
# notebook is still recent and Dark still chosen. The base spec is written again on every launch.
relaunch() {
  local before
  before="$(inode_and_mode "$BASE_SPEC")"
  record before-relaunch "$BASE_SPEC"
  run_part "$FLOWS/jd-027-state-write-t2-relaunch-and-run.yaml" part6-relaunch-and-run
  run_part "$FLOWS/jd-027-state-write-t1-relaunch.yaml" part7-relaunch-theme-and-recent
  record after-relaunch "$BASE_SPEC"
  report_replaced "$BASE_SPEC" "$before"
  report_kept "$THEME_SETTINGS_STORE" DARK
  report_kept "$RECENTS_STORE" "$NOTEBOOK"
  report_kept "$CONFIG" Dissertation
  report_kept "$STATS_SPEC" envs/stats
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/demo.txt" "$EVIDENCE/stores.txt" "$EVIDENCE/listing-after.txt"
cold_start
change_theme_and_recent
register_a_kernel
rename_the_space
relaunch
for dir in files "$THESIS" "$(dirname "$STATS_SPEC")" "$(dirname "$BASE_SPEC")"; do list_beside "$dir"; done
if grep -q ' \.~' "$EVIDENCE/listing-after.txt"; then report "a .~ file is left: FAILED"; else report "no .~ file left"; fi
! grep -qE "NOT-REPLACED|LOST|FAILED" "$EVIDENCE/demo.txt"
