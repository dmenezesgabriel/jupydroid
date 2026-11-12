package com.jupydroid.extensions.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A button in the chat panel draws the keyboard-focus ring, because it is one of the contract's
 * `FocusRingIconButton` / `FocusRingTextButton`, or carries `focusRing()` itself.
 *
 * material3 1.4.0's buttons call `ripple()` themselves, so the host's ring never reaches them and a
 * Tab changes only a plate at 1.14:1. This is `extension-api`'s `FocusRingGuardTest` for the module
 * built against it (ADR-0104). The kinds with one or two call sites take `focusRing()` on their own
 * `modifier` rather than a wrapper in the contract.
 */
class FocusRingGuardTest {
  private val sourceRoot = File("src/main/java/com/jupydroid/extensions/ai")

  private val sources: List<File>
    get() = sourceRoot.walkTopDown().filter { it.extension == "kt" }.toList().also {
      assertTrue("no Kotlin sources under ${sourceRoot.absolutePath}", it.isNotEmpty())
    }

  @Test
  fun noFileImportsMaterialIconButtonOrTextButton() {
    val offenders = sources.flatMap { source ->
      source.readLines()
        .filter { line -> WRAPPED_KINDS.any { line.trim() == "import androidx.compose.material3.$it" } }
        .map { "${source.name}: ${it.trim()}" }
    }

    assertEquals(
      "use FocusRingIconButton / FocusRingTextButton, not material3's own (expected no import of $WRAPPED_KINDS)",
      emptyList<String>(),
      offenders,
    )
  }

  @Test
  fun everyFilledIconButtonAndOutlinedButtonCallCarriesFocusRing() {
    val offenders = sources.mapNotNull { source ->
      val text = source.readText()
      val calls = DIRECT_KINDS.sumOf { text.split("$it(").size - 1 }
      val rings = text.split("focusRing()").size - 1
      "${source.name}: $calls call(s), $rings focusRing()".takeIf { rings < calls }
    }

    assertEquals(
      "put Modifier.focusRing() on each call of $DIRECT_KINDS (a call here has none)",
      emptyList<String>(),
      offenders,
    )
  }

  private companion object {
    val WRAPPED_KINDS = listOf("IconButton", "TextButton")
    val DIRECT_KINDS = listOf("FilledIconButton", "OutlinedButton")
  }
}
