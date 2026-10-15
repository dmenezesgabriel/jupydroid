package com.jupydroid.app.domain.model

/**
 * The rule for what a file may be called - a notebook, a `.py`, or a folder.
 *
 * Taken from JupyterLab rather than invented
 * (`jupyterlab@7f0e6906 packages/docmanager/src/dialogs.ts`, `isValidFileName`):
 * a name must have nonzero length and must not contain `/`, `\` or `:`.
 *
 * ```
 * if (!isValidFileName(typed)) { /* tell the user which characters are refused */ }
 * ```
 *
 * **Rejects, never rewrites.** Turning `a/b` quietly into `a-b` hands the user a notebook they did
 * not name and cannot find by the name they typed. The reference shows an error naming the three
 * characters; so should we.
 *
 * Pure, so it belongs in the domain: no file, no repository, nothing to mock.
 */
fun isValidFileName(name: String): Boolean {
  val trimmed = name.trim()
  return trimmed.isNotEmpty() && FORBIDDEN.none { it in trimmed } && !trimmed.startsWith(".")
}

/**
 * A path relative to the workspace root whose every segment passes [isValidFileName] - what a
 * Save As is typed as, since jupyterlab@7f0e690's `SaveWidget` edits the whole path so a copy can
 * go into another folder (`packages/docregistry/src/context.ts`, `Private.getSavePath`). The name
 * rule refusing a leading `.` is what refuses `..` here too.
 *
 * ```
 * isValidWorkspacePath("reports/notes-copy.py")   // true
 * ```
 */
fun isValidWorkspacePath(path: String): Boolean = path.split('/').all(::isValidFileName)

/**
 * The folder a notebook id names, joined back onto a new name.
 *
 * An id is a *path* and a rename supplies a *name*, so the two are joined rather than swapped.
 * `Context._rename` replaces the last segment and keeps the rest
 * (`packages/docregistry/src/context.ts:611-614`):
 *
 * ```ts
 * const splitPath = this.localPath.split('/');
 * splitPath[splitPath.length - 1] = newName;
 * let newPath = PathExt.join(...splitPath);
 * ```
 *
 * The file browser joins the same way, onto the directory it is showing -
 * `PathExt.join(this._model.path, newName)` (`packages/filebrowser/src/listing.ts:2621`) - and
 * `isValidFileName` refuses `/`, `\` and `:` in a name (`packages/docmanager/src/dialogs.ts:257`)
 * so a name can never carry a folder of its own. Without this a rename moves a notebook to the
 * root, silently, before notebooks in folders were reachable at all.
 *
 * ```
 * idBeside("reports/Draft", "Final")   // "reports/Final"
 * idBeside("Draft", "Final")           // "Final"
 * ```
 */
fun idBeside(id: String, name: String): String {
  val folder = id.substringBeforeLast('/', missingDelimiterValue = "")
  return if (folder.isEmpty()) name else "$folder/$name"
}

/** The last segment of a notebook id, which is what a title bar shows - `Contents.IModel.name`. */
fun notebookLeafOf(id: String): String = id.substringAfterLast('/')

/**
 * Where [path] ends up when [from] is renamed to [to], or null when it did not move.
 *
 * `Context._onFileChanged` (`packages/docregistry/src/context.ts`), which is how an open document
 * learns that a rename made *somewhere else* - the file browser, another client - moved its file.
 * It matches by prefix on purpose, in its own words: "When folder name changed, `oldPath` is
 * `foo`, `newPath` is `bar` and `this._path` is `foo/test`, we should update `foo/test` to
 * `bar/test` as well".
 *
 * The one correction: the reference tests `this._path.indexOf(oldPath) === 0`, which also matches
 * `foobar/x` against `foo` and would retarget a document that never moved. Segments are compared
 * whole here.
 *
 * ```
 * pathAfterRename("foo/test", from = "foo", to = "bar")   // "bar/test"
 * pathAfterRename("other.ipynb", from = "a", to = "b")    // null
 * ```
 */
fun pathAfterRename(path: String, from: String, to: String): String? = when {
  path == from -> to
  path.startsWith("$from/") -> to + path.removePrefix(from)
  else -> null
}

/**
 * `/` and `\` would make the name a path, `:` is refused by the reference alongside them, and a
 * leading `.` hides the file - from the explorer Phase X is for, from `ls`, and from
 * jupyter_server, whose `ContentsManager.allow_hidden` is false by default.
 */
private val FORBIDDEN = listOf('/', '\\', ':')

const val NOTEBOOK_SUFFIX = ".ipynb"

/** Without the dot, which is the form `increment_filename` compares against. */
private const val NOTEBOOK_EXTENSION = "ipynb"

/**
 * The first name in the series that [isTaken] says nothing holds.
 *
 * `FileContentsManager.increment_filename`, transcribed from the jupyter_server in our own rootfs
 * (`services/contents/manager.py`):
 *
 * ```python
 * for i in itertools.count():
 *     insert_i = f"{insert}{i}" if i else ""
 *     name = f"{basename}{insert_i}{suffix}"
 *     if not self.exists(f"{path}/{name}"):
 *         break
 * ```
 *
 * So the bare name comes first and the counter starts at 1 with **no separator** - `Untitled1`,
 * not `Untitled-1`. Copied rather than invented because the server that serves this same workspace
 * names files this way, and two conventions in one directory is two answers to one question.
 *
 * ```
 * incrementedName("Untitled") { File(dir, "$it.ipynb").exists() }
 * ```
 */
fun incrementedName(base: String, insert: String = "", isTaken: (String) -> Boolean): String {
  var counter = 0
  while (true) {
    val candidate = if (counter == 0) base else "$base$insert$counter"
    if (!isTaken(candidate)) return candidate
    counter++
  }
}

/**
 * Where a file's name ends and its suffix begins.
 *
 * `increment_filename` (`services/contents/manager.py:543-549`), and the asymmetry is the point:
 *
 * ```python
 * basename, dot, ext = filename.rpartition(".")
 * if ext != "ipynb":
 *     basename, dot, ext = filename.partition(".")
 * suffix = dot + ext
 * ```
 *
 * A notebook is read from the *right*, so `data.backup.ipynb` keeps the middle dot in its stem.
 * Everything else is read from the *left*, which is what gives `archive.tar.gz` the whole
 * `.tar.gz` - the server's own comment calls it "the full suffix".
 *
 * ```
 * stemAndSuffixOf("Untitled.ipynb")   // "Untitled" to ".ipynb"
 * stemAndSuffixOf("archive.tar.gz")   // "archive" to ".tar.gz"
 * ```
 */
fun stemAndSuffixOf(fileName: String): Pair<String, String> {
  val fromTheRight = fileName.substringBeforeLast('.', missingDelimiterValue = "")
  if (fileName.substringAfterLast('.', missingDelimiterValue = "") == NOTEBOOK_EXTENSION) {
    return fromTheRight to NOTEBOOK_SUFFIX
  }
  val stem = fileName.substringBefore('.', missingDelimiterValue = fileName)
  return stem to fileName.removePrefix(stem)
}

/**
 * The first name in the series nothing holds, counting on the stem so the suffix survives.
 *
 * [incrementedName] applied where `increment_filename` applies it: `f"{basename}{insert_i}{suffix}"`
 * - `untitled1.txt`, never `untitled.txt1`.
 *
 * ```
 * incrementedFileName("Untitled.ipynb") { File(dir, it).exists() }
 * incrementedFileName("Untitled Folder", insert = " ") { File(dir, it).exists() }
 * ```
 */
fun incrementedFileName(fileName: String, insert: String = "", isTaken: (String) -> Boolean): String {
  val (stem, suffix) = stemAndSuffixOf(fileName)
  return incrementedName(stem, insert) { isTaken("$it$suffix") } + suffix
}
