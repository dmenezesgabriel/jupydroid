package com.jupydroid.app.data.space

/**
 * A [SpaceLifecycleLog] that keeps what it was told, so a test can assert on it.
 *
 * Named rather than an inline object because two test classes need it, and because what the
 * runtime reports is now a thing under test in its own right: three sightings of the execution
 * stall went undiagnosed for want of these lines.
 *
 * ```
 * val log = RecordingSpaceLifecycleLog()
 * assertTrue(KERNEL_EXECUTE_REQUEST in log.stages)
 * assertEquals("cell-1", log.fieldsOf(KERNEL_EXECUTE_REQUEST)["cellId"])
 * ```
 */
class RecordingSpaceLifecycleLog : SpaceLifecycleLog {
  private val entries = mutableListOf<Pair<String, Map<String, String>>>()

  val failures = mutableListOf<String>()

  /** Every stage reported, in order, so a test can say what happened *and* in what sequence. */
  val stages: List<String> get() = entries.map { it.first }

  override fun reached(stage: String, fields: Map<String, String>) {
    entries += stage to fields
  }

  override fun failed(stage: String, cause: Throwable) {
    failures += stage
  }

  /** The fields of the first report of [stage], or empty when it was never reached. */
  fun fieldsOf(stage: String): Map<String, String> =
    entries.firstOrNull { it.first == stage }?.second.orEmpty()

  fun countOf(stage: String): Int = entries.count { it.first == stage }
}
