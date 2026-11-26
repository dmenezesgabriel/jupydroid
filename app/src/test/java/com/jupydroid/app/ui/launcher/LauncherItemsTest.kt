package com.jupydroid.app.ui.launcher

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelSpecModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The launcher's cards as the reference builds them: `notebook-extension/src/index.ts:2589-2608`
 * and `console-extension/src/index.ts:400-418` add one card per kernelspec to the Notebook and
 * Console categories - labelled by the spec's `display_name`, the default spec at rank 0 and the
 * rest ordered by label (`widget.tsx:378-396` sortCmp) - while the utility cards stay the
 * fileeditor/terminal extensions' own.
 */
class LauncherItemsTest {
  private fun spec(name: String, displayName: String = "Python ($name)") =
    KernelSpec(name = name, displayName = displayName, language = "python")

  private fun models(vararg specs: KernelSpec, default: String = specs.first().name) =
    KernelSpecModels(default = default, specs = specs.toList())

  @Test
  fun everySpecGetsANotebookAndAConsoleCardLabelledByItsDisplayName() {
    val python3 = spec("python3", "Python 3")
    val probe = spec("probe", "Python (probe)")

    val items = launcherItems(models(python3, probe))

    assertEquals(
      listOf(
        LauncherItem(LauncherCommand.NEW_NOTEBOOK, "Python 3", LauncherCategory.NOTEBOOK, python3),
        LauncherItem(LauncherCommand.NEW_NOTEBOOK, "Python (probe)", LauncherCategory.NOTEBOOK, probe),
        LauncherItem(LauncherCommand.NEW_CONSOLE, "Python 3", LauncherCategory.CONSOLE, python3),
        LauncherItem(LauncherCommand.NEW_CONSOLE, "Python (probe)", LauncherCategory.CONSOLE, probe),
      ),
      items.filter { it.kernel != null },
    )
  }

  /** `rank = name === specs.default ? 0 : Infinity`, then label - the default's cards lead. */
  @Test
  fun theDefaultKernelsCardsComeFirstAndTheRestSortByDisplayName() {
    val zeta = spec("zeta", "Zeta Python")
    val alpha = spec("alpha", "Alpha Python")
    val beta = spec("beta", "Beta Python")

    val notebooks = launcherItems(models(zeta, alpha, beta, default = "zeta"))
      .filter { it.category == LauncherCategory.NOTEBOOK }
      .map { it.label }

    assertEquals(listOf("Zeta Python", "Alpha Python", "Beta Python"), notebooks)
  }

  /** A default that sorts last by label still leads - rank beats label in sortCmp. */
  @Test
  fun theDefaultLeadsEvenWhenItsLabelSortsLast() {
    val aardvark = spec("aardvark", "Aardvark Python")
    val zebra = spec("zebra", "Zebra Python")

    val notebooks = launcherItems(models(aardvark, zebra, default = "zebra"))
      .filter { it.category == LauncherCategory.NOTEBOOK }
      .map { it.label }

    assertEquals(listOf("Zebra Python", "Aardvark Python"), notebooks)
  }

  @Test
  fun theUtilityCardsFollowTheKernelSectionsAndCarryNoKernel() {
    val items = launcherItems(models(spec("python3", "Python 3")))

    assertEquals(
      listOf(
        LauncherCommand.NEW_TERMINAL to "Terminal",
        LauncherCommand.NEW_TEXT_FILE to "Text File",
        LauncherCommand.NEW_MARKDOWN_FILE to "Markdown File",
        LauncherCommand.NEW_PYTHON_FILE to "Python File",
      ),
      items.takeLast(4).map { it.command to it.label },
    )
    assertEquals(LauncherCategory.OTHER, items.takeLast(4).first().category)
    assertEquals(listOf(null, null, null, null), items.takeLast(4).map { it.kernel })
  }

  @Test
  fun anEmptyCatalogueLeavesOnlyTheUtilityCards() {
    val items = launcherItems(KernelSpecModels(default = "", specs = emptyList()))

    assertEquals(4, items.size)
    assertEquals(LauncherCategory.OTHER, items.first().category)
  }

  /**
   * Two cards share `NEW_NOTEBOOK` once a Space has two kernels, so the tag a Maestro flow or a
   * Compose test addresses carries the kernel's name - `_kernel_name_pat`-safe, so resource-id
   * safe. A utility card's tag stays the bare command it always was.
   */
  @Test
  fun theTagNamesTheKernelOnlyOnKernelCards() {
    val python3 = spec("python3", "Python 3")
    val probe = spec("proj-a-.venv", "Python (proj-a/.venv)")
    val items = launcherItems(models(python3, probe))

    assertEquals(
      "launcher-card-NEW_NOTEBOOK-python3",
      items.single { it.command == LauncherCommand.NEW_NOTEBOOK && it.kernel == python3 }.testTag,
    )
    assertEquals(
      "launcher-card-NEW_NOTEBOOK-proj-a-.venv",
      items.single { it.command == LauncherCommand.NEW_NOTEBOOK && it.kernel == probe }.testTag,
    )
    assertEquals(
      "launcher-card-NEW_TERMINAL",
      items.single { it.command == LauncherCommand.NEW_TERMINAL }.testTag,
    )
    assertNull(items.single { it.command == LauncherCommand.NEW_TERMINAL }.kernel)
  }
}
