package com.jupydroid.app.domain.model

/**
 * The app-wide theme choice - collapsing the reference's `adaptive-theme` boolean plus its two
 * named-theme strings (`preferred-light-theme`/`preferred-dark-theme`,
 * `packages/apputils-extension/schema/themes.json:123-153`) into one tri-state choice: this app has
 * exactly two palettes, not an open-ended registry, so there is nothing for a second string to
 * name.
 */
enum class ThemeOverride {
  SYSTEM,
  LIGHT,
  DARK,
}

/**
 * Which of the two palettes [ThemeOverride] resolves to, given whatever the device itself reports.
 *
 * The reference's own resolution (`ThemeManager.preferredTheme`, `thememanager.ts:68-92`) is this
 * same three-way branch against `isSystemColorSchemeDark()`; Compose's `isSystemInDarkTheme()` is
 * the direct equivalent of the reference's `matchMedia('(prefers-color-scheme: dark)')` listener,
 * so [SYSTEM] needs no polling of its own - the caller passes the live value straight through.
 *
 * ```
 * ThemeOverride.SYSTEM.resolvesToDark(systemInDarkTheme = true) // true
 * ThemeOverride.LIGHT.resolvesToDark(systemInDarkTheme = true) // false
 * ```
 */
fun ThemeOverride.resolvesToDark(systemInDarkTheme: Boolean): Boolean = when (this) {
  ThemeOverride.SYSTEM -> systemInDarkTheme
  ThemeOverride.LIGHT -> false
  ThemeOverride.DARK -> true
}
