package com.jupydroid.app.data.space

/**
 * The stages the execute path reports, as constants so a log line and the code that writes it can
 * be found from each other.
 *
 * Named after the question each one answers when a cell has stopped and nobody knows why:
 *
 * ```
 * adb logcat -s JupydroidSpace | grep kernel. | jq -c 'select(.stage|startswith("kernel"))'
 * ```
 *
 * | stage | answers |
 * |---|---|
 * | [SESSION_OPENED] | which kernel this notebook is bound to |
 * | [SOCKET_OPENED] | the app asked for a connection |
 * | [SOCKET_CONNECTED] | the server accepted it - absent means the handshake never finished |
 * | [SOCKET_ENDED] | it went away, and what the server said |
 * | [FRAME_RECEIVED] | the kernel is talking, and whether anyone was listening |
 * | [FRAME_DROPPED] | it talked and the buffer refused it, which loses output silently |
 * | [EXECUTE_REQUEST] | the request left the app - `enqueued`, which is weaker than delivered |
 * | [EXECUTE_COMPLETE] | the cell finished, rather than being abandoned |
 *
 * A stall shows as `EXECUTE_REQUEST` with no `FRAME_RECEIVED` after it (nothing came back), or
 * with frames whose `subscribers` is `0` (it came back and was thrown away). Those are opposite
 * defects and the three sightings so far could not tell them apart - see `docs/PLAN.md`.
 *
 * **The ordering is itself worth reading.** On every device capture so far [EXECUTE_REQUEST]
 * precedes [SOCKET_CONNECTED]: the app hands the frame to OkHttp before the handshake finishes,
 * and OkHttp queues it. That is fine when the handshake then completes, and it is the obvious
 * candidate for a stall when it does not - a request enqueued against a socket that never
 * connects is waited on forever, with `enqueued: true` and no error anywhere.
 */
internal object KernelLog {
  const val SESSION_OPENED = "kernel.session.opened"
  const val SOCKET_OPENED = "kernel.socket.opened"
  const val SOCKET_CONNECTED = "kernel.socket.connected"
  const val SOCKET_ENDED = "kernel.socket.ended"
  const val FRAME_RECEIVED = "kernel.frame.received"
  const val FRAME_DROPPED = "kernel.frame.dropped"
  const val EXECUTE_REQUEST = "kernel.execute.request"
  const val EXECUTE_COMPLETE = "kernel.execute.complete"

  /**
   * The `kernel_info` handshake, which had no line of its own until the sixth sighting.
   *
   * Every stall before that had to be read from an *absence* - no `kernel_info_reply` among the
   * frames - which cannot tell "the greeting was never sent" from "it was sent and ignored". Those
   * are different defects with different fixes, and these three stages say which.
   */
  const val GREET_SENT = "kernel.greet.sent"
  const val GREET_ANSWERED = "kernel.greet.answered"
  const val GREET_TIMED_OUT = "kernel.greet.timedout"
}
