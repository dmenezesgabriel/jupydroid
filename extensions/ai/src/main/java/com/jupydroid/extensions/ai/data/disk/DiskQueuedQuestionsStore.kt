package com.jupydroid.extensions.ai.data.disk

import androidx.core.util.AtomicFile
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStoreException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * One Space's queued questions, in [file] in the app's own storage, as a JSON object from each chat's
 * path to its questions, oldest first. It is kept out of the workspace, which is the kernel's HOME and
 * the server's root, as VS Code keeps a session's pending requests in its workspace storage rather
 * than the workspace (vscode@3f3380c `src/vs/workbench/contrib/chat/common/model/chatSessionStore.ts`).
 *
 * Saved through [AtomicFile], written beside the file and renamed over it, so a process killed
 * mid-write leaves the questions as they were (ADR-0089).
 *
 * ```
 * DiskQueuedQuestionsStore(File(filesDir, "chat-queues/base.json")).read(ChatFile("untitled.chat"))
 * ```
 */
class DiskQueuedQuestionsStore(
  private val file: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QueuedQuestionsStore {
  /**
   * Taken before the switch to [ioDispatcher], so writes queue in the order they were launched from
   * the panel's thread, and a question taken off just after it was sent is not written back. The
   * mutex is fair, so it hands itself on in that order.
   */
  private val writeMutex = Mutex()

  override suspend fun read(chat: ChatFile): List<String> = withContext(ioDispatcher) {
    readAll()[chat.path].orEmpty()
  }

  override suspend fun write(chat: ChatFile, pending: List<String>) =
    rewrite("keeping ${chat.path}'s queued questions") { all -> all.hold(chat, pending) }

  /** In one replace of the file, so a kill leaves the questions under one path or the other, never both. */
  override suspend fun move(chat: ChatFile, to: ChatFile) =
    rewrite("moving ${chat.path}'s queued questions to ${to.path}") { all ->
      all.hold(to, all.remove(chat.path).orEmpty())
    }

  /** The file as [change] leaves every chat's questions; [action] names what failed if it cannot be kept. */
  private suspend fun rewrite(action: String, change: (MutableMap<String, List<String>>) -> Unit) =
    writeMutex.withLock {
      withContext(ioDispatcher) {
        // A file that cannot be read is written over, as `readOrNone` promises.
        val all = try {
          readAll().toMutableMap()
        } catch (_: QueuedQuestionsStoreException) {
          mutableMapOf()
        }
        change(all)
        try {
          replaceWith(Json.encodeToString(all).toByteArray())
        } catch (failure: IOException) {
          throw QueuedQuestionsStoreException("$action in ${file.path}", "$failure", failure)
        }
      }
    }

  private fun MutableMap<String, List<String>>.hold(chat: ChatFile, pending: List<String>) {
    if (pending.isEmpty()) remove(chat.path) else this[chat.path] = pending
  }

  /** [bytes] as the whole file, or the file as it was if the write fails. */
  private fun replaceWith(bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
      stream.write(bytes)
      atomic.finishWrite(stream)
    } catch (failure: IOException) {
      atomic.failWrite(stream)
      throw failure
    }
  }

  /** Every chat's questions, none where nothing was kept yet. */
  private fun readAll(): Map<String, List<String>> {
    val text = try {
      AtomicFile(file).readFully().decodeToString()
    } catch (_: FileNotFoundException) {
      return emptyMap()
    } catch (failure: IOException) {
      throw QueuedQuestionsStoreException("reading the queued questions in ${file.path}", "$failure", failure)
    }
    return try {
      Json.decodeFromString<Map<String, List<String>>>(text)
    } catch (failure: SerializationException) {
      throw QueuedQuestionsStoreException(
        "reading the queued questions in ${file.path}",
        "expected a JSON object of chat path to a list of questions: ${failure.message}",
        failure,
      )
    }
  }
}
