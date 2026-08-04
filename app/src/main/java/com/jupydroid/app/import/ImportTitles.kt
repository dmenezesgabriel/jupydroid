package com.jupydroid.app.import

private const val IPYNB_SUFFIX = ".ipynb"
private const val DEFAULT_IMPORT_TITLE = "Imported notebook"

/**
 * Turns a `.ipynb` file name into a notebook title. The extension is stripped
 * (case-insensitive); blank or missing names fall back to [DEFAULT_IMPORT_TITLE].
 * Step 2 imports title-only; nbformat content parsing lands in Step 3.
 *
 * Example: `importTitleFrom("Intro to NumPy.ipynb") == "Intro to NumPy"`.
 */
fun importTitleFrom(fileName: String?): String {
  val trimmed = fileName?.trim().orEmpty()
  if (trimmed.isBlank()) return DEFAULT_IMPORT_TITLE
  val stripped =
    if (trimmed.endsWith(IPYNB_SUFFIX, ignoreCase = true)) {
      trimmed.dropLast(IPYNB_SUFFIX.length)
    } else {
      trimmed
    }
  return stripped.ifBlank { DEFAULT_IMPORT_TITLE }
}
