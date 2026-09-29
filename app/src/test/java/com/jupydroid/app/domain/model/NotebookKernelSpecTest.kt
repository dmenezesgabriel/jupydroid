package com.jupydroid.app.domain.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `NotebookPanel._updateSpec` (`packages/notebook/src/panel.ts:248-258`): a kernel that binds
 * writes `metadata.kernelspec = {name, display_name, language}` - the whole object, not a merge,
 * the way `setMetadata` replaces the key. That is what makes reopening the file honour the kernel
 * the user picked.
 */
class NotebookKernelSpecTest {
  private val spec = KernelSpec(name = "p8g", displayName = "P8g Julia", language = "julia")

  private val notebook = Notebook(
    id = "nb-1",
    title = "Data",
    kernelId = "python3",
    cells = emptyList(),
    preserved = metadata(
      "kernelspec" to specObject("name" to "python3", "display_name" to "Python 3", "language" to "python"),
      "language_info" to specObject("name" to "python", "version" to "3.12.3"),
      "widgets" to specObject("state" to JsonObject(emptyMap())),
    ),
  )

  @Test
  fun withKernelSpec_writesNameDisplayNameAndLanguage() {
    val written = notebook.withKernelSpec("p8g", spec).kernelspecJson()

    assertEquals("p8g", written["name"]?.jsonPrimitive?.content)
    assertEquals("P8g Julia", written["display_name"]?.jsonPrimitive?.content)
    assertEquals("julia", written["language"]?.jsonPrimitive?.content)
    assertEquals("p8g", notebook.withKernelSpec("p8g", spec).kernelId)
  }

  /** The other metadata keys are not the kernelspec's, and are left alone. */
  @Test
  fun withKernelSpec_keepsUnrelatedMetadata() {
    val metadata = notebook.withKernelSpec("p8g", spec).metadataJson()

    assertEquals(
      specObject("name" to "python", "version" to "3.12.3"),
      metadata["language_info"],
    )
    assertEquals(specObject("state" to JsonObject(emptyMap())), metadata["widgets"])
  }

  /**
   * `setMetadata` *replaces* the object: a bound kernel whose name is not a known spec - a
   * `Running` attach whose kernel carries no catalogue entry - writes only its name, and the
   * previous kernel's `display_name`/`language` go rather than surviving to lie about the new one.
   */
  @Test
  fun withKernelSpec_forASpeclessKernelWritesOnlyTheName() {
    val written = notebook.withKernelSpec("xeus-python", spec = null).kernelspecJson()

    assertEquals(JsonObject(mapOf("name" to JsonPrimitive("xeus-python"))), written)
  }

  @Test
  fun kernelLanguage_isThePersistedLanguageInfoName() {
    assertEquals("python", notebook.kernelLanguage)
    assertEquals(null, notebook.copy(preserved = JsonObject(emptyMap())).kernelLanguage)
  }

  private fun Notebook.metadataJson(): JsonObject = preserved["metadata"] as? JsonObject ?: JsonObject(emptyMap())

  private fun Notebook.kernelspecJson(): JsonObject =
    metadataJson()["kernelspec"] as? JsonObject ?: JsonObject(emptyMap())

  private fun metadata(vararg pairs: Pair<String, JsonObject>): JsonObject =
    JsonObject(mapOf("metadata" to JsonObject(mapOf(*pairs))))

  private fun specObject(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
    pairs.associate { (key, value) ->
      key to when (value) {
        is JsonObject -> value
        else -> JsonPrimitive(value as String?)
      }
    },
  )
}
