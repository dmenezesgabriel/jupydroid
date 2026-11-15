# shellcheck shell=bash
# shellcheck disable=SC2154 # "adb" is assigned by rotation-reads.sh, which every caller sources first
# Sourced by the capture drivers. Not executable on its own.
#
# The font scale a capture runs at, and the device's own put back after it. The scale is the user's
# setting (the tablet's is 1.1), so a driver that left 2.0 behind would change every screen on a
# tablet someone also uses. Needs "adb", the device's own adb command line, from rotation-reads.sh.
#
#   . "$ROOT/scripts/font-scale.sh"
#   trap restore_font_scale EXIT
#   set_font_scale 2.0

# The scale found before the first set, `null` when the device has none of its own. Empty until then,
# which is how `restore_font_scale` knows there is nothing to put back.
ORIGINAL_FONT_SCALE=""

set_font_scale() { # set_font_scale <scale>
  if [ -z "$ORIGINAL_FONT_SCALE" ]; then
    ORIGINAL_FONT_SCALE="$("${adb[@]}" shell settings get system font_scale | tr -d '\r')"
  fi
  "${adb[@]}" shell settings put system font_scale "$1"
}

# `settings put ... null` would store the string "null", so an unset scale is deleted instead.
restore_font_scale() {
  [ -n "$ORIGINAL_FONT_SCALE" ] || return 0
  if [ "$ORIGINAL_FONT_SCALE" = null ]; then
    "${adb[@]}" shell settings delete system font_scale
  else
    "${adb[@]}" shell settings put system font_scale "$ORIGINAL_FONT_SCALE"
  fi
}
