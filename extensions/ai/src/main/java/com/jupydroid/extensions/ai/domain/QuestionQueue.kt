package com.jupydroid.extensions.ai.domain

/**
 * The questions sent while the agent answers, waiting their turn, oldest first.
 *
 * A question asked while a reply arrives would cut it short: jupyter-ai's ACP client cancels the turn
 * in flight before it takes the next prompt (`prompt_and_reply` in
 * `usr/lib/python3.12/site-packages/jupyter_ai_acp_client/default_acp_client.py`, in the shipped
 * rootfs). So every question goes through here, and one is [due] only when no agent writes and the
 * last one asked has had its reply, one at a time, as VS Code sends its pending requests
 * (vscode@3f3380c `src/vs/workbench/contrib/chat/common/chatService/chatServiceImpl.ts`,
 * `processNextPendingRequest`).
 *
 * ```
 * val queue = QuestionQueue().sent("What is 2+2?")
 * queue.due(transcript)?.let { chat.ask(it, persona); queue = queue.asked() }
 * ```
 *
 * @param awaitingReply a question was asked and its agent has not yet said it writes. The persona says
 *   so only once it holds its session's lock, so the asked question's echo arrives before that, while
 *   nothing writes; without this, that echo would let the next question cut in.
 * @param held the question being edited. It is never asked while edited, so what is asked is what the
 *   user saved, and the questions behind it wait with it, so their order holds.
 */
data class QuestionQueue(
  val pending: List<String> = emptyList(),
  val awaitingReply: Boolean = false,
  val held: String? = null,
) {
  /** [question] beneath those already waiting. */
  fun sent(question: String): QuestionQueue = copy(pending = pending + question)

  /** The queue once [transcript] has arrived: an agent writing has started on the one asked. */
  fun seen(transcript: ChatTranscript): QuestionQueue =
    if (transcript.agentWriting) copy(awaitingReply = false) else this

  /** The question to ask now, or null while a reply arrives or is still to start. */
  fun due(transcript: ChatTranscript): String? =
    pending.firstOrNull()?.takeUnless { transcript.agentWriting || awaitingReply || it == held }

  /** The queue once its [due] question has been asked. */
  fun asked(): QuestionQueue = copy(pending = pending.drop(1), awaitingReply = true)

  /**
   * The queue without [question], so it is never asked.
   *
   * By its text, not its place: the oldest is asked between a frame and the next, so a row's place can
   * shift under a tap. A question already asked is no longer here, and nothing changes. Of two equal
   * questions the first goes, which on screen is the same.
   */
  fun removed(question: String): QuestionQueue = copy(pending = pending - question)

  /** The queue with [question] being edited, and so held until it is [edited]. */
  fun editing(question: String): QuestionQueue = copy(held = question)

  /**
   * The queue with [question] saved as [into], trimmed, in its place, and no longer held, as
   * jupyter-chat-components@992c6fb `src/components/message-queue.tsx` saves an edit.
   *
   * By its text, as [removed] goes: a question already asked is no longer here, and only the hold goes.
   */
  fun edited(question: String, into: String): QuestionQueue {
    val at = pending.indexOf(question)
    if (at < 0) return copy(held = null)
    return copy(pending = pending.toMutableList().apply { set(at, into.trim()) }, held = null)
  }
}
