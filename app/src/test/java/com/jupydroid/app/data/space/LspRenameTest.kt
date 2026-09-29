package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `textDocument/rename` reply - a `WorkspaceEdit` (LSP 3.17) - parsed by [LspRename].
 *
 * What the shipped `pylsp` answers is captured in `evidence/p5e-0-lsp-surface/logcat-probe.txt`:
 * `documentChanges` with one `TextDocumentEdit` per document, its `edits` carrying a single
 * whole-document `TextEdit` whose `newText` rewrites every occurrence at once. The legacy
 * `changes` map is the fallback the spec keeps for servers that do not send `documentChanges`.
 */
class LspRenameTest {
  @Test
  fun documentChanges_parseToEditsKeyedByUri() {
    // The probe's own shape: one TextDocumentEdit, one
    // whole-document TextEdit spanning (0,0) to past the last line.
    val edit = LspRename.workspaceEditFrom(
      buildJsonObject {
        putJsonArray("documentChanges") {
          addJsonObject { putTextDocumentEdit(URI, "def summed(values):\n    ...\n", endLine = 15) }
        }
      },
    )

    val edits = edit.changes.getValue(URI)
    assertEquals(1, edits.size)
    assertEquals(LanguageRange(LanguagePosition(0, 0), LanguagePosition(15, 0)), edits[0].range)
    assertTrue(edits[0].newText.startsWith("def summed"))
  }

  /** Two documents touched by one rename each get their own edit list. */
  @Test
  fun documentChanges_spanningTwoUris_keyBoth() {
    val edit = LspRename.workspaceEditFrom(
      buildJsonObject {
        putJsonArray("documentChanges") {
          addJsonObject { putTextDocumentEdit(URI, "a") }
          addJsonObject { putTextDocumentEdit(OTHER_URI, "b") }
        }
      },
    )

    assertEquals(setOf(URI, OTHER_URI), edit.changes.keys)
    assertEquals("a", edit.changes.getValue(URI).single().newText)
    assertEquals("b", edit.changes.getValue(OTHER_URI).single().newText)
  }

  /**
   * A `documentChanges` entry that is a resource operation - `{"kind":"rename","oldUri":...}` -
   * is not a `TextDocumentEdit`: no `textDocument`, so it drops out. A cell editor applies text
   * edits; it cannot rename files.
   */
  @Test
  fun documentChanges_resourceOperations_areDropped() {
    val edit = LspRename.workspaceEditFrom(
      buildJsonObject {
        putJsonArray("documentChanges") {
          addJsonObject {
            put("kind", "rename")
            put("oldUri", URI)
            put("newUri", OTHER_URI)
          }
          addJsonObject { putTextDocumentEdit(URI, "a") }
        }
      },
    )

    assertEquals(listOf(URI), edit.changes.keys.toList())
    assertEquals("a", edit.changes.getValue(URI).single().newText)
  }

  /** The `changes` fallback - `{uri: TextEdit[]}` - when `documentChanges` is absent. */
  @Test
  fun changesMap_isTheFallbackShape() {
    val edit = LspRename.workspaceEditFrom(
      buildJsonObject {
        putJsonObject("changes") {
          putJsonArray(URI) {
            addJsonObject { putTextEdit("a") }
            addJsonObject { putTextEdit("b") }
          }
        }
      },
    )

    assertEquals(listOf("a", "b"), edit.changes.getValue(URI).map { it.newText })
  }

  /**
   * LSP's own rule: "the client should prefer `documentChanges` over `changes`" - when both
   * keys ride on one reply, the document-changes form is the answer.
   */
  @Test
  fun documentChanges_present_changesMapIsIgnored() {
    val edit = LspRename.workspaceEditFrom(
      buildJsonObject {
        putJsonArray("documentChanges") {
          addJsonObject { putTextDocumentEdit(URI, "preferred") }
        }
        putJsonObject("changes") {
          putJsonArray(OTHER_URI) { addJsonObject { putTextEdit("ignored") } }
        }
      },
    )

    assertEquals(mapOf(URI to "preferred"), edit.changes.mapValues { it.value.single().newText })
  }

  /**
   * A `documentChanges` key that arrived malformed - a non-array - is not "present and empty",
   * it is absent for parsing purposes, and the `changes` fallback still applies.
   */
  @Test
  fun documentChanges_malformed_fallsBackToChanges() {
    val edit = LspRename.workspaceEditFrom(
      buildJsonObject {
        put("documentChanges", JsonNull)
        putJsonObject("changes") {
          putJsonArray(URI) { addJsonObject { putTextEdit("a") } }
        }
      },
    )

    assertEquals("a", edit.changes.getValue(URI).single().newText)
  }

  /** An edit entry missing its `range` or `newText` falls out rather than splice half a change. */
  @Test
  fun aMalformedEditEntry_isDropped() {
    val edit = LspRename.workspaceEditFrom(
      buildJsonObject {
        putJsonObject("changes") {
          putJsonArray(URI) {
            addJsonObject { put("newText", "unranged") }
            addJsonObject { putTextEdit("good") }
          }
        }
      },
    )

    assertEquals(listOf("good"), edit.changes.getValue(URI).map { it.newText })
  }

  /** `result: null`, a bare `{}`, and a non-object all parse to "nothing to change". */
  @Test
  fun nullOrMalformedAnswers_areEmpty() {
    assertTrue(LspRename.workspaceEditFrom(null).changes.isEmpty())
    assertTrue(LspRename.workspaceEditFrom(JsonNull).changes.isEmpty())
    assertTrue(LspRename.workspaceEditFrom(buildJsonObject {}).changes.isEmpty())
    assertTrue(LspRename.workspaceEditFrom(buildJsonArray {}).changes.isEmpty())
    assertTrue(
      LspRename.workspaceEditFrom(
        buildJsonObject { putJsonArray("documentChanges") {} },
      ).changes.isEmpty(),
    )
  }

  private companion object {
    const val URI = "file:///jupyterdroid/notebooks/nb.py"
    const val OTHER_URI = "file:///site-packages/other.py"
  }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putTextDocumentEdit(
  uri: String,
  newText: String,
  endLine: Int = 2,
) {
  putJsonObject("textDocument") {
    put("uri", uri)
    put("version", 0)
  }
  putJsonArray("edits") { addJsonObject { putTextEdit(newText, endLine) } }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putTextEdit(newText: String, endLine: Int = 2) {
  putJsonObject("range") {
    putJsonObject("start") {
      put("line", 0)
      put("character", 0)
    }
    putJsonObject("end") {
      put("line", endLine)
      put("character", 0)
    }
  }
  put("newText", newText)
}
