package com.jupydroid.app.domain.port

/**
 * A JSON-RPC `error` reply for [method], typed so a feature can surface the server's own
 * refusal - [serverMessage] is what a refused rename toasts, VS Code's `rejectReason` path.
 * Still an [IllegalStateException], so every ask whose failure means "silence" keeps working,
 * and the exception's own message keeps the code beside it for the log.
 *
 * Its own type because the caller branches on it, exactly as JupyterLab branches on an error
 * response, and its own file because [FileNameTakenException] set that convention here.
 *
 * ```
 * catch (refused: LanguageServerException) { toast(refused.serverMessage ?: "Rename failed") }
 * ```
 */
class LanguageServerException(val method: String, val code: Int?, val serverMessage: String?) :
  IllegalStateException("$method returned error $code: $serverMessage")
