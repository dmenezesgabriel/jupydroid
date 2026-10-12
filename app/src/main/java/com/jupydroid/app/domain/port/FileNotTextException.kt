package com.jupydroid.app.domain.port

/**
 * A file asked for as text is not UTF-8, so no string stands for it: an image, an archive, or text
 * in another encoding.
 *
 * Refused rather than decoded leniently, because the string would be saved back as UTF-8 over
 * every byte the decoder gave up on. The rule is the shipped server's -
 * `jupyter_server/services/contents/fileio.py` `_read_file` answers a `format="text"` read of such
 * a file with 400 "is not UTF-8 encoded" - where vscode@3f3380c6 looks for a NUL in the first 512
 * bytes (`src/vs/workbench/services/textfile/common/encoding.ts`, `detectEncodingFromBuffer`),
 * which lets a Latin-1 file through.
 *
 * A type of its own so a reader can tell "not text" from "not there". Its message is what the file
 * editor shows in place of a field.
 *
 * ```
 * try { repository.readStampedText("figures/plot.png") }
 * catch (e: FileNotTextException) { showWhyThereIsNoField(e.message) }
 * ```
 */
class FileNotTextException(path: String, cause: Throwable? = null) :
  IllegalStateException(
    "'$path' is not UTF-8 text, so it is not opened in the text editor: an edit would be saved over its bytes",
    cause,
  )

/**
 * These bytes as the text of the file at [path], or a [FileNotTextException] when they are not
 * UTF-8 - the one decode every [WorkspaceRepository] reads text through, so the disk and its fake
 * refuse the same files.
 *
 * ```
 * val text = file.readBytes().decodedAsTextOf("reports/notes.txt")
 * ```
 */
fun ByteArray.decodedAsTextOf(path: String): String = try {
  decodeToString(throwOnInvalidSequence = true)
} catch (malformed: CharacterCodingException) {
  throw FileNotTextException(path, malformed)
}
