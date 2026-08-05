package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBridgeTest {
  @Test
  fun parse_readsEveryMessageThePageSends() {
    assertEquals(EditorMessage.Ready, parse("""{"type":"ready"}"""))
    assertEquals(EditorMessage.HeightChanged(240), parse("""{"type":"height","px":240}"""))
    assertEquals(EditorMessage.SourceEdited("x = 1"), parse("""{"type":"edit","source":"x = 1"}"""))
    assertEquals(EditorMessage.RunRequested, parse("""{"type":"run"}"""))
  }

  @Test
  fun parse_readsACompletionRequestWithItsCursor() {
    val message = parse("""{"type":"complete","id":"c7","source":"impo","line":0,"col":4}""")

    assertEquals(EditorMessage.CompletionRequested("c7", "impo", line = 0, column = 4), message)
  }

  /**
   * The page is the untrusted side of this bridge: a WebView can be fed anything, and one bad
   * message must not take the editor down.
   */
  @Test
  fun parse_ignoresAnythingItDoesNotUnderstand() {
    assertNull(parse("not json at all"))
    assertNull(parse("""{"type":"from_a_later_version"}"""))
    assertNull(parse("""{"nope":1}"""))
    assertNull(parse("""{"type":"height"}"""))
    assertNull(parse("""{"type":"height","px":"tall"}"""))
  }

  /**
   * The load-bearing part of the whole bridge. Source is user text going into a JavaScript
   * expression, so it is JSON-encoded rather than concatenated: a quote, a newline or a
   * backslash in someone's code would otherwise produce a syntax error at best.
   */
  @Test
  fun setSource_encodesQuotesNewlinesAndBackslashes() {
    val source = "print(\"hi\")\npath = 'C:\\tmp'"

    val call = EditorBridge.setSource(source)

    assertTrue("newlines must not be literal, got: $call", !call.contains("\n"))
    assertTrue(call.startsWith("window.JupydroidEditor.setSource("))
    assertEquals(source, sourceArgumentOf(call))
  }

  /**
   * The same encoding is what stops cell text being able to run as script.
   *
   * Counting occurrences of the call would be the wrong assertion: the hostile text appears
   * inside the escaped string, so a correct encoding still contains it twice. What matters is
   * that everything between the outer parentheses is one JSON string equal to the input - if
   * the text had escaped, that would not parse.
   */
  @Test
  fun setSource_cannotBeBrokenOutOfByCraftedCellText() {
    val hostile = "\");window.JupydroidEditor.setSource(\"owned"

    val call = EditorBridge.setSource(hostile)

    assertEquals(hostile, sourceArgumentOf(call))
    assertTrue("nothing may follow the call, got: $call", call.endsWith(")"))
  }

  @Test
  fun setTheme_passesTheTokensThePageStylesItselfWith() {
    val call = EditorBridge.setTheme(mapOf("background" to "#101418", "foreground" to "#e6e1e5"))

    assertTrue(call.startsWith("window.JupydroidEditor.setTheme("))
    assertTrue(call.contains("#101418"))
    assertTrue(call.contains("foreground"))
  }

  @Test
  fun completionsReady_answersTheRequestThatAskedForIt() {
    val call = EditorBridge.completionsReady("c7", listOf("import", "in"))

    assertTrue(call.startsWith("window.JupydroidEditor.completionsReady("))
    assertTrue("the id must come back so a late answer is not applied, got: $call", call.contains("\"c7\""))
    assertTrue(call.contains("import"))
  }

  private fun parse(json: String): EditorMessage? = EditorBridge.parse(json)

  /** Reads the first argument back out of the generated call, decoding it as JSON. */
  private fun sourceArgumentOf(call: String): String {
    val json = call.substringAfter('(').substringBeforeLast(')')
    return kotlinx.serialization.json.Json.parseToJsonElement(json)
      .let { (it as kotlinx.serialization.json.JsonPrimitive).content }
  }
}
