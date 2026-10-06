package com.jupydroid.app.domain.model

/**
 * The id of the one kernelspec the shipped rootfs declares, `python3`.
 *
 * A *new* notebook does not read this: it records its Space's own default from `KernelSpecs`
 * (`ExplorerViewModel.create`), since a Space's catalogue is a property of its rootfs. This names
 * what that catalogue holds while nothing has been installed into it - [BASE_SPACE]'s declared list
 * and the `display_name` the writer may add (`data/disk/NotebookJsonWriter.kt`).
 */
const val DEFAULT_KERNEL_ID = "python3"

/**
 * What that kernel is *called* - the `display_name` of the kernelspec the Space ships.
 *
 * Here rather than in the three places that used to spell it, because they have to agree: the
 * kernelspec written into the rootfs (`data/space/SpaceKernelSpec.kt`), the `metadata.kernelspec`
 * a notebook is saved with (`data/disk/NotebookJsonWriter.kt`), and the name the bar draws
 * ([com.jupydroid.app.ui.editor.KernelIndicator]). It is the *live* kernel's name in all three,
 * which is the distinction `kernelDisplayName` makes in the reference: it resolves through the
 * spec manager rather than reading the file's own metadata, so an imported notebook claiming
 * "Python 3.11 (conda)" is still reported as whatever is actually going to run it
 * (`packages/apputils/src/sessioncontext.tsx:584-603`).
 */
const val DEFAULT_KERNEL_DISPLAY_NAME = "Python 3"

/**
 * The base name of a brand-new notebook.
 *
 * `new_untitled` supplies `Untitled` for `type: "notebook"` (`services/contents/manager.py`),
 * and `increment_filename` turns a taken one into `Untitled1`, the counter on the stem. Callers
 * pass this and let the repository - like the server - do the incrementing.
 */
const val UNTITLED_NOTEBOOK = "Untitled"

/**
 * [UNTITLED_NOTEBOOK] under a browsed directory - an empty directory is the workspace root, so it
 * joins nothing. The explorer's New menu and the launcher card must name the same file.
 *
 * ```
 * untitledNotebookIn("") == "Untitled"; untitledNotebookIn("reports") == "reports/Untitled"
 * ```
 */
fun untitledNotebookIn(directory: String): String =
  if (directory.isEmpty()) UNTITLED_NOTEBOOK else "$directory/$UNTITLED_NOTEBOOK"
