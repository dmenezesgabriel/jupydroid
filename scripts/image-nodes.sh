#!/usr/bin/env bash
# Sourced by the drivers that check an image drew, after scripts/maestro-run.sh. Not executable on
# its own.
#
# A flow's `text:` matches a node's text or its description, and an image whose source resolves to
# nothing leaves its alt as text in the line, so a flow alone cannot tell a drawn image from its
# fallback. A drawn one is a node whose description is its alt
# (app/src/main/java/com/jupydroid/app/ui/editor/RichInlineText.kt), which only the hierarchy shows.
#
#   . "$ROOT/scripts/image-nodes.sh"
#   image_bounds "$OUT"
#   require_drawn "$OUT" "blue plot"

# Every described node on screen into [out]/image-bounds.txt, as `content-desc` / `bounds` pairs, with
# the whole dump beside them in [out]/hierarchy.xml. `uiautomator dump` writes to the device, so it
# is read back rather than piped.
image_bounds() {
  local out="$1" adb
  mapfile -t adb < <(device_adb)
  mkdir -p "$out"
  "${adb[@]}" shell uiautomator dump /sdcard/image-nodes-hierarchy.xml >/dev/null
  "${adb[@]}" shell cat /sdcard/image-nodes-hierarchy.xml >"$out/hierarchy.xml"
  "${adb[@]}" shell rm -f /sdcard/image-nodes-hierarchy.xml
  { grep -oE 'content-desc="[^"]+"[^>]*bounds="[^"]*"' "$out/hierarchy.xml" || true; } |
    grep -oE '(content-desc|bounds)="[^"]*"' | paste - - >"$out/image-bounds.txt"
}

# Fails unless every alt after [out] has an image node in [out]/image-bounds.txt.
require_drawn() {
  local out="$1" name
  for name in "${@:2}"; do
    grep -q "content-desc=\"$name\"" "$out/image-bounds.txt" || { echo "no image drawn for $name"; exit 1; }
  done
}

# Fails if any alt after [out] has an image node: its source resolves to nothing, so only its text
# may be on screen.
require_not_drawn() {
  local out="$1" name
  for name in "${@:2}"; do
    if grep -q "content-desc=\"$name\"" "$out/image-bounds.txt"; then echo "an image drew for $name"; exit 1; fi
  done
}

# Polls until the image [name] has a node, and appends how long that took, and the bounds it drew
# at, to [out]/drawn-after.txt. For an image read off the network or the disk, which draws a
# moment after its cell does.
await_drawn() {
  local out="$1" name="$2" started=$SECONDS
  until image_bounds "$out" && grep -q "content-desc=\"$name\"" "$out/image-bounds.txt"; do
    [ $((SECONDS - started)) -lt 90 ] || { echo "no image drawn for $name after 90 s"; exit 1; }
    sleep 1
  done
  echo "$name drawn within $((SECONDS - started)) s of being on screen (one dump a second)," \
    "$(grep "content-desc=\"$name\"" "$out/image-bounds.txt" | cut -f2)" | tee -a "$out/drawn-after.txt"
}

# Fails unless the image [name] in [out]/image-bounds.txt is [want_dp] wide, give or take a dp, and
# records the measure in [out]/<name>-width.txt. The density is the one the app draws at: an
# override when one is set, else the physical one. A layout rounds to whole pixels, which at any
# density is under a dp.
require_width_dp() {
  local out="$1" name="$2" want_dp="$3" adb density left right width_dp
  mapfile -t adb < <(device_adb)
  density="$("${adb[@]}" shell wm density | tr -d '\r' | sed -n 's/.*density: //p' | sed -n '$p')"
  read -r left right < <(grep "content-desc=\"$name\"" "$out/image-bounds.txt" |
    sed -E 's/.*bounds="\[([0-9]+),[0-9]+\]\[([0-9]+),[0-9]+\]".*/\1 \2/')
  width_dp=$(((right - left) * 160 / density))
  echo "$name: $((right - left)) px at density $density = $width_dp dp, asked $want_dp" |
    tee "$out/${name// /-}-width.txt"
  ((width_dp >= want_dp - 1 && width_dp <= want_dp + 1)) || { echo "$name is not $want_dp dp"; exit 1; }
}

# Fails unless the first image [name] in [out]/image-bounds.txt spans the first `Cell output`, edge to
# edge, and is [ratio] times as wide as it is tall, and records the measure in
# [out]/image-measure.txt. A layout rounds to whole pixels, so the ratio holds to 4 px.
require_across_column() {
  local out="$1" name="$2" ratio="$3" left top right bottom column_left column_right
  read -r left top right bottom < <(node_edges "$out" "$name")
  read -r column_left _ column_right _ < <(node_edges "$out" "Cell output")
  echo "$name $((right - left)) x $((bottom - top)) px, column $((column_right - column_left)) px wide" |
    tee "$out/image-measure.txt"
  [ "$left" -eq "$column_left" ] && [ "$right" -eq "$column_right" ] || { echo "$name is not the column's width"; exit 1; }
  local width=$((right - left)) height=$((bottom - top))
  ((height * ratio >= width - 4 && height * ratio <= width + 4)) || { echo "$name is not $ratio:1"; exit 1; }
}

# The left, top, right and bottom of the first node in [out]/image-bounds.txt described [name].
node_edges() {
  grep -m1 "content-desc=\"$2\"" "$1/image-bounds.txt" |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/'
}

# Fails unless exactly one image [name] is in [out]/image-bounds.txt, so a measure of it cannot be
# another image's.
require_one_drawn() {
  local count
  count="$(grep -c "content-desc=\"$2\"" "$1/image-bounds.txt" || true)"
  [ "$count" -eq 1 ] || { echo "$count images drawn for $2, expected 1"; exit 1; }
}
