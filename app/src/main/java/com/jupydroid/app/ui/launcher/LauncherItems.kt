package com.jupydroid.app.ui.launcher

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelSpecModels

/**
 * The command a launcher card runs - the reference's `IItemOptions.command` string
 * (`packages/launcher/src/tokens.ts`), folded to a variant since this app's commands are a
 * closed set rather than a registry third parties extend.
 */
enum class LauncherCommand {
  /** `notebook:create-new` - a fresh notebook on the card's kernelspec. */
  NEW_NOTEBOOK,

  /** `console:create` - a standalone console on its own kernel session. */
  NEW_CONSOLE,

  /** `terminal:create-new`. */
  NEW_TERMINAL,

  /** `fileeditor:create-new` with `ext: 'txt'`. */
  NEW_TEXT_FILE,

  /** `fileeditor:create-new-file` with `ext: 'md'`. */
  NEW_MARKDOWN_FILE,

  /** `fileeditor:create-new-file` with `ext: 'py'`. */
  NEW_PYTHON_FILE,
}

/**
 * A launcher section - the reference's `category` (`IItemOptions.category`), declared in
 * category-rank order so `entries` iterates the way the launcher lays them out: Notebook 0,
 * Console 20, Other 100 (`widget.tsx:137-141`).
 */
enum class LauncherCategory(val label: String) {
  NOTEBOOK("Notebook"),
  CONSOLE("Console"),
  OTHER("Other"),
}

/**
 * One card: a label the card draws under its icon, the command a tap runs, and - on the kernel
 * cards - the [KernelSpec] the launch records or prefers, the reference's `args.kernelName`
 * (`notebook-extension/src/index.ts:2597`, `console-extension/src/index.ts:408`).
 */
data class LauncherItem(
  val command: LauncherCommand,
  val label: String,
  val category: LauncherCategory,
  val kernel: KernelSpec? = null,
) {
  /**
   * The card's test tag: `launcher-card-<COMMAND>` for the utility cards, and
   * `launcher-card-<COMMAND>-<kernelName>` on kernel cards, where two cards share a command once
   * a Space lists more than one kernelspec. Kernel names match `_kernel_name_pat`, so the suffix
   * is resource-id safe.
   */
  val testTag: String
    get() = buildString {
      append(LAUNCHER_CARD_TAG).append('-').append(command.name)
      kernel?.let { append('-').append(it.name) }
    }
}

/**
 * The launcher's cards for [specs]' Space, in the order `Private.sortCmp` (`widget.tsx:378-396`)
 * leaves them within each category: rank first - the default spec's cards at rank 0, every other
 * spec's at `Infinity` (`notebook-extension/src/index.ts:2590`) - then label, which for a kernel
 * card is the spec's `display_name`. The kernel sections come first, the utility cards last, as
 * `LAUNCHER_ITEMS` always ordered them.
 *
 * ```
 * launcherItems(KernelSpecModels("python3", listOf(python3, stats)))
 * // [Notebook/python3, Notebook/stats, Console/python3, Console/stats, Terminal, Text File, ...]
 * ```
 */
fun launcherItems(specs: KernelSpecModels): List<LauncherItem> {
  val ordered = specs.specs.sortedWith(
    compareBy<KernelSpec> { it.name != specs.default }.thenBy { it.displayName },
  )
  return ordered.map { it.notebookCard() } +
    ordered.map { it.consoleCard() } +
    UTILITY_ITEMS
}

private fun KernelSpec.notebookCard() =
  LauncherItem(LauncherCommand.NEW_NOTEBOOK, displayName, LauncherCategory.NOTEBOOK, this)

private fun KernelSpec.consoleCard() =
  LauncherItem(LauncherCommand.NEW_CONSOLE, displayName, LauncherCategory.CONSOLE, this)

/**
 * The non-kernel cards - Terminal 0, Text File 1, Markdown File 2, Python File 3
 * (`fileeditor-extension/src/commands.ts:1422-1460`) - unchanged by how many kernels a Space
 * lists, as the reference's own utility items are.
 */
private val UTILITY_ITEMS = listOf(
  LauncherItem(LauncherCommand.NEW_TERMINAL, "Terminal", LauncherCategory.OTHER),
  LauncherItem(LauncherCommand.NEW_TEXT_FILE, "Text File", LauncherCategory.OTHER),
  LauncherItem(LauncherCommand.NEW_MARKDOWN_FILE, "Markdown File", LauncherCategory.OTHER),
  LauncherItem(LauncherCommand.NEW_PYTHON_FILE, "Python File", LauncherCategory.OTHER),
)

/** What Maestro and Compose tests address - the prefix of every [LauncherItem.testTag]. */
const val LAUNCHER_CARD_TAG = "launcher-card"
