package com.jupydroid.app.import

private const val IPYNB_SUFFIX = ".ipynb"
private const val DEFAULT_IMPORT_NAME = "Imported notebook$IPYNB_SUFFIX"

/**
 * A `.ipynb` another app handed over: the [uri] it serves the document at, and the [fileName] the
 * copy lands under in the workspace.
 */
data class SharedNotebook(val uri: String, val fileName: String)

/**
 * The notebook a VIEW intent shares, or null when what it shares is not one.
 *
 * `.ipynb` has no standard MIME type, so the name the provider reports is the only test of what the
 * document is. The name keeps its suffix because the copy *is* a file in the workspace, and only
 * its last segment: a provider's name is its own to report, and a folder in it is none of the
 * workspace's.
 *
 * ```
 * sharedNotebookFrom("content://media/external/file/42", "Intro.ipynb") // lands as Intro.ipynb
 * ```
 */
fun sharedNotebookFrom(uri: String, displayName: String?): SharedNotebook? {
  val name = displayName?.substringAfterLast('/')?.trim()
  if (name == null || !name.endsWith(IPYNB_SUFFIX, ignoreCase = true)) return null
  val stem = name.dropLast(IPYNB_SUFFIX.length)
  return SharedNotebook(uri, if (stem.isBlank()) DEFAULT_IMPORT_NAME else name)
}
