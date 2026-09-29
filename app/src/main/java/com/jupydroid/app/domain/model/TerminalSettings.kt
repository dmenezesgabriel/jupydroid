package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide terminal preferences - one field today, the font size.
 *
 * Its own port rather than a field on [EditorSettings]: a terminal's glyph size is not an editor
 * concern (`CLAUDE.md`'s "one home per kind of logic"), even though the two happen to share the
 * same default today - see [com.jupydroid.app.theme.CodeText.size].
 */
@Serializable
data class TerminalSettings(val fontSizeSp: Int = TerminalFontSize.DEFAULT)

/**
 * Bounds measured against Termux's
 * `TermuxAppSharedPreferences.getDefaultFontSizes`/`changeFontSize`
 * (`termux-app@3df69d1d termux-shared/.../TermuxAppSharedPreferences.java`) rather
 * than invented - 4 minimum, 256 maximum, 2 per step. [DEFAULT] departs from Termux's own ~12: it
 * is [com.jupydroid.app.theme.CodeText.size] instead, so shipping this setting changes no pixel
 * until a user acts.
 */
object TerminalFontSize {
  const val MIN = 4
  const val MAX = 256
  const val STEP = 2
  const val DEFAULT = 14
}
