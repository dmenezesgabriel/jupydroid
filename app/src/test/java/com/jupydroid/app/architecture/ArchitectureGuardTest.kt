package com.jupydroid.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Enforces the Working Agreement (CLAUDE.md, "Architecture"): the domain layer is pure Kotlin. */
class ArchitectureGuardTest {
  private val domainRoot = File("src/main/java/com/jupydroid/app/domain")

  /**
   * The document-and-language row of `docs/architecture.md`: parsers and formats that model
   * documents and languages rather than this app's domain. CLAUDE.md requires them free of
   * `android.*` **so that every test for them is a plain JVM test**, which was prose until P4f-1.
   *
   * P4f is the phase that would have broken it: `TerminalEmulator.java` imports
   * `android.util.Base64` on its OSC 52 path and `TerminalColors.java` imports
   * `android.graphics.Color`, and both are transcribed into `terminal/`.
   */
  private val documentAndLanguageRoots = listOf("syntax", "markdown", "html", "import", "terminal")
    .map { File("src/main/java/com/jupydroid/app/$it") }

  @Test
  fun domainLayer_hasSources_withZeroAndroidImports() {
    val sources = ktSourcesUnder(domainRoot)
    assertTrue("domain layer must contain Kotlin sources", sources.isNotEmpty())

    val offenders = androidImports(sources)
    assertTrue("domain must not import android.*: $offenders", offenders.isEmpty())
  }

  @Test
  fun documentAndLanguageModules_haveSources_withZeroAndroidImports() {
    for (root in documentAndLanguageRoots) {
      val sources = ktSourcesUnder(root)
      assertTrue("${root.name} must contain Kotlin sources", sources.isNotEmpty())

      val offenders = androidImports(sources)
      assertTrue("${root.name} must not import android.*: $offenders", offenders.isEmpty())
    }
  }

  @Test
  fun domainLayer_doesNotImportOtherLayers() {
    val sources = ktSourcesUnder(domainRoot)
    val forbidden = listOf(
      "com.jupydroid.app.data",
      "com.jupydroid.app.ui",
      "com.jupydroid.app.di",
    )

    val offenders = sources.flatMap { source ->
      source.readLines()
        .map { it.trim() }
        .filter { it.startsWith("import ") }
        .filter { line -> forbidden.any { line.startsWith("import $it") } }
        .map { "${source.name}: $it" }
    }
    assertTrue("domain must not import other layers: $offenders", offenders.isEmpty())
  }

  private fun ktSourcesUnder(root: File): List<File> = if (root.exists()) {
    root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
  } else {
    emptyList()
  }

  private fun androidImports(sources: List<File>): List<String> = sources.flatMap { source ->
    source.readLines()
      .map { it.trim() }
      .filter { it.startsWith("import android.") }
      .map { "${source.name}: $it" }
  }
}
