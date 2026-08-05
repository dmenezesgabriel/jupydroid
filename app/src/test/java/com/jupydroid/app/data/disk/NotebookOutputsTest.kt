package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * nbformat's output shapes, which are not the wire protocol's: a saved `stream` carries
 * `text` as an array of lines, and `execute_result` keeps its `execution_count` alongside
 * the data. Reading a notebook another tool wrote has to cope with both.
 */
class NotebookOutputsTest {
  @Test
  fun toDomain_readsAStreamWrittenAsAnArrayOfLines() {
    val output = outputOf("""{"output_type":"stream","name":"stdout","text":["one\n","two"]}""")

    assertEquals(CellOutput.Stream(StreamName.STDOUT, "one\ntwo"), output)
  }

  @Test
  fun toDomain_readsAStreamWrittenAsAPlainString() {
    val output = outputOf("""{"output_type":"stream","name":"stderr","text":"careful"}""")

    assertEquals(CellOutput.Stream(StreamName.STDERR, "careful"), output)
  }

  @Test
  fun toDomain_readsExecuteResultWithItsCount() {
    val output = outputOf(
      """{"output_type":"execute_result","execution_count":3,"data":{"text/plain":"42"},"metadata":{}}""",
    )

    assertEquals(CellOutput.ExecuteResult(mapOf("text/plain" to "42"), 3), output)
  }

  @Test
  fun toDomain_readsDisplayDataAndKeepsEveryMimeType() {
    val output = outputOf(
      """{"output_type":"display_data","data":{"image/png":"iVBOR","text/plain":"<Figure>"},"metadata":{}}""",
    ) as CellOutput.DisplayData

    assertEquals("iVBOR", output.mimeBundle["image/png"])
    assertEquals("<Figure>", output.mimeBundle["text/plain"])
  }

  @Test
  fun toDomain_readsAnErrorWithItsTraceback() {
    val output = outputOf(
      """{"output_type":"error","ename":"ValueError","evalue":"bad","traceback":["one","two"]}""",
    )

    assertEquals(CellOutput.Error("ValueError", "bad", listOf("one", "two")), output)
  }

  /** An output type we do not model must be dropped, not crash the notebook. */
  @Test
  fun toDomain_ignoresOutputTypesItDoesNotModel() {
    assertEquals(null, outputOf("""{"output_type":"from_the_future","payload":1}"""))
  }

  @Test
  fun roundTrip_survivesEveryOutputKindWeModel() {
    val outputs = listOf(
      CellOutput.Stream(StreamName.STDOUT, "hello\nworld"),
      CellOutput.ExecuteResult(mapOf("text/plain" to "42"), 7),
      CellOutput.DisplayData(mapOf("image/png" to "iVBOR")),
      CellOutput.Error("ValueError", "bad", listOf("line one")),
    )

    val reread = outputs.map { it.toFileOutput() }.mapNotNull { it.toDomainOutput() }

    assertEquals(outputs, reread)
  }

  /** Jupyter writes stream text as lines; writing one long string is legal but unfriendly. */
  @Test
  fun toFileOutput_writesStreamTextAsLinesTheWayJupyterDoes() {
    val written = CellOutput.Stream(StreamName.STDOUT, "one\ntwo").toFileOutput() as JsonObject

    assertEquals("stream", written["output_type"]!!.jsonPrimitive.content)
    assertTrue(
      "expected an array of lines, was ${written["text"]}",
      written["text"] is JsonArray,
    )
  }

  /** Jupyter writes "42\n" as ["42\n"]; a trailing "" is an artefact, not a line. */
  @Test
  fun toFileOutput_doesNotWriteATrailingEmptyLine() {
    val written = CellOutput.Stream(StreamName.STDOUT, "42\n").toFileOutput() as JsonObject

    assertEquals(1, (written["text"] as JsonArray).size)
  }

  @Test
  fun roundTrip_isExactForTextEndingInANewline() {
    val output = CellOutput.Stream(StreamName.STDOUT, "one\ntwo\n")

    assertEquals(output, output.toFileOutput().toDomainOutput())
  }

  private fun outputOf(json: String): CellOutput? = Json.parseToJsonElement(json).toDomainOutput()
}
