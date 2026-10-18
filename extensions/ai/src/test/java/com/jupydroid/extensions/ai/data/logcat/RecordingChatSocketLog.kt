package com.jupydroid.extensions.ai.data.logcat

/**
 * A [ChatSocketLog] that keeps what it was told, so a test can assert on what a socket's end reported.
 *
 * ```
 * val log = RecordingChatSocketLog()
 * assertEquals("EOFException", log.fieldsOf("chat.socket.failed")["error"])
 * ```
 */
class RecordingChatSocketLog : ChatSocketLog {
  private val entries = mutableListOf<Pair<String, Map<String, String>>>()

  /** Every stage reported, in order. */
  val stages: List<String> get() = synchronized(entries) { entries.map { it.first } }

  override fun reached(stage: String, fields: Map<String, String>) {
    synchronized(entries) { entries += stage to fields }
  }

  /** The fields of the first report of [stage], or empty when it was never reached. */
  fun fieldsOf(stage: String): Map<String, String> =
    synchronized(entries) { entries.firstOrNull { it.first == stage }?.second.orEmpty() }
}
