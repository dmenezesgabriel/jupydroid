package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageCapabilities
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.PublishDiagnostics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Stub [LanguageServerPort] - `AppContainer`'s default, so a device with no runnable Space
 * still composes every UI the port feeds, and tests read the calls it recorded rather than
 * spawn a process to see them.
 */
class FakeLanguageServerPort : LanguageServerPort {
  /** Completions every call answers, replaceable by a test that wants its own list. */
  var completions: List<LanguageCompletion> = listOf(
    LanguageCompletion(label = "print", insertText = "print"),
    LanguageCompletion(label = "property", insertText = "property"),
  )

  /** What the fake's `initialize` reports - one completion provider, utf-16 offsets. */
  var capabilitiesAnswer = LanguageCapabilities(completionProvider = true, positionEncoding = "utf-16")

  /** `didOpen`/`didChange` in arrival order, so a test sees the sync the server would have. */
  val opened = mutableListOf<String>()
  val changes = mutableListOf<String>()
  val closed = mutableListOf<String>()
  var stops = 0
    private set

  private val pushed = MutableSharedFlow<PublishDiagnostics>(extraBufferCapacity = 64)
  override val diagnostics: SharedFlow<PublishDiagnostics> = pushed

  /** What a server's `publishDiagnostics` would push - emits onto [diagnostics] for tests. */
  suspend fun emitDiagnostics(publishDiagnostics: PublishDiagnostics) {
    pushed.emit(publishDiagnostics)
  }

  override suspend fun capabilities(space: Space): LanguageCapabilities = capabilitiesAnswer

  override suspend fun openDocument(space: Space, uri: String, languageId: String, text: String) {
    opened += uri
  }

  override suspend fun changeDocument(space: Space, uri: String, text: String) {
    changes += uri
  }

  override suspend fun closeDocument(space: Space, uri: String) {
    closed += uri
  }

  override suspend fun completion(space: Space, uri: String, position: LanguagePosition): List<LanguageCompletion> =
    completions

  override suspend fun stop(space: Space) {
    stops++
  }
}
