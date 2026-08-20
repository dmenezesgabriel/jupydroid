package com.jupydroid.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every text role is Material's own, with nothing hand-built beside it.
 *
 * Written because one was. `theme/Type.kt` shipped as the Android Studio template - a single
 * `bodyLarge` built from a bare `TextStyle(...)` at 16sp/24sp/0.5sp, which are the three numbers
 * Material already supplies (`TypeScaleTokens.BodyLargeSize`, `BodyLargeLineHeight`,
 * `BodyLargeTracking`). So it changed nothing it appeared to change, and changed one thing it did
 * not appear to: Material builds every role from `DefaultTextStyle`, which carries
 * `platformStyle = defaultPlatformTextStyle()` - `includeFontPadding = false` on Android - and
 * `lineHeightStyle = DefaultLineHeightStyle` (`TypographyTokens.kt:272-276`). A bare `TextStyle`
 * carries neither, so **the app's most-used role was the only one still laying out with legacy
 * font padding**, and it had been since Step 1.
 *
 * Enumerating the roles is the point, exactly as it is in [ColorSchemeTest]: fixing `bodyLarge`
 * alone would leave the same trap set for whichever role someone hand-builds next. A real
 * departure is still allowed - it just has to be written into this table with a reason, rather
 * than arriving as a copied template.
 */
class TypographyTest {
  private val material = Typography()

  @Test
  fun everyRoleIsMaterialsOwn() {
    val departures = JupydroidTypography.roles().filter { (name, style) -> material.roles()[name] != style }
    assertEquals("roles that depart from Material's scale", emptyMap<String, TextStyle>(), departures)
  }

  /**
   * The property the template broke, asserted on its own so a failure says *why* rather than
   * printing two `TextStyle`s and leaving the reader to diff them.
   */
  @Test
  fun everyRoleKeepsMaterialsVerticalMetrics() {
    val stripped = JupydroidTypography.roles()
      .filterValues { it.platformStyle == null || it.lineHeightStyle == null }
    assertEquals("roles built without Material's vertical metrics", emptyMap<String, TextStyle>(), stripped)
  }

  /** The fifteen baseline roles, named so a failure says which one drifted. */
  private fun Typography.roles(): Map<String, TextStyle> = mapOf(
    "displayLarge" to displayLarge,
    "displayMedium" to displayMedium,
    "displaySmall" to displaySmall,
    "headlineLarge" to headlineLarge,
    "headlineMedium" to headlineMedium,
    "headlineSmall" to headlineSmall,
    "titleLarge" to titleLarge,
    "titleMedium" to titleMedium,
    "titleSmall" to titleSmall,
    "bodyLarge" to bodyLarge,
    "bodyMedium" to bodyMedium,
    "bodySmall" to bodySmall,
    "labelLarge" to labelLarge,
    "labelMedium" to labelMedium,
    "labelSmall" to labelSmall,
  )
}
