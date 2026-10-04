package com.jupydroid.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the Working Agreement (AGENTS.md, "Architecture"): the domain layer is pure Kotlin, the
 * document-and-language packages carry no `android.*`, and `architecture/building-blocks.md` §5.4's
 * dependency rules hold — the whole table, gated rather than grepped.
 */
class ArchitectureGuardTest {
  private val mainRoot = File("src/main/java/com/jupydroid/app")
  private val domainRoot = File("src/main/java/com/jupydroid/app/domain")
  private val uiRoot = File("src/main/java/com/jupydroid/app/ui")
  private val dataRoot = File("src/main/java/com/jupydroid/app/data")

  /**
   * The document-and-language row of `docs/architecture/building-blocks.md`: parsers and formats
   * that model documents and languages rather than this app's domain. AGENTS.md requires them free
   * of `android.*` **so that every test for them is a plain JVM test**, which was prose until this
   * test existed.
   *
   * The terminal is what would have broken it: `TerminalEmulator.java` imports
   * `android.util.Base64` on its OSC 52 path and `TerminalColors.java` imports
   * `android.graphics.Color`, and both are transcribed into `terminal/`. The list is §5.2's six
   * packages in §5.2's order, so the two read side by side — a gap between them is the drift
   * `GAP-006` (`docs/architecture.md` §11) measured when `glob/` sat covered by §5.2's produced
   * count but absent from this guard.
   */
  private val documentAndLanguageRoots =
    listOf("glob", "html", "import", "markdown", "syntax", "terminal")
      .map { File("src/main/java/com/jupydroid/app/$it") }

  /**
   * The real-adapter packages, as against `data.fake` — which is deliberately absent: a port-typed
   * parameter defaulting to `Fake*()` is the manual-DI mechanism (`architecture/building-blocks.md`
   * §5.6), not a violation. `GAP-006` (`docs/architecture.md` §11) measured the documented "ui
   * never imports data" as already false in exactly that way, so the gated rule is the one that
   * holds: `ui` names no *real* adapter.
   */
  private val realAdapterPrefixes =
    listOf(
      "com.jupydroid.app.data.disk",
      "com.jupydroid.app.data.intent",
      "com.jupydroid.app.data.space",
    )

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

  @Test
  fun uiLayer_namesNoRealAdapterPackages() {
    val sources = ktSourcesUnder(uiRoot)
    assertTrue("ui layer must contain Kotlin sources", sources.isNotEmpty())

    val offenders = qualifiedRefs(sources, realAdapterPrefixes)
    assertTrue(
      "ui must not name a real adapter (data.fake is the DI default): $offenders",
      offenders.isEmpty(),
    )
  }

  @Test
  fun dataLayer_namesNoUiOrDi() {
    val sources = ktSourcesUnder(dataRoot)
    assertTrue("data layer must contain Kotlin sources", sources.isNotEmpty())

    val offenders =
      qualifiedRefs(sources, listOf("com.jupydroid.app.ui", "com.jupydroid.app.di"))
    assertTrue("data must not name ui/di: $offenders", offenders.isEmpty())
  }

  /**
   * §5.4's composition-root rule: a real adapter is named only inside `data/` itself — adapters may
   * compose — and at the composition root: `di/` and `JupydroidApplication.kt`, the call site that
   * builds `AppContainer`.
   */
  @Test
  fun realAdapters_areNamedOnlyInsideDataOrTheCompositionRoot() {
    val outsiders = ktSourcesUnder(mainRoot).filterNot(::isAdapterOrCompositionRoot)
    val offenders = qualifiedRefs(outsiders, realAdapterPrefixes)
    assertTrue(
      "only data/, di/ and JupydroidApplication name real adapters: $offenders",
      offenders.isEmpty(),
    )
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

  /**
   * Every non-comment line naming one of [prefixes] — imports *and* inline qualified uses, so an
   * `import`-only scan cannot be dodged by spelling the class out. KDoc links such as
   * `[com.jupydroid.app.di.AppContainer]` are citations, not dependencies, so comment lines are
   * skipped.
   */
  private fun qualifiedRefs(sources: List<File>, prefixes: List<String>): List<String> = sources.flatMap { source ->
    source.readLines().mapIndexedNotNull { index, raw ->
      val line = raw.trim()
      if (isComment(line) || prefixes.none { line.contains(it) }) {
        null
      } else {
        "${source.name}:${index + 1}: $line"
      }
    }
  }

  private fun isComment(line: String): Boolean = line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")

  private fun isAdapterOrCompositionRoot(source: File): Boolean = source.path.contains("/data/") ||
    source.path.contains("/di/") ||
    source.name == "JupydroidApplication.kt"
}
