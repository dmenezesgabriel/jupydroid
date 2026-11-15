package com.jupydroid.extension.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A button the contract hands out draws the keyboard-focus ring, because it goes through
 * `FocusRingIconButton`, `FocusRingTextButton` or `FocusRingDropdownMenuItem`.
 *
 * material3 1.4.0's `IconButton` and `TextButton` call `ripple()` themselves, so the ring never
 * reaches them and a Tab changes only a plate at 1.14:1. A file that names the raw one brings that
 * plate back on the control; this is `:app`'s `ArchitectureGuardTest.uiLayer_callsNoMaterialTextButton`
 * for the module that holds the dialogs every extension asks the host for.
 */
class FocusRingGuardTest {
  private val sourceRoot = File("src/main/java/com/jupydroid/extension/api")

  private val wrappers = setOf(
    "FocusRingIconButton.kt",
    "FocusRingTextButton.kt",
    "FocusRingDropdownMenuItem.kt",
  )

  @Test
  fun theContractNamesNoMaterialIconButtonOrTextButton() {
    val sources = sourceRoot.walkTopDown().filter { it.extension == "kt" }.toList()
    assertTrue("no Kotlin sources under ${sourceRoot.absolutePath}", sources.isNotEmpty())

    val offenders = sources.filterNot { it.name in wrappers }.flatMap { source ->
      source.readLines()
        .filter { line -> RAW_BUTTONS.any { line.trim() == "import androidx.compose.material3.$it" } }
        .map { "${source.name}: ${it.trim()}" }
    }

    assertEquals(
      "use the FocusRing wrappers, not material3's own (expected no import of $RAW_BUTTONS)",
      emptyList<String>(),
      offenders,
    )
  }

  /**
   * Compose's dialog window draws no background to inset, and M3's `AlertDialog` is `sizeIn(280dp, 560dp)`
   * with nothing outside it, so a dialog whose text wraps fills a phone edge to edge. `MarginedAlertDialog`
   * keeps 24dp beside the surface; a dialog this module draws that reaches past it loses that. This is
   * `:app`'s `ArchitectureGuardTest.uiLayer_callsNoMaterialAlertDialog` for the module `ui/`'s rule cannot scan.
   */
  @Test
  fun theContractNamesNoMaterialAlertDialog() {
    val offenders = sourceRoot.walkTopDown()
      .filter { it.extension == "kt" && it.name != "MarginedAlertDialog.kt" }
      .flatMap { source ->
        source.readLines()
          .filter { it.trim() == "import androidx.compose.material3.AlertDialog" }
          .map { "${source.name}: ${it.trim()}" }
      }.toList()

    assertEquals(
      "use MarginedAlertDialog, not material3's AlertDialog (expected no import of it)",
      emptyList<String>(),
      offenders,
    )
  }

  private companion object {
    val RAW_BUTTONS = listOf("IconButton", "TextButton", "DropdownMenuItem")
  }
}
