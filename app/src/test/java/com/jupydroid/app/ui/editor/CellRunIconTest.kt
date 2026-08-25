package com.jupydroid.app.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PlayArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * P2v-1: a cell's play is outlined, not filled.
 *
 * A filled triangle at rest reads as "playing"; outlined reads as available, which is what an idle
 * control means - and a notebook draws one per cell, so the misread repeats down the whole
 * document (`docs/DECISIONS.md`, "The cell keeps its header strip"). Scope is deliberately narrow:
 * the three run controls in [CellHeader]. `NotebookBar`'s *Run all* and `SelectionTopBar`'s play
 * are toolbar commands acting on a selection rather than on a cell's own state, and keep their
 * filled glyphs.
 *
 * The icon choice is a value rather than an inline argument so this pin has something to hold:
 * without it the departure back to filled would be a one-token edit no gate would see.
 */
class CellRunIconTest {
  @Test
  fun aCellsPlayIsOutlined() {
    assertEquals(Icons.Outlined.PlayArrow, cellRunIcon)
  }

  /** Guards the exact regression this step retires, since identity makes the pair comparable. */
  @Test
  fun theFilledGlyphIsNotWhatACellDraws() {
    assertNotEquals(Icons.Filled.PlayArrow, cellRunIcon)
  }
}
