package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide theme preference - one field today, which [ThemeOverride] the app is on.
 *
 * Its own type rather than a field on [EditorSettings]: a theme choice is neither an editor
 * preference nor a terminal one (`AGENTS.md`'s "one home per kind of logic"), the same reasoning
 * [ExplorerSettings] and [TerminalSettings] already carry.
 *
 * **[ThemeOverride.SYSTEM] by default**, matching the reference's own `adaptive-theme` default of
 * `false` in spirit only where it disagrees: JupyterLab starts on a fixed light theme and lets a
 * user opt into following the system, but Compose's `isSystemInDarkTheme()` already answers that
 * question for free on first launch, so following it by default costs nothing and matches this
 * platform's own convention (`MaterialTheme` itself defaults every sample to the system scheme).
 *
 * New fields must carry a default: a `theme-settings.json` written before they existed is read
 * with `ignoreUnknownKeys` and has nothing to say about them.
 */
@Serializable
data class ThemeSettings(val override: ThemeOverride = ThemeOverride.SYSTEM)
