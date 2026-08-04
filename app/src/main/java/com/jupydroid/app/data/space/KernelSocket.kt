package com.jupydroid.app.data.space

/**
 * What happens on a kernel socket, in the only three kinds that matter to a caller.
 *
 * There is no separate "closed" and "failed": both end the socket, and what a stuck cell
 * needs is the reason, not the category. [Ended.failed] keeps the distinction for callers
 * that report differently, without letting anyone emit a bare, undebuggable "closed".
 */
sealed interface KernelSocketEvent {
  data object Opened : KernelSocketEvent

  data class Received(val frame: String) : KernelSocketEvent

  /** Terminal. [reason] always says why. */
  data class Ended(val reason: String, val failed: Boolean) : KernelSocketEvent
}

/**
 * A live WebSocket to one kernel, for as long as the caller wants it.
 *
 * Owned by this project rather than exposing OkHttp, so the gateway above it can be tested
 * against a fake and the transport can change without touching it.
 */
interface KernelSocket {
  /** False when the frame could not be queued - the socket is already ending. */
  fun send(frame: String): Boolean

  fun close()
}

/**
 * Opens sockets to a kernel's `channels` endpoint.
 *
 * ```
 * val socket = opener.open(kernelId, sessionId) { event -> … }
 * ```
 */
fun interface KernelSocketOpener {
  fun open(kernelId: String, sessionId: String, onEvent: (KernelSocketEvent) -> Unit): KernelSocket
}
