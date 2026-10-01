package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.flow.Flow

/**
 * The app-wide editor preferences, persisted - `notebook:toggle-autoclosing-brackets` (and its
 * universal sibling `fileeditor:toggle-autoclosing-brackets-universal`) today, grown
 * rather than replaced.
 *
 * A port with one implementation, kept because a uniform port surface beats case-by-case
 * minimalism (`AGENTS.md`): every setting screen and every editor reads through here, never
 * through a concrete adapter.
 *
 * ```
 * editorSettings.update(editorSettings.observeSettings().first().copy(autoClosingBrackets = false))
 * ```
 */
interface EditorSettingsRepository {
  /** Current settings, and every update after - the app's single source of truth for them. */
  fun observeSettings(): Flow<EditorSettings>

  /** Replaces the stored settings outright; the caller reads the current ones first if it needs to. */
  suspend fun update(settings: EditorSettings)
}
