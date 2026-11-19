package com.jupydroid.app.data.disk

/**
 * Each shape the server's token takes where the app writes it: the `Authorization: token` header
 * (`app/src/main/java/com/jupydroid/app/data/space/KernelLifecycle.kt`), the MCP `Bearer` header and the
 * `--IdentityProvider.token=` start argument (`JupyterServerLauncher.kt`), and a `?token=` URL query.
 *
 * The `token ` header form asks for 16 or more characters because the token is random hex, and the same two
 * words open an ordinary message ("token count 3 exceeds limit"). Plain classes only, no lookbehind: ART's
 * regex is ICU, not the JVM's.
 */
private val SECRET_SHAPES: List<Regex> = listOf(
  Regex("(?i)(bearer\\s+)[^\\s'\"]+"),
  Regex("(?i)(token\\s+)[0-9A-Za-z]{16,}"),
  Regex("(?i)(token=)[^\\s&'\"]+"),
)

private const val REDACTED = "[redacted]"

/**
 * [text] with every token in a shape [SECRET_SHAPES] names replaced by `[redacted]`, its label kept so the
 * line still says what was sent.
 *
 * ```
 * redactSecrets("Authorization: Bearer abc123") // "Authorization: Bearer [redacted]"
 * ```
 */
internal fun redactSecrets(text: String): String =
  SECRET_SHAPES.fold(text) { redacted, shape -> shape.replace(redacted, "$1$REDACTED") }
