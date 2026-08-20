package com.jupydroid.app.theme

import androidx.compose.material3.Typography

/**
 * The app's text scale, which is Material's own, unchanged.
 *
 * **Stated rather than omitted, and that is the whole of the decision.** What stood here was the
 * Android Studio template: a single `bodyLarge` hand-built from a bare `TextStyle` at
 * 16sp/24sp/0.5sp. Those are the three numbers Material already supplies for that role
 * (`TypeScaleTokens.BodyLargeSize`/`BodyLargeLineHeight`/`BodyLargeTracking`), so it read as a
 * choice and made none - except one nobody chose: Material derives every role from
 * `DefaultTextStyle`, which carries `platformStyle = defaultPlatformTextStyle()`
 * (`includeFontPadding = false` on Android) and `lineHeightStyle = DefaultLineHeightStyle`
 * (`TypographyTokens.kt:272-276`). A bare `TextStyle` carries neither, so the app's most-used role
 * was the only one still laying out with legacy font padding, and had been since Step 1.
 *
 * Material's scale is kept because it is already a design system - fifteen roles with measured
 * sizes, line heights and tracking - and re-typing its numbers here would be a copy that can
 * drift from the library rather than a decision. Colour is the opposite case and that asymmetry
 * is deliberate: `DarkColorScheme` states every role because Material's default there is the
 * baseline **purple**, a colour from a different design system than the editor's
 * (`Theme.kt`, `ColorSchemeTest`).
 *
 * A real departure is allowed and belongs here, named, with the reason - and in `TypographyTest`,
 * which enumerates the roles so the next hand-built one fails a test instead of a screenshot.
 */
internal val JupydroidTypography: Typography = Typography()
