package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.AutosaveInterval
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.IndentUnit
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
 * `packages/fileeditor-extension/src/commands.ts:552-575`) - grown rather than replaced,
 * the way [RecentDocumentsRepository]'s port is.
 */
abstract class EditorSettingsRepositoryContract {
  protected abstract fun repository(): EditorSettingsRepository

  /**
   * Auto-closing brackets is on until told otherwise, matching what this app has always done -
   * a departure from the reference's own `default: false`
   * (`packages/codemirror/src/extension.ts:636-644`): on a soft keyboard `(` and `)` are two trips
   * through the symbol row.
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
   * The first field here that keeps a reference default rather than departing from it; the two
   * beside it depart because a soft keyboard changes the cost of the thing being defaulted.
   */
  @Test
  fun defaultsToKernelHistoryAccessOff() = runTest {
    assertFalse(repository().observeSettings().first().accessKernelHistory)
  }

  /**
   * `notebook:toggle-all-cell-line-numbers` - on by default, matching what `EditorUiState` always
   * defaulted to before this field moved out of per-notebook ViewModel state and into the
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

  /**
   * `symbolRowShown` defaults to `true` - shipping this field changes no pixel until a user
   * turns it off, the same reasoning `codeWrapEnabled`'s and `completionEnabled`'s own defaults
   * carry. There is no reference command to depart from here: `SymbolRow` is itself a phone-only
   * adaptation with no JupyterLab/IntelliJ counterpart.
   */
  @Test
  fun defaultsToSymbolRowShown() = runTest {
    assertTrue(repository().observeSettings().first().symbolRowShown)
  }

  @Test
  fun remembersSymbolRowShownTurnedOff() = runTest {
    val repository = repository()

    repository.update(EditorSettings(symbolRowShown = false))

    assertFalse(repository.observeSettings().first().symbolRowShown)
  }

  /**
   * `indentUnit` - the schema's own `'4'`, its default
   * (`packages/codemirror/src/extension.ts:707-720`): "The indentation is a `Tab` or the number
   * of spaces." Shipping it changes no keystroke until a user picks otherwise - the typing paths
   * it feeds already used four spaces.
   */
  @Test
  fun defaultsToFourSpacesAsTheIndentUnit() = runTest {
    assertEquals(IndentUnit.SPACES_4, repository().observeSettings().first().indentUnit)
  }

  @Test
  fun remembersAnIndentUnitChange() = runTest {
    val repository = repository()

    repository.update(EditorSettings(indentUnit = IndentUnit.TAB))

    assertEquals(IndentUnit.TAB, repository.observeSettings().first().indentUnit)
  }

  /**
   * `docmanager:toggle-autosave` - on by default, the schema's own value
   * (`packages/docmanager-extension/schema/plugin.json:113-118`). Shipping this field changes no
   * behaviour until a user turns it off, the same reasoning `codeWrapEnabled`'s default carries.
   */
  @Test
  fun defaultsToAutosaveOn() = runTest {
    assertTrue(repository().observeSettings().first().autosaveEnabled)
  }

  @Test
  fun remembersAutosaveTurnedOff() = runTest {
    val repository = repository()

    repository.update(EditorSettings(autosaveEnabled = false))

    assertFalse(repository.observeSettings().first().autosaveEnabled)
  }

  /**
   * `autosaveInterval` defaults to the schema's own 120 seconds
   * (`packages/docmanager-extension/schema/plugin.json:119-124`) - how stale a document is allowed
   * to get, the question `SaveHandler`'s timer answers (`docmanager/src/savehandler.ts:21-30`).
   */
  @Test
  fun defaultsToTheReferenceAutosaveInterval() = runTest {
    assertEquals(AutosaveInterval.DEFAULT, repository().observeSettings().first().autosaveIntervalSeconds)
  }

  @Test
  fun remembersAnAutosaveIntervalChange() = runTest {
    val repository = repository()

    repository.update(EditorSettings(autosaveIntervalSeconds = 30))

    assertEquals(30, repository.observeSettings().first().autosaveIntervalSeconds)
  }

  /**
   * `kernelShutdown` - off by default, the schema's own value
   * (`packages/notebook-extension/schema/tracker.json:741-746`): closing a notebook lets the
   * kernel live, so reopening reattaches to it rather than booting a fresh interpreter.
   */
  @Test
  fun defaultsToLeavingTheKernelRunning() = runTest {
    assertFalse(repository().observeSettings().first().shutdownKernelOnClose)
  }

  @Test
  fun remembersKernelShutdownTurnedOn() = runTest {
    val repository = repository()

    repository.update(EditorSettings(shutdownKernelOnClose = true))

    assertTrue(repository.observeSettings().first().shutdownKernelOnClose)
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
