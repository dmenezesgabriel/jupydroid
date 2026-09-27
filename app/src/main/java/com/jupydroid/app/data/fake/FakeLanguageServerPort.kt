package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageCapabilities
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageLocation
import com.jupydroid.app.domain.port.LanguageMarkup
import com.jupydroid.app.domain.port.LanguageMarkupKind
import com.jupydroid.app.domain.port.LanguageParameter
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageSignature
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import com.jupydroid.app.domain.port.PublishDiagnostics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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
  var capabilitiesAnswer = LanguageCapabilities(
    completionProvider = true,
    positionEncoding = "utf-16",
    hoverProvider = true,
    definitionProvider = true,
    documentRangeFormattingProvider = true,
  )

  /** What `hover` answers, replaceable with null for a "nothing here" test. */
  var hoverAnswer: LanguageHover? = LanguageHover(
    LanguageMarkup(LanguageMarkupKind.MARKDOWN, "```python\nprint(value, ...)\n```\n\nWrite to a stream."),
  )

  /** What `signatureHelp` answers - the probe-measured `join` reply by default. */
  var signatureHelpAnswer: LanguageSignatureHelp? = LanguageSignatureHelp(
    signatures = listOf(
      LanguageSignature(
        label = "join(path: LiteralString, /, *paths: LiteralString) -> LiteralString",
        parameters = listOf(LanguageParameter("path"), LanguageParameter("paths")),
      ),
    ),
  )

  /**
   * What `definition` answers, or null to answer the probe's shape at the asked uri -
   * `compute_total` defined on line 4, the reply `evidence/p5e-0-lsp-surface/` captured off
   * real `pylsp`. Pointing inside the asked document is what makes the fake's jumps land
   * somewhere a UI test can see.
   */
  var definitionAnswer: List<LanguageLocation>? = null

  /**
   * What `rangeFormatting` answers - the `TextEdit[]` a test scripts, `[]` ("already
   * formatted") by default. [formattingAsks] records the range and options each call
   * arrived with, so a test can assert the settings reached the wire.
   */
  var formattingAnswer: List<LanguageEdit> = emptyList()

  /** Every `rangeFormatting` call, in arrival order - the (uri, range, options) triple. */
  val formattingAsks = mutableListOf<Triple<String, LanguageRange, LanguageFormatOptions>>()

  /** `didOpen`/`didChange` in arrival order, so a test sees the sync the server would have. */
  val opened = mutableListOf<String>()
  val changes = mutableListOf<String>()
  val closed = mutableListOf<String>()
  var stops = 0
    private set

  private val pushed = MutableSharedFlow<PublishDiagnostics>(extraBufferCapacity = 64)
  override val diagnostics: SharedFlow<PublishDiagnostics> = pushed

  /**
   * How many collectors are listening - the stream drops an emission nobody heard, so a test
   * driving [emitDiagnostics] waits for this rather than racing the subscribe.
   */
  val diagnosticSubscriberCount: StateFlow<Int> = pushed.subscriptionCount

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

  override suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover? = hoverAnswer

  override suspend fun signatureHelp(space: Space, uri: String, position: LanguagePosition): LanguageSignatureHelp? =
    signatureHelpAnswer

  override suspend fun definition(space: Space, uri: String, position: LanguagePosition): List<LanguageLocation> =
    definitionAnswer ?: listOf(LanguageLocation(uri, PROBE_DEFINITION_RANGE))

  override suspend fun rangeFormatting(
    space: Space,
    uri: String,
    range: LanguageRange,
    options: LanguageFormatOptions,
  ): List<LanguageEdit> {
    formattingAsks += Triple(uri, range, options)
    return formattingAnswer
  }

  override suspend fun stop(space: Space) {
    stops++
  }

  private companion object {
    /** The `compute_total` name span real `pylsp` answered in `evidence/p5e-0-lsp-surface/`. */
    val PROBE_DEFINITION_RANGE = LanguageRange(LanguagePosition(4, 4), LanguagePosition(4, 17))
  }
}
