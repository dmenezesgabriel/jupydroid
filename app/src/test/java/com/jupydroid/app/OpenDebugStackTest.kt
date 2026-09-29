package com.jupydroid.app

import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.DebugVariableGroup
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the open notebook hands its stop to the shell's side panel - [OpenDocumentOutline]'s own
 * seam, and the same "a live callback travels with the data" shape [DocumentOutline.jumpTo]
 * already established, so a compound variable can be expanded from a pane that owns no session.
 */
class OpenDebugStackTest {
  private val stack = DebugStack(
    frames = listOf(DebugStackFrame(id = 1, name = "<module>", line = 2, column = 1)),
    groups = listOf(DebugVariableGroup(scope = "Locals", variables = emptyList())),
  )

  @Test
  fun nothingIsStoppedUntilSomethingPublishes() {
    assertNull(OpenDebugStack().current)
  }

  @Test
  fun whatWasPublishedIsWhatThePanelReads() {
    val open = OpenDebugStack()

    open.publish(stack, expand = { emptyList() }, selectFrame = {}, evaluate = { null })

    assertEquals(stack, open.current)
  }

  @Test
  fun closingTheDocumentEmptiesThePanel() {
    val open = OpenDebugStack()
    open.publish(stack, expand = { emptyList() }, selectFrame = {}, evaluate = { null })

    open.clear()

    assertNull(open.current)
  }

  /** The expand reaches the debugger that published it, not a stale one from before a re-publish. */
  @Test
  fun expandReachesTheDebuggerThatPublishedIt() = runTest {
    val open = OpenDebugStack()
    open.publish(stack, expand = { ref ->
      listOf(DebugVariable("$ref", "0", "int", 0))
    }, selectFrame = {}, evaluate = { null })

    val children = open.expand(9)

    assertEquals(listOf(DebugVariable("9", "0", "int", 0)), children)
  }

  /** Nothing left to call once the document has gone - the same rule [clear] already states. */
  @Test
  fun expandAnswersNothingOnceCleared() = runTest {
    val open = OpenDebugStack()
    open.publish(stack, expand = { ref ->
      listOf(DebugVariable("$ref", "0", "int", 0))
    }, selectFrame = {}, evaluate = { null })

    open.clear()

    assertEquals(emptyList<DebugVariable>(), open.expand(9))
  }

  /** [OpenDebugStack.selectFrame] reaches the debugger that published it (P6a-15), [expand]'s own shape. */
  @Test
  fun selectFrameReachesTheDebuggerThatPublishedIt() = runTest {
    val open = OpenDebugStack()
    var selected: Int? = null
    open.publish(stack, expand = { emptyList() }, selectFrame = { selected = it }, evaluate = { null })

    open.selectFrame(5)

    assertEquals(5, selected)
  }

  /** Nothing left to call once the document has gone - [expandAnswersNothingOnceCleared]'s own shape. */
  @Test
  fun selectFrameDoesNothingOnceCleared() = runTest {
    val open = OpenDebugStack()
    var selected: Int? = null
    open.publish(stack, expand = { emptyList() }, selectFrame = { selected = it }, evaluate = { null })

    open.clear()
    open.selectFrame(5)

    assertNull(selected)
  }

  /** [OpenDebugStack.evaluate] reaches the debugger that published it (P6a-17), [expand]'s own shape. */
  @Test
  fun evaluateReachesTheDebuggerThatPublishedIt() = runTest {
    val open = OpenDebugStack()
    open.publish(
      stack,
      expand = { emptyList() },
      selectFrame = {},
      evaluate = { expression -> DebugVariable(expression, "42", "int", 0) },
    )

    val result = open.evaluate("6 * 7")

    assertEquals(DebugVariable("6 * 7", "42", "int", 0), result)
  }

  /** Nothing left to call once the document has gone - [expandAnswersNothingOnceCleared]'s own shape. */
  @Test
  fun evaluateAnswersNothingOnceCleared() = runTest {
    val open = OpenDebugStack()
    open.publish(stack, expand = { emptyList() }, selectFrame = {}, evaluate = { DebugVariable("x", "1", "int", 0) })

    open.clear()

    assertNull(open.evaluate("x"))
  }
}
