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
 * (before this).
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
   * **`secondaryContainer` is the toggle's own id, and until P7a-1b it was the list's.**
   *
   * The role has four consumers and exactly one was a list: the explorer's open row. The other
   * three - the search toggles' on-wash and the two contextual top bars - cite
   * `inputOption.activeBackground`, and `docs/DECISIONS.md`'s P2x-3d folded them together on the
   * grounds that the two ids measured 4/255 apart.
   *
   * The fold could not survive the alpha. `list.inactiveSelectionBackground` resolves `#DADADA99`
   * in 2026 Light, so honouring it would have made the toggle's wash translucent where the
   * reference draws it opaque, and 17/255 from the id it cites. The row moved to
   * [ChromePalette.listInactiveSelectionBackground] and the role kept the three consumers it
   * always described.
   *
   * Pinned by **equality to the resolved value**, the way `EditorPaletteTest` pins the syntax
   * colours and `ChromePaletteTest` the parts - membership on the ramp cannot say *which* position,
   * which is the hole this closes.
   *
   * ```sh
   * python3 scripts/resolve_vscode_theme.py 2026-dark.json --color inputOption.activeBackground
   * ```
   */
  @Test
  fun theSecondaryContainerIsTheReferencesOwnToggleWash() {
    assertEquals(Color(0xFF313233), DarkColorScheme.secondaryContainer)
    assertEquals(Color(0xFFD6D6D6), LightColorScheme.secondaryContainer)
  }

  @Test
  fun everyDarkPairClearsItsContrastFloor() = assertEveryPairClearsItsFloor("dark", DarkColorScheme)

  @Test
  fun everyLightPairClearsItsContrastFloor() = assertEveryPairClearsItsFloor("light", LightColorScheme)

  /**
   * **Membership alone cannot hold this ramp.** The test above says every role came from
   * [ColorRamp]; it cannot say the role is legible on what it is drawn over, and P2u-1 turns on
   * exactly that. Two of the mappings `docs/PLAN.md` named did not survive being measured -
   * `primary` from `button.background` puts nine text sites at 3.88:1 in dark, and `outline` from
   * `panel.border` measures 1.31:1 - so both were re-sourced, and without an assertion the
   * re-sourcing is a comment that the next transcription silently undoes.
   *
   * `docs/DECISIONS.md` already states the rule this enforces: *a value may not be nudged "to
   * match VS Code more closely" without re-measuring.*
   */
  private fun assertEveryPairClearsItsFloor(name: String, scheme: ColorScheme) {
    val shortfalls = scheme.contrastPairs()
      .mapValues { (_, pairing) -> pairing to contrastRatio(pairing.foreground, pairing.background) }
      .filterValues { (pairing, ratio) -> ratio < pairing.floor }
      .map { (label, measured) -> "$label ${"%.2f".format(measured.second)}:1 below ${measured.first.floor}" }
    assertEquals("$name pairs under their floor", emptyList<String>(), shortfalls)
  }

  /** A foreground, what it is drawn over, and the floor WCAG asks of that pair. */
  private data class Pairing(val foreground: Color, val background: Color, val floor: Double)

  private fun ColorScheme.contrastPairs(): Map<String, Pairing> =
    accentPairs() + surfacePairs() + textOnContainers() + accentsOnTheSurface()

  private fun ColorScheme.accentPairs(): Map<String, Pairing> = mapOf(
    "onPrimary on primary" to Pairing(onPrimary, primary, TEXT_FLOOR),
    "onPrimaryContainer on primaryContainer" to Pairing(onPrimaryContainer, primaryContainer, TEXT_FLOOR),
    "onSecondary on secondary" to Pairing(onSecondary, secondary, TEXT_FLOOR),
    "onSecondaryContainer on secondaryContainer" to Pairing(onSecondaryContainer, secondaryContainer, TEXT_FLOOR),
    "onTertiary on tertiary" to Pairing(onTertiary, tertiary, TEXT_FLOOR),
    "onTertiaryContainer on tertiaryContainer" to Pairing(onTertiaryContainer, tertiaryContainer, TEXT_FLOOR),
    "onError on error" to Pairing(onError, error, TEXT_FLOOR),
    "onErrorContainer on errorContainer" to Pairing(onErrorContainer, errorContainer, TEXT_FLOOR),
  )

  private fun ColorScheme.surfacePairs(): Map<String, Pairing> = mapOf(
    "onBackground on background" to Pairing(onBackground, background, TEXT_FLOOR),
    "onSurface on surface" to Pairing(onSurface, surface, TEXT_FLOOR),
    "onSurfaceVariant on surfaceVariant" to Pairing(onSurfaceVariant, surfaceVariant, TEXT_FLOOR),
    "inverseOnSurface on inverseSurface" to Pairing(inverseOnSurface, inverseSurface, TEXT_FLOOR),
  )

  /**
   * Material draws body text on every container step, not only on `surface` - a menu is
   * `surfaceContainer`, a bottom sheet `surfaceContainerLow` - so each step owes the same 4.5:1.
   */
  private fun ColorScheme.textOnContainers(): Map<String, Pairing> = mapOf(
    "onSurface on surfaceContainerLowest" to Pairing(onSurface, surfaceContainerLowest, TEXT_FLOOR),
    "onSurface on surfaceContainerLow" to Pairing(onSurface, surfaceContainerLow, TEXT_FLOOR),
    "onSurface on surfaceContainer" to Pairing(onSurface, surfaceContainer, TEXT_FLOOR),
    "onSurface on surfaceContainerHigh" to Pairing(onSurface, surfaceContainerHigh, TEXT_FLOOR),
    "onSurface on surfaceContainerHighest" to Pairing(onSurface, surfaceContainerHighest, TEXT_FLOOR),
  )

  /**
   * `primary` is held to the **text** floor because that is what this app draws with it: nine
   * sites, every one a label, an icon tint or a caret, and not one a filled button
   * (`HiddenCellsRow`, `CollapsedCell`, `CellHeader`, `HeadingCollapser`, `TableOfContentsPanel`).
   *
   * `outline` is held to 1.4.11's 3:1 instead, because it bounds a component rather than spelling
   * anything - `RichText.kt`'s blockquote rule is its one reader. `outlineVariant` is exempt by
   * the same reasoning inverted: Material's decorative divider is *meant* to be faint.
   */
  private fun ColorScheme.accentsOnTheSurface(): Map<String, Pairing> = mapOf(
    "primary on surface" to Pairing(primary, surface, TEXT_FLOOR),
    "primary on surfaceContainer" to Pairing(primary, surfaceContainer, TEXT_FLOOR),
    "error on surface" to Pairing(error, surface, TEXT_FLOOR),
    "outline on surface" to Pairing(outline, surface, NON_TEXT_FLOOR),
  )

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

  private companion object {
    /** WCAG 2.1 AA for body text, the same floor `EditorPaletteTest` holds the syntax colours to. */
    const val TEXT_FLOOR = 4.5

    /** WCAG 2.1 1.4.11, the floor for a component's bounds rather than for text. */
    const val NON_TEXT_FLOOR = 3.0
  }
}
