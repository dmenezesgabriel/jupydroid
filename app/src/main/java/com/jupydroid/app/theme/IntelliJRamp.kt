package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * IntelliJ's New UI colour ramps, the source every Material role and every editor token here is
 * drawn from.
 *
 * Copied from `platform/platform-resources/src/themes/expUI/expUI_{dark,light}.theme.json` in the
 * reference tree. Named by ramp position, exactly as JetBrains names them, so a value can be
 * checked against its origin instead of being argued about - `Gray2` is one specific grey, where
 * "surfaceVariant" is a job that several greys could apply for.
 *
 * The two ramps are not reflections of each other: dark's greys run light-on-dark from `Gray1`
 * (the darkest surface) upward, light's run dark-on-light from `Gray1` (black text) downward. So
 * `DarkGray2` is a background and `LightGray2` is a foreground, and they must not be swapped.
 */
internal object IntelliJRamp {
  // Dark - expUI_dark.theme.json
  val DarkGray1 = Color(0xFF1E1F22)
  val DarkGray2 = Color(0xFF2B2D30)
  val DarkGray3 = Color(0xFF393B40)
  val DarkGray4 = Color(0xFF43454A)
  val DarkGray5 = Color(0xFF4E5157)
  val DarkGray9 = Color(0xFF9DA0A8)
  val DarkGray10 = Color(0xFFB4B8BF)
  val DarkGray11 = Color(0xFFCED0D6)
  val DarkBlue6 = Color(0xFF3574F0)
  val DarkGreen7 = Color(0xFF5FAD65)
  val DarkRed1 = Color(0xFF402929)
  val DarkRed7 = Color(0xFFDB5C5C)
  val DarkRed10 = Color(0xFFF2B1AA)

  // Light - expUI_light.theme.json
  val LightGray1 = Color(0xFF000000)
  val LightGray2 = Color(0xFF27282E)
  val LightGray5 = Color(0xFF5A5D6B)
  val LightGray6 = Color(0xFF6C707E)
  val LightGray8 = Color(0xFFA8ADBD)
  val LightGray11 = Color(0xFFDFE1E5)
  val LightGray12 = Color(0xFFEBECF0)
  val LightGray13 = Color(0xFFF7F8FA)
  val LightGray14 = Color(0xFFFFFFFF)
  val LightBlue4 = Color(0xFF3574F0)
  val LightGreen5 = Color(0xFF369650)
  val LightRed4 = Color(0xFFDB3B4B)
  val LightRed1 = Color(0xFFAD2B38)
  val LightRed10 = Color(0xFFFFEBEC)
}
