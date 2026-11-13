package com.jupydroid.app.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A glyph beside text, sized so it keeps its proportion to the text as the system's font size changes.
 *
 * Android leaves an icon in `dp` while a label in `sp` grows, which is its documented default
 * (https://developer.android.com/develop/ui/compose/accessibility/scalable-content), so a glyph that matched its
 * label at the default size falls behind it at a large one. The glyph grows by what the label's own size grows
 * by, read through the density, which is the conversion the label goes through. Multiplying by `fontScale` would
 * overshoot it, and converting the glyph's own size would fall short of a label much smaller than the glyph:
 * Android 14 scales a large size by a curve and not by a constant, and grows a 14sp label by more than a 24sp one.
 *
 * Use it for a glyph in the same row as a text. A glyph with no text beside it, and a touch target, keep their `dp`.
 *
 * ```
 * val glyph = LocalDensity.current.glyphBesideText(24.dp, labelSize = 14.sp)
 * Icon(icon, contentDescription = null, modifier = Modifier.size(glyph))
 * ```
 *
 * @param size the glyph's size at the default font scale.
 * @param labelSize the font size of the text beside it, as that text's style states it. It defaults to the glyph's
 *   own size, which suits a glyph about as large as its label's font (a 14dp tab glyph); a glyph much larger than
 *   the font it sits beside (24dp beside 14sp) must state it, or the curve grows it less than the label.
 */
fun Density.glyphBesideText(size: Dp, labelSize: TextUnit = size.value.sp): Dp =
  size * (labelSize.toDp() / labelSize.value.dp)
