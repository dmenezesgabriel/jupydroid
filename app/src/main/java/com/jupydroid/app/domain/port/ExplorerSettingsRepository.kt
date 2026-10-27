package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ExplorerSettings
import kotlinx.coroutines.flow.Flow

/**
 * The app-wide explorer preferences, persisted - whether the Recents section is shut, today.
 *
 * The reference persists exactly this and on every change: `setCollapsed(id, collapsed)` writes the
 * view's state straight through to storage (`viewContainerModel.ts:456-461`), so a section shut
 * before lunch is still shut after a restart.
 *
 * Grown the way [EditorSettingsRepository] and [TerminalSettingsRepository] are rather than folded
 * into either - see [ExplorerSettings].
 *
 * ```
 * explorerSettings.update(ExplorerSettings(recentsCollapsed = false))
 * ```
 */
interface ExplorerSettingsRepository {
  /** Current settings, and every update after - the app's single source of truth for them. */
  fun observeSettings(): Flow<ExplorerSettings>

  /** Replaces the stored settings outright; the caller reads the current ones first if it needs to. */
  suspend fun update(settings: ExplorerSettings)
}
