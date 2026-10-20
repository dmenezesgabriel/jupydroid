package com.jupydroid.extensions.ai.data.http

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * A workspace root as jupyter_server's Contents API serves it, holding the files at [paths]: `GET`
 * lists them, and `POST` makes the next untitled file under the name `increment_filename` gives it
 * (`jupyter_server/services/contents/manager.py` in the shipped rootfs), with the 201 it answers.
 */
class FakeContentsRoot(paths: List<String>) : Dispatcher() {
  private val held = paths.toMutableList()

  override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
    "GET" -> MockResponse.Builder().body(rootListing(held.map(::fileEntry))).build()
    "POST" -> MockResponse.Builder().code(HTTP_CREATED).body(fileEntry(nextUntitled())).build()
    else -> MockResponse.Builder().code(HTTP_NOT_ALLOWED).build()
  }

  private fun nextUntitled(): String {
    val free = generateSequence(0) { it + 1 }.map { n -> "untitled${if (n == 0) "" else n}.chat" }.first { it !in held }
    held += free
    return free
  }

  companion object {
    private const val HTTP_CREATED = 201
    private const val HTTP_NOT_ALLOWED = 405

    fun fileEntry(path: String) = """{"name": "$path", "path": "$path", "type": "file"}"""

    fun rootListing(entries: List<String>) =
      """{"name": "", "path": "", "type": "directory", "content": [${entries.joinToString()}]}"""
  }
}
