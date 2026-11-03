package com.jupydroid.app.domain.model

/**
 * One licence document the APK ships - the app's own licence, its credits, the third-party
 * listing or the source offer - as a title a list shows and the text a reader reads.
 *
 * [isMarkdown] says how the text is drawn: a Markdown file is rendered, anything else is shown as
 * written, since `LICENSE` and `NOTICE` are prose whose line breaks carry meaning.
 *
 * ```
 * LicenceDocument(title = "Licence", text = "Apache License ...")
 * ```
 */
data class LicenceDocument(val title: String, val text: String, val isMarkdown: Boolean = false)
