package com.jupydroid.extensions.ai.data.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * A workspace root as jupyter_server's Contents API serves it, holding the files at [paths]: `GET`
 * lists them, and `POST` makes the next untitled file under the name `increment_filename` gives it
 * (`jupyter_server/services/contents/manager.py` in the shipped rootfs), with the 201 it answers.
 * `PATCH` moves a file to the body's `path`, or answers 409 when a file is there already, as
 * `FileContentsManager.rename_file` does (`jupyter_server/services/contents/filemanager.py`).
 * `DELETE` removes a file with the 204 the handler answers (`jupyter_server/services/contents/handlers.py`),
 * or answers 404 when it is not there, as `delete_file` does.
 */
class FakeContentsRoot(paths: List<String>) : Dispatcher() {
  private val held = paths.toMutableList()

  override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
    "GET" -> MockResponse.Builder().body(rootListing(held.map(::fileEntry))).build()
    "POST" -> MockResponse.Builder().code(HTTP_CREATED).body(fileEntry(nextUntitled())).build()
    "PATCH" -> rename(pathOf(request), request)
    "DELETE" -> delete(pathOf(request))
    else -> MockResponse.Builder().code(HTTP_NOT_ALLOWED).build()
  }

  private fun rename(from: String, request: RecordedRequest): MockResponse {
    val to = Json.parseToJsonElement(request.body!!.utf8()).jsonObject.getValue("path").jsonPrimitive.content
    if (to in held) {
      return MockResponse.Builder().code(HTTP_CONFLICT).body("""{"message": "File already exists: $to"}""").build()
    }
    held[held.indexOf(from)] = to
    return MockResponse.Builder().body(fileEntry(to)).build()
  }

  private fun delete(path: String): MockResponse {
    if (!held.remove(path)) {
      val missing = """{"message": "file or directory does not exist: '$path'"}"""
      return MockResponse.Builder().code(HTTP_NOT_FOUND).body(missing).build()
    }
    return MockResponse.Builder().code(HTTP_NO_CONTENT).build()
  }

  /** The file a request names after `api/contents/`. */
  private fun pathOf(request: RecordedRequest): String = request.url.pathSegments.drop(2).joinToString("/")

  private fun nextUntitled(): String {
    val free = generateSequence(0) { it + 1 }.map { n -> "untitled${if (n == 0) "" else n}.chat" }.first { it !in held }
    held += free
    return free
  }

  companion object {
    private const val HTTP_CREATED = 201
    private const val HTTP_NO_CONTENT = 204
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_NOT_ALLOWED = 405
    private const val HTTP_CONFLICT = 409

    /** Encoded rather than spliced, so a name with a quote in it is still a model the adapter reads. */
    fun fileEntry(path: String) = buildJsonObject {
      put("name", path)
      put("path", path)
      put("type", "file")
    }.toString()

    fun rootListing(entries: List<String>) =
      """{"name": "", "path": "", "type": "directory", "content": [${entries.joinToString()}]}"""
  }
}
