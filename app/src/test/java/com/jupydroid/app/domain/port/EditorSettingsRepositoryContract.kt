package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

  /**
   * `notebook:toggle-all-cell-line-numbers` - on by default, matching what `EditorUiState` always
   * defaulted to before P6c-2 moved this field out of per-notebook ViewModel state and into the
   * app-wide, persisted settings this repository owns.
   */
  @Test
  fun defaultsToLineNumbersShown() = runTest {
    assertTrue(repository().observeSettings().first().lineNumbersShown)
  }

  @Test
  fun remembersLineNumbersShownTurnedOff() = runTest {
    val repository = repository()

    repository.update(EditorSettings(lineNumbersShown = false))

    assertFalse(repository.observeSettings().first().lineNumbersShown)
  }

  /**
   * `codeFontSizeSp` defaults to [CodeFontSize.DEFAULT], which is `CodeText.size`'s own value -
   * shipping this field changes no pixel until a user acts, the same reasoning
   * `TerminalFontSize.DEFAULT` was given.
   */
  @Test
  fun defaultsToTheSharedCodeFontSize() = runTest {
    assertEquals(CodeFontSize.DEFAULT, repository().observeSettings().first().codeFontSizeSp)
  }

  @Test
  fun remembersACodeFontSizeChange() = runTest {
    val repository = repository()

    repository.update(EditorSettings(codeFontSizeSp = 20))

    assertEquals(20, repository.observeSettings().first().codeFontSizeSp)
  }

  /**
   * `codeWrapEnabled` defaults to `true` - a deliberate departure from the reference's own
   * `codeCellConfig.lineWrap` default of `false`
   * (`packages/notebook-extension/schema/tracker.json:699-706`), the same kind of departure
   * `autoClosingBrackets` already is: horizontal drag-to-scroll is worse touch ergonomics than
   * desktop's mouse wheel, so shipping this field changes no pixel until a user turns it off.
   */
  @Test
  fun defaultsToCodeWrapEnabled() = runTest {
    assertTrue(repository().observeSettings().first().codeWrapEnabled)
  }

  @Test
  fun remembersCodeWrapEnabledTurnedOff() = runTest {
    val repository = repository()

    repository.update(EditorSettings(codeWrapEnabled = false))

    assertFalse(repository.observeSettings().first().codeWrapEnabled)
  }

  /**
   * `completionEnabled` defaults to `true` - a faithful collapse of the reference's own ranked
   * `availableProviders` list (`packages/completer-extension/schema/manager.json:1-50`,
   * `"CompletionProvider:kernel": 550`) into one boolean, since this app has exactly one completion
   * provider rather than a ranked set of them - the same collapse `ThemeOverride` already is for the
   * reference's open-ended theme registry. Shipping this field changes no pixel until a user turns
   * it off, the same reasoning `codeWrapEnabled`'s own default carries.
   */
  @Test
  fun defaultsToCompletionEnabled() = runTest {
    assertTrue(repository().observeSettings().first().completionEnabled)
  }

  @Test
  fun remembersCompletionEnabledTurnedOff() = runTest {
    val repository = repository()

    repository.update(EditorSettings(completionEnabled = false))

    assertFalse(repository.observeSettings().first().completionEnabled)
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
