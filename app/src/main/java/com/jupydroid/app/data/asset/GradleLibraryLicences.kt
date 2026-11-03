package com.jupydroid.app.data.asset

import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Where the `bundle<Variant>GradleLicences` task in `app/build.gradle.kts` puts each linked library's POM. */
internal const val GRADLE_LIBRARIES_DIR = "licences/gradle/libraries"

/** Where it puts the POMs those name as `<parent>`, which carry the licence a child leaves out. */
private const val GRADLE_PARENTS_DIR = "licences/gradle/parents"

private const val BUNDLING_TASK = "bundle<Variant>GradleLicences"
private const val NOT_DECLARED = "licence not declared"

private data class PomCoordinates(val group: String, val artifact: String, val version: String)

/** The few facts of a POM the Licences screen needs; each may be absent because Maven lets a child inherit it. */
private data class PomFacts(
  val group: String?,
  val artifact: String,
  val version: String?,
  val name: String?,
  val licences: List<String>,
  val parent: PomCoordinates?,
)

/**
 * The licences of the Gradle libraries linked into the APK, read from the POMs the build bundled,
 * as the text of one document: a library's name, its coordinates, then the licence it declares.
 *
 * A POM that declares no `<licenses>` gets the first one up its `<parent>` chain, because that is
 * where Maven Central puts it for commons-io, commons-codec and the commonmark modules. One that has
 * none anywhere says so rather than being left out: a missing line is indistinguishable from a
 * library the build forgot.
 *
 * ```
 * GradleLibraryLicences(readAsset, listAssets).render() // "kotlinx-coroutines-core\n  org.jetbrains..."
 * ```
 */
internal class GradleLibraryLicences(
  private val readAsset: (String) -> String,
  private val listAssets: (String) -> List<String>,
) {
  fun render(): String {
    val names = listAssets(GRADLE_LIBRARIES_DIR).sorted()
    check(names.isNotEmpty()) {
      "no POMs under '$GRADLE_LIBRARIES_DIR'; expected one per linked library, copied by the $BUNDLING_TASK task"
    }
    return names.map { library("$GRADLE_LIBRARIES_DIR/$it") }.sortedBy { it.first }.joinToString("") { it.second }
  }

  /** The coordinates (for ordering) and the rendered entry of the library whose POM is at [path]. */
  private fun library(path: String): Pair<String, String> {
    val chain = pomChain(path)
    val coordinates = "${chain.firstNotNullOfOrNull { it.group }}:${chain[0].artifact}" +
      ":${chain.firstNotNullOfOrNull { it.version }}"
    val licences = chain.firstOrNull { it.licences.isNotEmpty() }?.licences ?: listOf(NOT_DECLARED)
    val name = chain[0].name?.takeUnless { "\${" in it } ?: chain[0].artifact
    return coordinates to "$name\n  $coordinates\n" + licences.joinToString("") { "  $it\n" }
  }

  private fun pomChain(path: String): List<PomFacts> {
    val chain = mutableListOf(parse(path))
    while (true) {
      val parent = chain.last().parent ?: return chain
      chain += parse("$GRADLE_PARENTS_DIR/${parent.group}_${parent.artifact}_${parent.version}.pom")
    }
  }

  private fun parse(path: String): PomFacts = try {
    val project = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
      .newDocumentBuilder().parse(InputSource(StringReader(readAsset(path)))).documentElement
    project.toFacts()
  } catch (failure: IOException) {
    throw IllegalStateException("POM '$path' is not in the APK; the $BUNDLING_TASK task copies it", failure)
  } catch (failure: SAXException) {
    throw IllegalStateException("POM '$path' is not well-formed XML; expected a <project> element", failure)
  }
}

private fun Element.children(): List<Element> =
  (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

private fun Element.child(name: String): Element? = children().firstOrNull { it.localName == name }

private fun Element.childText(name: String): String? = child(name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

private fun Element.toFacts(): PomFacts = PomFacts(
  group = childText("groupId"),
  artifact = requireNotNull(childText("artifactId")) { "POM has no <artifactId>; expected a <project> POM" },
  version = childText("version"),
  name = childText("name"),
  licences = child("licenses")?.children().orEmpty().mapNotNull { it.toLicenceLine() },
  parent = child("parent")?.toCoordinates(),
)

private fun Element.toCoordinates(): PomCoordinates = PomCoordinates(
  group = requireNotNull(childText("groupId")) { "<parent> has no <groupId>" },
  artifact = requireNotNull(childText("artifactId")) { "<parent> has no <artifactId>" },
  version = requireNotNull(childText("version")) { "<parent> has no <version>" },
)

private fun Element.toLicenceLine(): String? {
  val name = childText("name")
  val url = childText("url")
  return when {
    name != null && url != null -> "$name ($url)"
    else -> name ?: url
  }
}
