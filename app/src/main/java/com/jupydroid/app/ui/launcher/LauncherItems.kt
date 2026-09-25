package com.jupydroid.app.ui.launcher

import com.jupydroid.app.domain.model.DEFAULT_KERNEL_DISPLAY_NAME

/**
 * The command a launcher card runs - the reference's `IItemOptions.command` string
 * (`packages/launcher/src/tokens.ts`), folded to a variant since this app's commands are a
 * closed set rather than a registry third parties extend.
 */
enum class LauncherCommand {
  /** `notebook:create-new` - a fresh notebook on the Space's default kernel. */
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

/** One card: a label the card draws under its icon, and the command a tap runs. */
data class LauncherItem(val command: LauncherCommand, val label: String, val category: LauncherCategory)

/**
 * The launcher's cards, in the order `Private.sortCmp` (`widget.tsx:378-396`) leaves them -
 * rank first, then label - with this server's one kernelspec collapsing each kernel section to a
 * single card. The ranks these come from: notebook/console kernel cards rank 0
 * (`notebook-extension/src/index.ts`, `console-extension/src/index.ts`), Terminal 0, Text File 1,
 * Markdown File 2, Python File 3 (`fileeditor-extension/src/commands.ts:1422-1460`).
 */
val LAUNCHER_ITEMS: List<LauncherItem> = listOf(
  LauncherItem(LauncherCommand.NEW_NOTEBOOK, DEFAULT_KERNEL_DISPLAY_NAME, LauncherCategory.NOTEBOOK),
  LauncherItem(LauncherCommand.NEW_CONSOLE, DEFAULT_KERNEL_DISPLAY_NAME, LauncherCategory.CONSOLE),
  LauncherItem(LauncherCommand.NEW_TERMINAL, "Terminal", LauncherCategory.OTHER),
  LauncherItem(LauncherCommand.NEW_TEXT_FILE, "Text File", LauncherCategory.OTHER),
  LauncherItem(LauncherCommand.NEW_MARKDOWN_FILE, "Markdown File", LauncherCategory.OTHER),
  LauncherItem(LauncherCommand.NEW_PYTHON_FILE, "Python File", LauncherCategory.OTHER),
)
