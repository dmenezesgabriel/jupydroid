package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The line a stall gets read from, so it has to be machine-readable and it has to escape.
 *
 * `AGENTS.md`: structured JSON when logging for observability. Built with the serializer the app
 * already depends on rather than by hand - a reason string carrying a quote or a newline is the
 * ordinary case here, not the exotic one (`"KernelChannelException: closed: 1006 "gone""`), and
 * hand-rolled JSON is where that becomes an unparseable evening.
 *
 * Rendering is separated from [LogcatSpaceLifecycleLog] because `android.util.Log` throws when it
 * is not mocked, so the adapter itself cannot be driven from a JVM test.
 */
class SpaceLogLineTest {
  @Test
  fun aStageWithNoFieldsIsStillAnObject() {
    assertEquals("""{"stage":"starting jupyter_server"}""", spaceLogLine("starting jupyter_server"))
  }

  @Test
  fun fieldsAreRenderedBesideTheStage() {
    val line = spaceLogLine("kernel.execute.request", mapOf("cellId" to "c1", "msgId" to "m-7"))

    assertEquals("""{"stage":"kernel.execute.request","cellId":"c1","msgId":"m-7"}""", line)
  }

  @Test
  fun quotesAndNewlinesInAValueAreEscaped() {
    val line = spaceLogLine("kernel.socket.ended", mapOf("reason" to "closed: 1006 \"gone\"\nbye"))

    assertEquals("""{"stage":"kernel.socket.ended","reason":"closed: 1006 \"gone\"\nbye"}""", line)
  }

  /** Insertion order, so a line reads the way it was written rather than alphabetically. */
  @Test
  fun fieldOrderIsTheOrderGiven() {
    val line = spaceLogLine("s", linkedMapOf("z" to "1", "a" to "2"))

    assertEquals("""{"stage":"s","z":"1","a":"2"}""", line)
  }
}
