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
 * queue.due(transcript)?.let { chat.ask(it); queue = queue.asked() }
 * ```
 *
 * @param awaitingReply a question was asked and its agent has not yet said it writes. The persona says
 *   so only once it holds its session's lock, so the asked question's echo arrives before that, while
 *   nothing writes; without this, that echo would let the next question cut in.
 */
data class QuestionQueue(val pending: List<String> = emptyList(), val awaitingReply: Boolean = false) {
  /** [question] beneath those already waiting. */
  fun sent(question: String): QuestionQueue = copy(pending = pending + question)

  /** The queue once [transcript] has arrived: an agent writing has started on the one asked. */
  fun seen(transcript: ChatTranscript): QuestionQueue =
    if (transcript.agentWriting) copy(awaitingReply = false) else this

  /** The question to ask now, or null while a reply arrives or is still to start. */
  fun due(transcript: ChatTranscript): String? =
    pending.firstOrNull()?.takeUnless { transcript.agentWriting || awaitingReply }

  /** The queue once its [due] question has been asked. */
  fun asked(): QuestionQueue = copy(pending = pending.drop(1), awaitingReply = true)
}
