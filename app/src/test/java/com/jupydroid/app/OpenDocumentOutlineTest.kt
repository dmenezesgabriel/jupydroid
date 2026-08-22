package com.jupydroid.app

import com.jupydroid.app.ui.editor.TocHeading
import com.jupydroid.app.ui.editor.TocNumbering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the open document hands its structure to the shell's side panel, and - the half that has a
 * lifetime rather than a value - where it takes it back.
 */
class OpenDocumentOutlineTest {
  private val headings = listOf(
    TocHeading("h1", 1, "Chapter one"),
    TocHeading("h2", 2, "Part A"),
  )

  @Test
  fun nothingIsOpenUntilSomethingPublishes() {
    assertNull(OpenDocumentOutline().current)
  }

  @Test
  fun whatWasPublishedIsWhatThePanelReads() {
    val open = OpenDocumentOutline()

    open.publish(DocumentOutline("Analysis.ipynb", headings, TocNumbering(numberHeaders = true)) {})

    val outline = requireNotNull(open.current)
    assertEquals("Analysis.ipynb", outline.title)
    assertEquals(headings, outline.headings)
    assertEquals(TocNumbering(numberHeaders = true), outline.numbering)
  }

  /**
   * `toc.model = model` is an assignment, not an append (`packages/toc-extension/src/index.ts:353`):
   * a second document replaces the first rather than joining it.
   */
  @Test
  fun aSecondDocumentReplacesTheFirst() {
    val open = OpenDocumentOutline()
    open.publish(DocumentOutline("First.ipynb", headings, TocNumbering()) {})

    open.publish(DocumentOutline("Second.ipynb", emptyList(), TocNumbering()) {})

    assertEquals("Second.ipynb", open.current?.title)
    assertEquals(emptyList<TocHeading>(), open.current?.headings)
  }

  /**
   * **A table of contents cannot outlive its document.** The reference disposes the model with the
   * widget - `widget.disposed.connect(() => { model?.dispose(); })`
   * (`packages/toc-extension/src/index.ts:341-343`) - and this is the same rule said as a clear,
   * so nothing is left holding a jump into a document that has gone.
   */
  @Test
  fun closingTheDocumentEmptiesThePanel() {
    val open = OpenDocumentOutline()
    open.publish(DocumentOutline("Analysis.ipynb", headings, TocNumbering()) {})

    open.clear()

    assertNull(open.current)
  }

  /** The jump is published with the headings, because a heading nothing can reach is a list. */
  @Test
  fun theJumpReachesTheDocumentThatPublishedIt() {
    val jumped = mutableListOf<String>()
    val open = OpenDocumentOutline()
    open.publish(DocumentOutline("Analysis.ipynb", headings, TocNumbering()) { jumped += it })

    open.current?.jumpTo("h2")

    assertEquals(listOf("h2"), jumped)
  }
}
