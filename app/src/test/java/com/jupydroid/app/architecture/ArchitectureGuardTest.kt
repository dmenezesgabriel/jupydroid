package com.jupydroid.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Enforces the Working Agreement (docs/PLAN.md): the domain layer is pure Kotlin. */
class ArchitectureGuardTest {
  private val domainRoot = File("src/main/java/com/jupydroid/app/domain")

  @Test
  fun domainLayer_hasSources_withZeroAndroidImports() {
    val sources = ktSourcesUnder(domainRoot)
    assertTrue("domain layer must contain Kotlin sources", sources.isNotEmpty())

    val offenders = androidImports(sources)
    assertTrue("domain must not import android.*: $offenders", offenders.isEmpty())
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

  private fun ktSourcesUnder(root: File): List<File> =
    if (root.exists()) {
      root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    } else {
      emptyList()
    }

  private fun androidImports(sources: List<File>): List<String> =
    sources.flatMap { source ->
      source.readLines()
        .map { it.trim() }
        .filter { it.startsWith("import android.") }
        .map { "${source.name}: $it" }
    }
}
