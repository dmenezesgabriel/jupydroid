package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionQueueTest {
  private val idle = ChatTranscript()
  private val writing = ChatTranscript(agentWriting = true)

  @Test
  fun aQuestionSentWhileNothingIsWritingIsDueAtOnce() {
    assertEquals("2+2?", QuestionQueue().sent("2+2?").due(idle))
  }

  @Test
  fun aQuestionSentWhileTheAgentWritesWaits() {
    val queue = QuestionQueue().sent("2+2?")

    assertNull(queue.due(writing))
    assertEquals(listOf("2+2?"), queue.pending)
  }

  @Test
  fun theOldestIsDueOnceTheReplyEnds() {
    val queue = QuestionQueue().sent("first").sent("second")

    assertEquals("first", queue.due(idle))
  }

  @Test
  fun askingTheDueQuestionTakesItOffThePending() {
    val queue = QuestionQueue().sent("first").sent("second").asked()

    assertEquals(listOf("second"), queue.pending)
  }

  /**
   * The persona says it writes only once it holds its session's lock, and a prompt that arrives before
   * then cancels the turn in flight (`prompt_and_reply` in
   * `usr/lib/python3.12/site-packages/jupyter_ai_acp_client/default_acp_client.py`, in the shipped
   * rootfs): the first question's echo comes while nothing writes yet.
   */
  @Test
  fun theNextWaitsUntilTheAgentHasStartedOnTheOneAsked() {
    val asked = QuestionQueue().sent("first").sent("second").asked()

    assertNull(asked.seen(idle).due(idle))
  }

  @Test
  fun theNextIsDueOnceTheReplyToTheOneAskedHasEnded() {
    val asked = QuestionQueue().sent("first").sent("second").asked()

    assertEquals("second", asked.seen(writing).seen(idle).due(idle))
  }

  @Test
  fun removingAPendingQuestionTakesOnlyIt() {
    val queue = QuestionQueue().sent("a").sent("b").sent("c")

    assertEquals(listOf("a", "c"), queue.removed("b").pending)
  }

  /** A tap on a row's × can land in the frame after its question was asked, before the row is gone. */
  @Test
  fun removingAQuestionAlreadyAskedLeavesTheQueue() {
    val asked = QuestionQueue().sent("a").sent("b").asked()

    assertEquals(asked, asked.removed("a"))
  }

  @Test
  fun editingAPendingQuestionReplacesItInPlace() {
    val queue = QuestionQueue().sent("a").sent("b").sent("c").editing("b")

    assertEquals(listOf("a", "B", "c"), queue.edited("b", "  B ").pending)
  }

  @Test
  fun aQuestionBeingEditedIsNeverDue() {
    assertNull(QuestionQueue().sent("a").editing("a").due(idle))
  }

  @Test
  fun savingReleasesTheEditedQuestion() {
    val saved = QuestionQueue().sent("a").editing("a").edited("a", "A")

    assertEquals("A", saved.due(idle))
  }

  /** A pencil tapped in the frame the oldest was asked opens on a question no longer here. */
  @Test
  fun anEditOfAQuestionAlreadyAskedChangesNothingButTheHold() {
    val asked = QuestionQueue().sent("a").sent("b").asked()

    assertEquals(asked, asked.editing("a").edited("a", "A"))
  }

  @Test
  fun onlyTheDueQuestionBeingEditedHoldsTheQueue() {
    val queue = QuestionQueue().sent("first").sent("second").editing("second")

    assertEquals("first", queue.due(idle))
  }
}
