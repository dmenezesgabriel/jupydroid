package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ThemeSettings
import kotlinx.coroutines.flow.Flow

/**
 * The app-wide theme preference, persisted - which [com.jupydroid.app.domain.model.ThemeOverride]
 * the app is on, today.
 *
 * Grown the way [EditorSettingsRepository], [TerminalSettingsRepository] and
 * [ExplorerSettingsRepository] are rather than folded into any of them - see [ThemeSettings].
 *
 * ```
 * themeSettings.update(ThemeSettings(override = ThemeOverride.DARK))
 * ```
 */
interface ThemeSettingsRepository {
  /** Current settings, and every update after - the app's single source of truth for them. */
  fun observeSettings(): Flow<ThemeSettings>

  /** Replaces the stored settings outright; the caller reads the current ones first if it needs to. */
  suspend fun update(settings: ThemeSettings)
}
