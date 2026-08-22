package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the cell-type control calls each of the three types.
 *
 * **The reference's own option labels, not this app's menu copy.** `CellTypeSwitcher.render`
 * builds an `HTMLSelect` over `Code`, `Markdown` and `Raw`
 * (`packages/notebook/src/default-toolbar.tsx:388-393`), which is what a control naming a *state*
 * says - where [cellActionItems]' rows name an *action* ("Change to markdown") because they are
 * a verb list. Two vocabularies for one enum, and this is the noun half.
 */
class CellTypeLabelTest {
  @Test
  fun eachTypeIsNamedTheWayTheReferenceNamesIt() {
    assertEquals("Code", cellTypeLabel(CellKind.CODE))
    assertEquals("Markdown", cellTypeLabel(CellKind.MARKDOWN))
    assertEquals("Raw", cellTypeLabel(CellKind.RAW))
  }

  /**
   * A fourth cell type would have to decide how it reads here rather than inherit a spelling,
   * which is [changeKindItem]'s reason for being exhaustive too. This is what says the `when` is
   * over the enum and not over a `name.lowercase()`.
   */
  @Test
  fun everyTypeHasOneAndTheyAreAllDifferent() {
    val labels = CellKind.entries.map { cellTypeLabel(it) }
    assertEquals(CellKind.entries.size, labels.toSet().size)
  }
}
