package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * `textDocument/rangeFormatting` over [StdioLanguageServer], its own
 * file the way [StdioLanguageServerDefinitionTest] is: the scripted doubles and the
 * `languageServer` scaffold are the same ones [StdioLanguageServerTest] uses, and the 500-line
 * file gate is what keeps them apart.
 *
 * The shipped `pylsp` answers this through autopep8 (`@hookimpl(tryfirst=True)` over yapf),
 * which rounds the asked range to full lines, runs `fix_code` with `line_range`, and answers
 * **one whole-document `TextEdit`** - read off the rootfs's own
 * `usr/lib/python3.12/site-packages/pylsp/plugins/autopep8_format.py`.
 */
class StdioLanguageServerFormattingTest {
  private val servers = mutableListOf<ScriptedLanguageServer>()

  private fun languageServer(
    log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
    spawn: () -> ScriptedLanguageServer = { ScriptedLanguageServer().also(servers::add) },
  ) = StdioLanguageServer(
    installSpace = { InstalledSpace(File("/dev/null"), 7, false) },
    spawn = { spawn() },
    requestTimeout = 30.seconds,
    log = log,
  )

  /**
   * `DocumentRangeFormattingParams`: the document, the range, and the `options` object LSP
   * requires - `tabSize`/`insertSpaces` from the editor's own indentation setting, the way
   * VS Code sends `model.getOptions()`. The range itself is a cell's whole block, computed
   * by `NotebookVirtualDocument.rangeOf` - the adapter only carries it.
   */
  @Test
  fun rangeFormatting_sendsTheDocumentsRangeAndOptions() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    languageServer.rangeFormatting(BASE_SPACE, URI, RANGE, OPTIONS)

    val request = server.sent("textDocument/rangeFormatting").single()
    assertEquals(URI, request.textDocument["uri"]!!.jsonPrimitive.content)
    val range = request.getValue("params").jsonObject.getValue("range").jsonObject
    assertEquals(3, range.getValue("start").jsonObject.getValue("line").jsonPrimitive.intOrNull)
    assertEquals(0, range.getValue("start").jsonObject.getValue("character").jsonPrimitive.intOrNull)
    assertEquals(4, range.getValue("end").jsonObject.getValue("line").jsonPrimitive.intOrNull)
    assertEquals(7, range.getValue("end").jsonObject.getValue("character").jsonPrimitive.intOrNull)
    val options = request.getValue("params").jsonObject.getValue("options").jsonObject
    assertEquals(4, options.getValue("tabSize").jsonPrimitive.intOrNull)
    assertEquals(true, options.getValue("insertSpaces").jsonPrimitive.booleanOrNull)
  }

  /** A `TextEdit[]` answer parses to [LanguageEdit]s - one per entry, range and text verbatim. */
  @Test
  fun rangeFormatting_parsesTheEditsItsAnswerCarries() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.formattingResult = oneEditResult()
    val languageServer = languageServer { server }

    val edits = languageServer.rangeFormatting(BASE_SPACE, URI, RANGE, OPTIONS)

    val edit = edits.single()
    assertEquals(LanguagePosition(3, 0), edit.range.start)
    assertEquals(LanguagePosition(5, 0), edit.range.end)
    assertEquals("x = 1\n", edit.newText)
  }

  /** One `TextEdit` covering lines 3..5 with `x = 1\n` - autopep8's whole-document answer shape. */
  private fun oneEditResult() = buildJsonArray {
    add(
      buildJsonObject {
        putJsonObject("range") {
          putJsonObject("start") {
            put("line", 3)
            put("character", 0)
          }
          putJsonObject("end") {
            put("line", 5)
            put("character", 0)
          }
        }
        put("newText", "x = 1\n")
      },
    )
  }

  /** `result: null` and `result: []` are the same nothing - the cell was already formatted. */
  @Test
  fun rangeFormatting_answeredNullOrEmpty_isEmpty() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    server.formattingResult = JsonNull
    assertTrue(languageServer.rangeFormatting(BASE_SPACE, URI, RANGE, OPTIONS).isEmpty())
    server.formattingResult = buildJsonArray {}
    assertTrue(languageServer.rangeFormatting(BASE_SPACE, URI, RANGE, OPTIONS).isEmpty())
  }

  /**
   * `documentRangeFormattingProvider` absent is an ask never sent - the same
   * undeclared-capability gate `definitionProvider` already applies.
   */
  @Test
  fun rangeFormattingProvider_absent_sendsNoRequest() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.initializeResult = buildJsonObject {
      putJsonObject("capabilities") { put("hoverProvider", true) }
    }
    val languageServer = languageServer { server }

    assertTrue(languageServer.rangeFormatting(BASE_SPACE, URI, RANGE, OPTIONS).isEmpty())
    assertTrue(server.sent("textDocument/rangeFormatting").isEmpty())
  }

  /**
   * The ask and its answer both reach `JupydroidSpace`, the way definition's do - the
   * `lsp.ask`/`lsp.answer` pair is the device-side evidence a format went through the port.
   */
  @Test
  fun rangeFormatting_reachesTheLifecycleLog_askedAndAnswered() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val log = RecordingSpaceLifecycleLog()
    val languageServer = languageServer(log) { server }

    languageServer.rangeFormatting(BASE_SPACE, URI, RANGE, OPTIONS)

    val ask = log.fieldsOf("lsp.ask")
    assertEquals("rangeFormatting", ask["method"])
    assertEquals(URI, ask["uri"])
    val answer = log.fieldsOf("lsp.answer")
    assertEquals("rangeFormatting", answer["method"])
    assertEquals(listOf("lsp.ask", "lsp.answer"), log.stages.filter { it.startsWith("lsp.a") })
  }

  private val JsonObject.textDocument: JsonObject
    get() = getValue("params").jsonObject.getValue("textDocument").jsonObject

  private companion object {
    const val URI = "file:///home/jupydroid/test.py"
    val RANGE = LanguageRange(LanguagePosition(3, 0), LanguagePosition(4, 7))
    val OPTIONS = LanguageFormatOptions(tabSize = 4, insertSpaces = true)
  }
}
