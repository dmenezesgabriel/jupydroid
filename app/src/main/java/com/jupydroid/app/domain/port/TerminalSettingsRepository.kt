package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.TerminalSettings
import kotlinx.coroutines.flow.Flow

/**
 * The app-wide terminal preferences, persisted - the font-size setting today,
 * grown the way [EditorSettingsRepository] is rather than folded into it (`domain/model/TerminalSettings.kt`).
 *
 * ```
 * terminalSettings.update(terminalSettings.observeSettings().first().copy(fontSizeSp = 16))
 * ```
 */
interface TerminalSettingsRepository {
  /** Current settings, and every update after - the app's single source of truth for them. */
  fun observeSettings(): Flow<TerminalSettings>

  /** Replaces the stored settings outright; the caller reads the current ones first if it needs to. */
  suspend fun update(settings: TerminalSettings)
}
