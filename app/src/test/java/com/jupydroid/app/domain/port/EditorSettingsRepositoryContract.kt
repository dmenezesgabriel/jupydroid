package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [EditorSettingsRepository] must do, whoever implements it.
 *
 * One boolean today - `notebook:toggle-autoclosing-brackets`, universal across the notebook and
 * the plain file editor (`fileeditor:toggle-autoclosing-brackets-universal`,
 * `packages/fileeditor-extension/src/commands.ts:552-575`) - grown by P6c rather than replaced,
 * the way [RecentDocumentsRepository]'s port is.
 */
abstract class EditorSettingsRepositoryContract {
  protected abstract fun repository(): EditorSettingsRepository

  /**
   * Auto-closing brackets is on until told otherwise, matching what this app has always done -
   * a departure from the reference's own `default: false`
   * (`packages/codemirror/src/extension.ts:636-644`), recorded in `docs/DECISIONS.md`.
   */
  @Test
  fun defaultsToAutoClosingBracketsOn() = runTest {
    assertTrue(repository().observeSettings().first().autoClosingBrackets)
  }

  @Test
  fun remembersAnUpdate() = runTest {
    val repository = repository()

    repository.update(EditorSettings(autoClosingBrackets = false))

    assertFalse(repository.observeSettings().first().autoClosingBrackets)
  }

  @Test
  fun anUpdateCanTurnItBackOn() = runTest {
    val repository = repository()
    repository.update(EditorSettings(autoClosingBrackets = false))

    repository.update(EditorSettings(autoClosingBrackets = true))

    assertTrue(repository.observeSettings().first().autoClosingBrackets)
  }
}
