package com.jupydroid.app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every Material role is drawn from IntelliJ's ramp, with nothing left at Material's baseline.
 *
 * Written because half of them were. U1 set `primary`, `surface`, `error` and a dozen more and
 * left the rest alone - and `darkColorScheme()`'s defaults are the Material baseline **purple**,
 * so anything reaching for an unset role got a colour from a different design system. The FAB
 * did exactly that: `FloatingActionButton` defaults to `primaryContainer`, which nothing had
 * set, so it sat on Home in baseline purple next to an editor drawn in Darcula
 * (`evidence/u2-cell-mode/05-back-still-leaves.png`, before this).
 *
 * Enumerating the roles is the point. Fixing only the FAB would have left the same bug waiting
 * in every role no screen happens to use yet - and U5 is about to add a bottom bar and a bottom
 * sheet, which reach for several of them.
 */
class ColorSchemeTest {
  @Test
  fun everyDarkRoleComesFromTheRamp() = assertEveryRoleIsFromTheRamp("dark", DarkColorScheme)

  @Test
  fun everyLightRoleComesFromTheRamp() = assertEveryRoleIsFromTheRamp("light", LightColorScheme)

  private fun assertEveryRoleIsFromTheRamp(name: String, scheme: ColorScheme) {
    val strays = scheme.roles().filterValues { it !in ColorRamp.all }
    assertEquals("$name roles that are not on the ramp", emptyMap<String, Color>(), strays)
  }

  /**
   * The roles this app can actually reach, named so a failure says which one drifted.
   *
   * Not every field on `ColorScheme`: the `*Fixed` family exists for cross-theme accents that
   * nothing here uses, and asserting over colours no component reads would be a rule about
   * nothing.
   */
  private fun ColorScheme.roles(): Map<String, Color> = accentRoles() + surfaceRoles() + outlineRoles()

  private fun ColorScheme.accentRoles(): Map<String, Color> = mapOf(
    "primary" to primary,
    "onPrimary" to onPrimary,
    "primaryContainer" to primaryContainer,
    "onPrimaryContainer" to onPrimaryContainer,
    "inversePrimary" to inversePrimary,
    "secondary" to secondary,
    "onSecondary" to onSecondary,
    "secondaryContainer" to secondaryContainer,
    "onSecondaryContainer" to onSecondaryContainer,
    "tertiary" to tertiary,
    "onTertiary" to onTertiary,
    "tertiaryContainer" to tertiaryContainer,
    "onTertiaryContainer" to onTertiaryContainer,
    "error" to error,
    "onError" to onError,
    "errorContainer" to errorContainer,
    "onErrorContainer" to onErrorContainer,
  )

  private fun ColorScheme.surfaceRoles(): Map<String, Color> = mapOf(
    "background" to background,
    "onBackground" to onBackground,
    "surface" to surface,
    "onSurface" to onSurface,
    "surfaceVariant" to surfaceVariant,
    "onSurfaceVariant" to onSurfaceVariant,
    "surfaceTint" to surfaceTint,
    "inverseSurface" to inverseSurface,
    "inverseOnSurface" to inverseOnSurface,
    "surfaceBright" to surfaceBright,
    "surfaceDim" to surfaceDim,
    "surfaceContainer" to surfaceContainer,
    "surfaceContainerHigh" to surfaceContainerHigh,
    "surfaceContainerHighest" to surfaceContainerHighest,
    "surfaceContainerLow" to surfaceContainerLow,
    "surfaceContainerLowest" to surfaceContainerLowest,
  )

  private fun ColorScheme.outlineRoles(): Map<String, Color> = mapOf(
    "outline" to outline,
    "outlineVariant" to outlineVariant,
    "scrim" to scrim,
  )
}
