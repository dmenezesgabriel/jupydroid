package com.jupydroid.extensions.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An extension keeps `:app`'s layering inside its module (ADR-0104), and so `:app`'s first rule: the
 * domain is pure Kotlin, importing no `android.*` and none of the module's own adapters or UI -
 * the rule `ArchitectureGuardTest` holds for `:app` (`app/src/test/java/com/jupydroid/app/architecture/`).
 */
class GitDomainPurityTest {
  private val domainRoot = File("src/main/java/com/jupydroid/extensions/git/domain")

  @Test
  fun theDomainHasSourcesAndImportsNothingButKotlin() {
    val sources = domainRoot.walkTopDown().filter { it.extension == "kt" }.toList()
    assertTrue("no Kotlin sources under ${domainRoot.absolutePath}", sources.isNotEmpty())

    val forbidden =
      listOf("android.", "androidx.", "com.jupydroid.extensions.git.data", "com.jupydroid.extensions.git.ui")
    val offenders = sources.flatMap { source ->
      source.readLines()
        .filter { line -> line.startsWith("import ") && forbidden.any { line.removePrefix("import ").startsWith(it) } }
        .map { "${source.name}: $it" }
    }

    assertEquals(emptyList<String>(), offenders)
  }
}
