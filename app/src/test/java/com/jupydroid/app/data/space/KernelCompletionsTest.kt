package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.CompletionMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KernelCompletionsTest {
  @Test
  fun of_readsTheMatchesAndTheRangeTheyReplace() {
    val result = KernelCompletions.of(KernelFrames.completeReply("m1", listOf("print", "printf"), 6, 9), "m1")

    // A kernel's `matches` carry no label/kind distinction - each is its own display and insert.
    assertEquals(
      listOf(CompletionMatch("print", "print"), CompletionMatch("printf", "printf")),
      result?.matches,
    )
    assertEquals(6, result?.replacedStart)
    assertEquals(9, result?.replacedEnd)
  }

  /** A shared kernel's shell channel carries other clients' replies too. */
  @Test
  fun of_ignoresAReplyToSomebodyElsesRequest() {
    assertNull(KernelCompletions.of(KernelFrames.completeReply("other", listOf("print"), 0, 3), "m1"))
  }

  @Test
  fun of_ignoresFramesThatAreNotACompleteReply() {
    assertNull(KernelCompletions.of(KernelFrames.executeReply("m1"), "m1"))
    assertNull(KernelCompletions.of(KernelFrames.idle("m1"), "m1"))
  }

  /** A kernel that failed to complete has nothing to offer, which is not the same as an error. */
  @Test
  fun of_readsAnErrorReplyAsNoMatches() {
    val frame = """{"channel":"shell","parent_header":{"msg_id":"m1"},"header":{"msg_type":"complete_reply"},
       "content":{"status":"error","ename":"ValueError"}}"""

    val result = KernelCompletions.of(frame, "m1")

    assertEquals(emptyList<CompletionMatch>(), result?.matches)
  }

  /** One malformed frame must not take the channel down - every reader here is total. */
  @Test
  fun of_ignoresAFrameItCannotParse() {
    assertNull(KernelCompletions.of("not json at all", "m1"))
  }
}
