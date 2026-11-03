package com.jupydroid.app.data.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APACHE = "<license><name>Apache License, Version 2.0</name>" +
  "<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url></license>"

/** A POM as Maven Central serves it; null arguments leave the element out, as real POMs do. */
private fun pom(
  artifact: String,
  group: String? = "org.example",
  version: String? = "1.0",
  name: String? = null,
  licences: String? = null,
  parent: Triple<String, String, String>? = null,
): String = buildString {
  append("""<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>""")
  parent?.let { (g, a, v) ->
    append("<parent><groupId>$g</groupId><artifactId>$a</artifactId><version>$v</version></parent>")
  }
  group?.let { append("<groupId>$it</groupId>") }
  append("<artifactId>$artifact</artifactId>")
  version?.let { append("<version>$it</version>") }
  name?.let { append("<name>$it</name>") }
  licences?.let { append("<licenses>$it</licenses>") }
  append("</project>")
}

private const val LIBRARIES = "licences/gradle/libraries"
private const val PARENTS = "licences/gradle/parents"

class GradleLibraryLicencesTest {
  private fun render(files: Map<String, String>): String {
    val assets = InMemoryAssets(files)
    return GradleLibraryLicences(
      readAsset = { path -> assets.open(path).readBytes().toString(Charsets.UTF_8) },
      listAssets = assets::list,
    ).render()
  }

  @Test
  fun aLibraryShowsItsNameCoordinatesAndDeclaredLicence() {
    val text = render(
      mapOf("$LIBRARIES/org.example_lib-a_1.0.pom" to pom("lib-a", name = "Lib A", licences = APACHE)),
    )

    assertEquals(
      "Lib A\n  org.example:lib-a:1.0\n" +
        "  Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt)\n",
      text,
    )
  }

  @Test
  fun aLibraryWithoutAnElementNameShowsItsArtifactId() {
    val text = render(mapOf("$LIBRARIES/org.example_lib-a_1.0.pom" to pom("lib-a", licences = APACHE)))

    assertTrue(text, text.startsWith("lib-a\n  org.example:lib-a:1.0\n"))
  }

  @Test
  fun aLibraryInheritsLicenceGroupAndVersionFromItsParentChain() {
    val child = pom("child", group = null, version = null, parent = Triple("org.example", "mid", "2.0"))
    val mid = pom("mid", version = "2.0", parent = Triple("org.example", "top", "3.0"))
    val top = pom("top", version = "3.0", licences = APACHE)

    val text = render(
      mapOf(
        "$LIBRARIES/org.example_child_2.0.pom" to child,
        "$PARENTS/org.example_mid_2.0.pom" to mid,
        "$PARENTS/org.example_top_3.0.pom" to top,
      ),
    )

    assertTrue(text, "org.example:child:2.0" in text)
    assertTrue(text, "Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt)" in text)
  }

  @Test
  fun aLibraryWithNoLicenceAnywhereSaysSoRatherThanDisappearing() {
    val text = render(mapOf("$LIBRARIES/org.example_lib-a_1.0.pom" to pom("lib-a")))

    assertTrue(text, "licence not declared" in text)
  }

  @Test
  fun librariesAreListedByCoordinates() {
    val text = render(
      mapOf(
        "$LIBRARIES/org.example_zeta_1.0.pom" to pom("zeta", licences = APACHE),
        "$LIBRARIES/org.example_alpha_1.0.pom" to pom("alpha", licences = APACHE),
      ),
    )

    assertTrue(text, text.indexOf("alpha") < text.indexOf("zeta"))
  }

  @Test
  fun aMalformedPomFailsNamingItsPathAndTheShapeExpected() {
    val failure = assertThrows(IllegalStateException::class.java) {
      render(mapOf("$LIBRARIES/org.example_bad_1.0.pom" to "<project><unclosed>"))
    }

    assertTrue(failure.message.orEmpty(), "$LIBRARIES/org.example_bad_1.0.pom" in failure.message.orEmpty())
    assertTrue(failure.message.orEmpty(), "<project>" in failure.message.orEmpty())
  }

  @Test
  fun aParentTheBuildDidNotCopyFailsNamingItsPathAndTheTask() {
    val child = pom("child", parent = Triple("org.example", "gone", "9.9"))

    val failure = assertThrows(IllegalStateException::class.java) {
      render(mapOf("$LIBRARIES/org.example_child_1.0.pom" to child))
    }

    assertTrue(failure.message.orEmpty(), "$PARENTS/org.example_gone_9.9.pom" in failure.message.orEmpty())
    assertTrue(failure.message.orEmpty(), "GradleLicences" in failure.message.orEmpty())
  }

  @Test
  fun noBundledLibrariesFailsNamingTheDirectoryAndTheTask() {
    val failure = assertThrows(IllegalStateException::class.java) { render(emptyMap()) }

    assertTrue(failure.message.orEmpty(), LIBRARIES in failure.message.orEmpty())
    assertTrue(failure.message.orEmpty(), "GradleLicences" in failure.message.orEmpty())
  }
}
