package com.jupydroid.app.domain.port

/**
 * Offers a file in the workspace to whatever else on the device can take it.
 *
 * A port because sharing is a platform mechanism and `AGENTS.md` keeps those behind an interface
 * this project owns - and because there is no such thing on the reference side to copy: JupyterLab
 * exports by `window.open(url, download=true)`, and where the file goes after that is the
 * browser's business. On Android there is no download folder to hand it to, so the artifact lands
 * in the workspace (see `NotebookExport`) and this is how it leaves.
 *
 * ```
 * sharer.share("reports/Analysis.html", "text/html")
 * ```
 */
interface DocumentSharer {
  /**
   * Offers the file at [path] - relative to the workspace root, as every path here is - to the
   * apps that can receive [mediaType].
   *
   * [mediaType] is the server's own rather than one derived from the extension, because the two
   * disagree: a `markdown` export of a notebook with an image is `application/zip` named `.zip`,
   * measured on the tablet rather than assumed.
   */
  fun share(path: String, mediaType: String)
}
