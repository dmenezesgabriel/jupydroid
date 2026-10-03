package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.mimeBundleOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    assertEquals(CellOutput.ExecuteResult(mimeBundleOf("text/plain" to "42"), 3), output)
  }

  @Test
  fun toDomain_readsDisplayDataAndKeepsEveryMimeType() {
    val output = outputOf(
      """{"output_type":"display_data","data":{"image/png":"iVBOR","text/plain":"<Figure>"},"metadata":{}}""",
    ) as CellOutput.DisplayData

    assertEquals(JsonPrimitive("iVBOR"), output.mimeBundle["image/png"])
    assertEquals(JsonPrimitive("<Figure>"), output.mimeBundle["text/plain"])
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

  /** Totality covers shape too: an `output_type` that is not a string cannot be dispatched. */
  @Test
  fun toDomain_ignoresAnOutputWhoseTypeIsNotAString() {
    assertEquals(null, outputOf("""{"output_type":{"kind":"display_data"},"data":{}}"""))
  }

  /** A stream's `text` member another tool wrote oddly must not take the output with it. */
  @Test
  fun toDomain_keepsTheLinesOfAStreamItCanRead() {
    val output = outputOf(
      """{"output_type":"stream","name":"stdout","text":["one\n",{"json":true},"two"]}""",
    )

    assertEquals(CellOutput.Stream(StreamName.STDOUT, "one\ntwo"), output)
  }

  /** The error survives its malformed traceback lines - the failure still names itself. */
  @Test
  fun toDomain_keepsAnErrorButDropsTheTracebackLinesItCannotRead() {
    val output = outputOf(
      """{"output_type":"error","ename":"ValueError","evalue":"bad","traceback":["one",["nested"],"two"]}""",
    )

    assertEquals(CellOutput.Error("ValueError", "bad", listOf("one", "two")), output)
  }

  /** `execution_count` is optional metadata - an unreadable one reads as none recorded. */
  @Test
  fun toDomain_readsAnExecuteResultWhoseCountIsUnreadableAsZero() {
    val output = outputOf(
      """{"output_type":"execute_result","execution_count":{"n":3},"data":{"text/plain":"42"}}""",
    ) as CellOutput.ExecuteResult

    assertEquals(0, output.executionCount)
  }

  @Test
  fun roundTrip_survivesEveryOutputKindWeModel() {
    val outputs = listOf(
      CellOutput.Stream(StreamName.STDOUT, "hello\nworld"),
      CellOutput.ExecuteResult(mimeBundleOf("text/plain" to "42"), 7),
      CellOutput.DisplayData(mimeBundleOf("image/png" to "iVBOR")),
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
