package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.StampedText
import com.jupydroid.app.domain.model.WorkspaceEntry

/**
 * Reads the directory the kernel, the server and the app all share.
 *
 * It is one tree - the kernel's `HOME`, the server's `--ServerApp.root_dir` and the
 * notebook repository's root - so a notebook can `open('data.csv')` on a file the user can see.
 * This is what lets the user see it back.
 *
 * Paths are **relative to the workspace root** throughout, which is `Contents.IModel.path` in
 * JupyterLab: `""` is the root, `reports/summary.ipynb` is a notebook one level down. Nothing above
 * the root is addressable - `list("../..")` is refused, not served, the way jupyter_server's
 * `_validate_path` refuses anything resolving outside `root_dir`.
 *
 * ```
 * repository.list("reports")   // entries, directories first, natural name order
 * ```
 */
interface WorkspaceRepository {
  /**
   * What is in [path], already in the order the browser shows it.
   *
   * Ordered here rather than by the caller because the order is part of the behaviour, not a
   * presentation choice: `Private.sort` puts directories first and compares names naturally.
   * Hidden entries are left out, as `ContentsManager.allow_hidden` (false by default) leaves them
   * out of the reference.
   */
  suspend fun list(path: String): List<WorkspaceEntry>

  /**
   * The whole file at [path] as text.
   *
   * One string rather than a stream: this serves an editor, and `Contents.IModel.content` for a
   * text file is likewise the whole thing (`fileFormat: 'text'` in the type registry). Refuses a
   * path outside the root, and a path that names nothing, the same way [list] does. For a reader
   * that will not write it back.
   *
   * @throws FileNotTextException when the file is not UTF-8; see [readStampedText].
   */
  @Throws(FileNotTextException::class)
  suspend fun readText(path: String): String = readStampedText(path).text

  /**
   * The file at [path] as text and the stamp of the file it came from, for a caller that will save
   * it: the stamp is what [writeText] is handed back as `expected`.
   *
   * ```
   * val (text, stamp) = repository.readStampedText("reports/notes.txt")
   * ```
   *
   * A file that is not UTF-8 is refused rather than decoded leniently: [writeText] writes UTF-8,
   * so the string would be saved over every byte the decoder gave up on. [readBytes] still reads it.
   *
   * @throws FileNotTextException when the file is not UTF-8 text - an image, an archive, another encoding.
   */
  @Throws(FileNotTextException::class)
  suspend fun readStampedText(path: String): StampedText

  /**
   * Replaces the file at [path] with [text], creating it when it is not there.
   *
   * Whole-file too, because that is what `contents.save` sends. The parent directory must already
   * exist - creating one is a separate act with its own name, as `newUntitled(type: 'directory')`
   * is in the reference.
   *
   * Answers with the stamp the file has now - the `expected` of the save after this one. With an
   * [expected] stamp the write is refused when the file is no longer the one that stamp describes,
   * because the workspace is the shell's and the kernel's too; a file that already holds exactly
   * [text] is not a change, and a file that is gone is written back. With `null` the write is
   * unconditional - a caller with a check of its own, or the user's own "Overwrite". The rule is
   * [NotebookRepository.saveNotebook]'s.
   *
   * ```
   * stamp = repository.writeText("reports/notes.txt", edited, expected = stamp)
   * ```
   *
   * @throws FileChangedOnDiskException when the file changed since [expected] was taken.
   */
  @Throws(FileChangedOnDiskException::class)
  suspend fun writeText(path: String, text: String, expected: FileStamp? = null): FileStamp

  /**
   * The whole file at [path] as bytes, for content this app did not author as text.
   *
   * Beside [readText] rather than under it: `Contents.IModel` types content by the file's own
   * format - `'text'` or `'base64'` - and reading a binary file as a string is how a zip becomes a
   * corrupt zip (`ContentsManager.get(format=...)`, `filemanager.py`).
   */
  suspend fun readBytes(path: String): ByteArray

  /**
   * Replaces the file at [path] with [bytes], creating it when it is not there.
   *
   * The byte form of [writeText], and export is why it exists: an nbconvert export of a notebook
   * with an image output comes back as a **zip**, and the
   * shape depends on the format *and* on the notebook, so nothing that writes an export may
   * assume text.
   */
  suspend fun writeBytes(path: String, bytes: ByteArray)
}
