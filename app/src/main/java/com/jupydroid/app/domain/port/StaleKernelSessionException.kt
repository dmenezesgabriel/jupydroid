package com.jupydroid.app.domain.port

/**
 * The session belongs to a server that is no longer running, so nothing can be asked of it.
 *
 * Its own type because the caller can resolve this one: it connects again and runs the cell,
 * which is what P1c's backstop makes an ordinary event rather than a fault - the app stops the
 * server when it is left alone, and the next run brings one back.
 *
 * An `IllegalStateException`, like [FileNameTakenException], so a caller that only knows the
 * general failure - the completion popup, which has nothing to say when the kernel cannot answer -
 * keeps working without knowing this type exists.
 *
 * ```
 * try { gateway.execute(session, cell.id, cell.source) }
 * catch (stale: StaleKernelSessionException) { gateway.execute(freshSession(), cell.id, cell.source) }
 * ```
 */
class StaleKernelSessionException(sessionId: String) :
  IllegalStateException("session '$sessionId' belongs to a server that has stopped; connect again")
