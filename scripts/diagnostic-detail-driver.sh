#!/usr/bin/env bash
# Evidence: on a line holding both a breakpoint and a language-server finding, a tap in the gutter
# arms or removes the breakpoint and never opens a card, and a tap on the squiggle opens the finding's
# card and never touches the breakpoint - on the tablet at both of its widths.
#
# TABLET ONLY (SM-X216B), for the reason `diagnostics-driver.sh` states: the emulator's
# `FakeKernelGateway` means no kernel session ever opens - the sync gate stays shut and no real
# `pylsp` runs to push a finding. The tablet is both widths the item names: 1280dp landscape (two
# panes) and 800dp portrait (one pane); the flow runs at each in turn and the rotation the device
# had is put back on exit. The phone widths (320, 360, 412dp) are `DiagnosticMarkTapTest`'s.
#
# Three pieces of evidence per width:
#
#   1. `Diagnostic detail` + the finding's text in view after a tap on the squiggle, and absent after
#      each gutter tap - read from the flow's own assertions in `maestro.log`.
#   2. The frames: `04-line-holding-both-armed.png` shows one circle in the gutter's cell and the
#      squiggle under the text, `06-gutter-tap-removed-the-breakpoint.png` the same line with neither
#      circle. A frame is looked at; the card assertions say nothing about a glyph.
#   3. `lsp.start` in the Space's log: the findings came down the real port.
#
# Runs no debugger: arming a breakpoint is state in the gutter and nothing here continues the kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/diagnostics.ipynb"
FLOW="$ROOT/.maestro/diagnostic-detail.yaml"
OUT="$ROOT/evidence/diagnostic-detail"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)
MODEL="$("${adb[@]}" shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

PRIOR_AUTO_ROTATION="$("${adb[@]}" shell settings get system accelerometer_rotation | tr -d '\r')"
PRIOR_ROTATION="$("${adb[@]}" shell settings get system user_rotation | tr -d '\r')"
# shellcheck disable=SC2329 # invoked by the EXIT trap below
restore_rotation() {
  "${adb[@]}" shell settings put system user_rotation "$PRIOR_ROTATION"
  "${adb[@]}" shell settings put system accelerometer_rotation "$PRIOR_AUTO_ROTATION"
}
trap restore_rotation EXIT

# Written before every use, and a turn forced by going 1 then 0: the tablet turns itself back to
# landscape within a minute, and `user_rotation 0` does nothing when it already reads 0.
rotate_to() { # rotate_to <0=portrait|1=landscape>
  "${adb[@]}" shell settings put system accelerometer_rotation 0
  "${adb[@]}" shell settings put system user_rotation 1
  sleep 2
  "${adb[@]}" shell settings put system user_rotation "$1"
  sleep 5
}

# Where each target is, as a whole-number share of the window (Maestro takes no fraction), for the fixture's three cells after the first has
# run. Measured on the tablet from a `uiautomator` dump and the frame beside it: the gutter is the
# 84px left of each field, the error's squiggle runs under `never_defined_lsp_name`, and the
# warning's under the `=` of `y=2`. A wrong point fails loudly: the squiggle tap waits for a card
# that never opens, and a gutter tap that misses leaves the breakpoint's frame without its circle.
export_points_for() { # export_points_for <window width in dp>
  case "$1" in
    1280)
      export MAESTRO_GUTTER_BREAKPOINT_ONLY="44%,26%" MAESTRO_GUTTER_BOTH="44%,40%"
      export MAESTRO_ERROR_SQUIGGLE="59%,40%" MAESTRO_WARNING_SQUIGGLE="47%,54%"
      export MAESTRO_OUTSIDE="20%,38%" ;;
    800)
      export MAESTRO_GUTTER_BREAKPOINT_ONLY="6%,17%" MAESTRO_GUTTER_BOTH="6%,26%"
      export MAESTRO_ERROR_SQUIGGLE="31%,26%" MAESTRO_WARNING_SQUIGGLE="12%,35%"
      export MAESTRO_OUTSIDE="50%,75%" ;;
    *) echo "no measured points for a ${1}dp window; measure them first"; exit 1 ;;
  esac
}

read_flow_outcome() { # read_flow_outcome <evidence dir>
  local dir="$1" fail=0
  "${adb[@]}" logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
    grep '"stage":"lsp\.' | tail -20 > "$dir/lsp-lifecycle.log"
  if grep -q '"stage":"lsp\.start"' "$dir/lsp-lifecycle.log"; then
    echo "PASS: pylsp spawned during the flow - the findings came down the real port"
  else
    echo "FAIL: no lsp.start during the flow - the LSP path never ran"; fail=1
  fi
  if grep -q "Take screenshot 07-warning-squiggle-tap-shows-the-finding... COMPLETED" "$dir/maestro.log" &&
    ! grep -q "FAILED" "$dir/maestro.log"; then
    echo "PASS: every card assertion held - the squiggle opened it, no gutter tap did"
  else
    echo "FAIL: a card assertion failed; see $dir/maestro.log"; fail=1
  fi
  return "$fail"
}

detail_at() { # detail_at <0=portrait|1=landscape>
  rotate_to "$1"
  local width dir
  width="$(window_width_dp)"
  dir="$OUT/${width}dp"
  echo "=== a breakpoint and a finding on one line, on $MODEL at ${width}dp ==="
  export_points_for "$width"
  "${adb[@]}" logcat -c
  reset_notebooks
  seed_notebook "$FIXTURE" "Diagnostics"
  run_flow "$FLOW" "$dir" || true
  read_flow_outcome "$dir"
}

status=0
detail_at 1 || status=1
detail_at 0 || status=1
exit "$status"
