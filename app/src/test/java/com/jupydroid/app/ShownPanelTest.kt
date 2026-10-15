package com.jupydroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * The side area's selection once an extension can add a panel to it (ADR-0104): the same toggle and
 * the same one-pane rule as the shell's own panels, and a place on the rail.
 */
class ShownPanelTest {
  private val files = ShownPanel.Shell(SidePanel.FILES)
  private val sourceControl = ShownPanel.Contributed("git.sourceControl")

  @Test
  fun aContributedPanelTogglesLikeTheShellsOwn() {
    assertEquals(sourceControl, files.chosen(sourceControl))
    assertNull(sourceControl.chosen(sourceControl))
    assertEquals(files, sourceControl.chosen(files))
  }

  /** The one-pane bar has no item for a contributed panel, so it falls back as the outline does. */
  @Test
  fun aOnePaneWindowShowsFilesInPlaceOfAContributedPanel() {
    assertEquals(SidePanel.FILES, sourceControl.showingAtOnePane())
    assertEquals(SidePanel.SEARCH, ShownPanel.Shell(SidePanel.SEARCH).showingAtOnePane())
    assertEquals(SidePanel.FILES, (null as ShownPanel?).showingAtOnePane())
  }

  /**
   * After Search, which is VS Code's place for Source Control (`order: 2`, after Search's 1), and
   * before the table of contents and the debugger, which only JupyterLab and this app rank (ADR-0062).
   */
  @Test
  fun contributedPanelsSitAfterSearchInTheOrderGiven() {
    val order = railOrder(contributedIds = listOf("a", "b"))

    assertEquals(
      listOf(
        ShownPanel.Shell(SidePanel.FILES),
        ShownPanel.Shell(SidePanel.RUNNING),
        ShownPanel.Shell(SidePanel.SEARCH),
        ShownPanel.Contributed("a"),
        ShownPanel.Contributed("b"),
        ShownPanel.Shell(SidePanel.TABLE_OF_CONTENTS),
        ShownPanel.Shell(SidePanel.DEBUGGER),
      ),
      order,
    )
  }

  /** `rememberSaveable` keeps it across a rotation only if a Bundle can hold it. */
  @Test
  fun theSelectionSurvivesSerialisation() {
    for (shown in listOf(files, sourceControl)) {
      assertEquals(shown, roundTrip(shown))
    }
  }

  private fun roundTrip(shown: ShownPanel): Any? {
    val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(shown) } }
    return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
  }
}
