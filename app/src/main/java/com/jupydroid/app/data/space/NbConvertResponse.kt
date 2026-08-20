package com.jupydroid.app.data.space

import java.net.URLDecoder

/**
 * The three things an nbconvert response says that are not its body, parsed as text.
 *
 * Kept apart from [OkHttpNotebookExporter] because each of them was a surprise measured on the
 * tablet rather than a header read off a spec, and because parsing is worth testing without a
 * socket. The captures are `evidence/p2j-2-export-formats/bodies/`.
 */
internal object NbConvertResponse {
  /**
   * The name the server chose for the converted file, or null when it named none.
   *
   * **`jupyter_server` sends RFC 5987 and never a plain filename**, measured on every one of the
   * six formats that converted:
   *
   * ```
   * Content-Disposition: attachment; filename*=utf-8''p2j-2-export-formats.slides.html
   * ```
   *
   * `set_attachment_header` is tornado's, and it emits `filename*=` whenever the name is not
   * plain ASCII **or** always, depending on version - so both forms are read here rather than
   * only the one this rootfs happens to send.
   */
  fun fileName(contentDisposition: String?): String? {
    val header = contentDisposition ?: return null
    return extendedFileName(header) ?: plainFileName(header)
  }

  /**
   * The media type without its parameters - `text/html`, not `text/html; charset=utf-8`.
   *
   * The charset belongs to the transfer and not to the file: it is what an Android share intent
   * and a file's type are matched on, and `text/html; charset=utf-8` matches neither.
   */
  fun mediaType(contentType: String?): String? =
    contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

  /**
   * nbconvert's own sentence out of the page tornado wrapped it in.
   *
   * The whole body is an HTML error page; the one line worth showing a user is inside its `<pre>`
   * and is HTML-escaped there - *"Pandoc wasn&#39;t found."* Falling back to the raw text rather
   * than to nothing, because a server that failed differently still has to say something.
   */
  fun failureMessage(body: String): String {
    val quoted = PRE_BLOCK.find(body)?.groupValues?.get(1) ?: NBCONVERT_FAILED.find(body)?.value ?: body
    return quoted.unescapedHtml().trim().take(MESSAGE_LIMIT).ifEmpty { "the server said nothing" }
  }

  /** `filename*=utf-8''name.html` - a charset, a language, and a percent-encoded name. */
  private fun extendedFileName(header: String): String? {
    val parts = EXTENDED.find(header)?.groupValues?.get(1)?.split('\'', limit = EXTENDED_PARTS)
    if (parts == null || parts.size != EXTENDED_PARTS) return null
    val (charset, _, name) = parts
    return runCatching { URLDecoder.decode(name, charset.ifEmpty { DEFAULT_CHARSET }) }.getOrNull()
  }

  /** `filename="name.html"`, quoted or bare, which older tornado sends for an ASCII name. */
  private fun plainFileName(header: String): String? =
    PLAIN.find(header)?.groupValues?.get(1)?.trim('"')?.takeIf { it.isNotEmpty() }

  /** The five named entities tornado's escaping produces, since only those can appear. */
  private fun String.unescapedHtml(): String =
    replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
      .replace("&#39;", "'").replace("&amp;", "&")

  private val EXTENDED = Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
  private val PLAIN = Regex("""filename\s*=\s*("[^"]*"|[^;]+)""", RegexOption.IGNORE_CASE)
  private val PRE_BLOCK = Regex("<pre>(.*?)</pre>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
  private val NBCONVERT_FAILED = Regex("nbconvert failed[^<]*")

  /** Long enough for every message the tablet produced; short enough to put in a snackbar. */
  private const val MESSAGE_LIMIT = 500

  /** `charset'language'name` - RFC 5987 fixes the count, so a header with fewer is not one. */
  private const val EXTENDED_PARTS = 3

  /** What RFC 5987 lets a sender leave out, and what tornado sends anyway. */
  private const val DEFAULT_CHARSET = "utf-8"
}
