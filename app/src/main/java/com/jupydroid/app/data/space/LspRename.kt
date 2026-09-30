package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguageWorkspaceEdit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `textDocument/rename` reply: a `WorkspaceEdit` (LSP 3.17) or `null` - `null` and empty
 * are the same nothing, the server has no rename for the symbol at the position.
 *
 * Two shapes normalize into [LanguageWorkspaceEdit.changes]: `documentChanges` - the form the
 * shipped `pylsp` answers, one `TextDocumentEdit` per document whose `edits` carry a single
 * whole-document `TextEdit` (`LspSurfaceProbeTest`'s capture) - and the legacy `changes` uri-to-edits
 * map, which the spec keeps as the fallback and prefers `documentChanges` over when both ride
 * one reply. `documentChanges` entries that are resource operations - `kind` of `create`,
 * `rename` or `delete` - drop out: they carry no `textDocument`/`edits`, and a cell editor
 * applies text edits, not file operations.
 *
 * Kept off [LspWire] the way [LspDefinition] is: the wire holds request params and the leaves
 * replies share, this holds the rename reply.
 */
internal object LspRename {
  fun workspaceEditFrom(result: JsonElement?): LanguageWorkspaceEdit {
    val edit = result as? JsonObject
    val documentChanges = edit?.get("documentChanges") as? JsonArray
    return when {
      edit == null -> LanguageWorkspaceEdit(emptyMap())
      // `documentChanges` when the key rides the reply - LSP's own "prefer over `changes`".
      documentChanges != null -> LanguageWorkspaceEdit(textDocumentEdits(documentChanges))
      else -> LanguageWorkspaceEdit(editsByUri(edit["changes"]))
    }
  }

  /** `documentChanges`'s `TextDocumentEdit` entries, collected into uri-keyed edit lists. */
  private fun textDocumentEdits(changes: JsonArray): Map<String, List<LanguageEdit>> {
    val byUri = linkedMapOf<String, MutableList<LanguageEdit>>()
    changes.forEach { change ->
      val document = (change as? JsonObject)?.get("textDocument") as? JsonObject ?: return@forEach
      val uri = document["uri"]?.jsonPrimitive?.contentOrNull ?: return@forEach
      val edits = change["edits"] as? JsonArray ?: return@forEach
      edits.mapNotNullTo(byUri.getOrPut(uri, ::mutableListOf), ::textEditFrom)
    }
    return byUri
  }

  /** The legacy `changes` map - `{uri: TextEdit[]}` - collected the same way. */
  private fun editsByUri(changes: JsonElement?): Map<String, List<LanguageEdit>> {
    val byUri = linkedMapOf<String, MutableList<LanguageEdit>>()
    (changes as? JsonObject)?.forEach { (uri, edits) ->
      (edits as? JsonArray)?.mapNotNullTo(byUri.getOrPut(uri, ::mutableListOf), ::textEditFrom)
    }
    return byUri
  }
}
