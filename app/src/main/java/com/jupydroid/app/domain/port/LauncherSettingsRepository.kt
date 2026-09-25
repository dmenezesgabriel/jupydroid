package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.LauncherSettings
import kotlinx.coroutines.flow.Flow

/**
 * The app-wide launcher bookkeeping, persisted - whether the launcher has ever presented itself.
 *
 * The one-pane window pushes the launcher on the first run only (`docs/DECISIONS.md`, P6d), so the
 * flag has to survive a restart - which is what makes it a port and not a `rememberSaveable`. At
 * two panes the reference's own rule applies instead: `maybeCreate` fills an empty main area on
 * every `layoutModified` (`launcher-extension/src/index.ts:147-157`) and needs no flag.
 *
 * ```
 * launcherSettings.update(LauncherSettings(launcherSeen = true))
 * ```
 */
interface LauncherSettingsRepository {
  /** Current settings, and every update after - the app's single source of truth for them. */
  fun observeSettings(): Flow<LauncherSettings>

  /** Replaces the stored settings outright; the caller reads the current ones first if it needs to. */
  suspend fun update(settings: LauncherSettings)
}
