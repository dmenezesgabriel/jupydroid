package com.jupydroid.app.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * A glyph beside text, sized so it keeps its proportion to the text as the system's font size changes.
 *
 * Android leaves an icon in `dp` while a label in `sp` grows, which is its documented default
 * (https://developer.android.com/develop/ui/compose/accessibility/scalable-content), so a glyph that matched its
 * label at the default size falls behind it at a large one. The size is read as `sp` and converted back through
 * the density, which is the conversion the label's own size goes through. Multiplying by `fontScale` would
 * overshoot it, because Android 14 scales a large size by a curve and not by a constant.
 *
 * Use it for a glyph in the same row as a text. A glyph with no text beside it, and a touch target, keep their `dp`.
 *
 * ```
 * Icon(icon, contentDescription = null, modifier = Modifier.size(LocalDensity.current.glyphBesideText(14.dp)))
 * ```
 */
fun Density.glyphBesideText(size: Dp): Dp = size.value.sp.toDp()
