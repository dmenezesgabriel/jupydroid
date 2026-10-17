package com.jupydroid.extensions.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An extension keeps `:app`'s layering inside its module (ADR-0104), and so `:app`'s first rule: the
 * domain is pure Kotlin, importing no `android.*` and none of the module's own adapters or UI -
 * the rule `ArchitectureGuardTest` holds for `:app` (`app/src/test/java/com/jupydroid/app/architecture/`).
 */
class AiDomainPurityTest {
  private val domainRoot = File("src/main/java/com/jupydroid/extensions/ai/domain")

  @Test
  fun theDomainHasSourcesAndImportsNothingButKotlin() {
    val sources = domainRoot.walkTopDown().filter { it.extension == "kt" }.toList()
    assertTrue("no Kotlin sources under ${domainRoot.absolutePath}", sources.isNotEmpty())

    val forbidden =
      listOf("android.", "androidx.", "com.jupydroid.extensions.ai.data", "com.jupydroid.extensions.ai.ui")
    val offenders = sources.flatMap { source ->
      source.readLines()
        .filter { line -> line.startsWith("import ") && forbidden.any { line.removePrefix("import ").startsWith(it) } }
        .map { "${source.name}: $it" }
    }

    assertEquals(emptyList<String>(), offenders)
  }
}
