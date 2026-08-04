package com.jupydroid.app.data.space

/**
 * A [KernelLifecycle] that hands out predictable kernel ids and records interrupts, so the
 * gateway's use of it can be asserted without a server.
 */
class FakeKernelLifecycle(
  private val kernelId: String = "kernel-1",
  private val failWith: Exception? = null,
) : KernelLifecycle {
  var startCount = 0
    private set
  val interrupted = mutableListOf<String>()

  override suspend fun start(): String {
    failWith?.let { throw it }
    startCount++
    return kernelId
  }

  override suspend fun interrupt(kernelId: String) {
    failWith?.let { throw it }
    interrupted += kernelId
  }
}
