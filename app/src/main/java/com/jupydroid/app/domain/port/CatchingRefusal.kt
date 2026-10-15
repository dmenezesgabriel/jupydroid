package com.jupydroid.app.domain.port

import kotlinx.coroutines.CancellationException

/**
 * [block]'s answer, or what refused it - except a cancellation, which is rethrown so the coroutine
 * that was cancelled still ends. Plain `runCatching` holds that one too, and a cancelled save or
 * restore would then report itself as refused and carry on.
 *
 * For a port call whose refusal the caller turns into words rather than a crash.
 *
 * ```
 * catchingRefusal { workspace.restoreCheckpoint(path, id) }.exceptionOrNull()?.let { say(it.message) }
 * ```
 */
inline fun <T> catchingRefusal(block: () -> T): Result<T> {
  val attempt = runCatching(block)
  (attempt.exceptionOrNull() as? CancellationException)?.let { throw it }
  return attempt
}
