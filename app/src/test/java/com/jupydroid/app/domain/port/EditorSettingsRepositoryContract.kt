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

  /**
   * `accessKernelHistory` is **off** until asked for, which is the reference's own default
   * (`packages/notebook-extension/schema/tracker.json:873-878`,
   * `packages/notebook/src/widget.ts:1488`) and its own documented instruction: *"To use this
   * feature, enable kernel history access in the notebook settings"*
   * (`docs/source/user/notebook.md:191`).
   *
   * The first field here that keeps a reference default rather than departing from it - see
   * `docs/DECISIONS.md` for why the two beside it do not.
   */
  @Test
  fun defaultsToKernelHistoryAccessOff() = runTest {
    assertFalse(repository().observeSettings().first().accessKernelHistory)
  }

  @Test
  fun remembersEveryFieldOfAnUpdateAndNotOnlyTheFirst() = runTest {
    val repository = repository()

    repository.update(EditorSettings(accessKernelHistory = true))

    assertTrue(repository.observeSettings().first().accessKernelHistory)
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
