package com.jupydroid.app.data.http

import com.jupydroid.app.domain.model.PackageReleases
import com.jupydroid.app.domain.model.normalizedPackageName
import com.jupydroid.app.domain.port.WheelIndex
import com.jupydroid.app.domain.port.WheelIndexContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The contract against a real loopback server, and what only HTTP has: a 404, a dead host, and a
 * body that is not the index.
 */
class PyPiWheelIndexTest : WheelIndexContract() {
  @get:Rule val serverRule = MockWebServerRule()

  private val duckdb =
    PackageReleases(
      latestVersion = "2.0.1",
      wheelNamesByVersion = mapOf(
        "2.0.0" to listOf("duckdb-2.0.0-cp312-abi3-manylinux_2_26_aarch64.whl"),
        "2.0.1" to listOf(
          "duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.whl",
          "duckdb-2.0.1-cp312-abi3-manylinux_2_27_x86_64.whl",
        ),
      ),
    )

  override fun indexServing(packageName: String, releases: PackageReleases): WheelIndex {
    serverRule.server.dispatcher = PyPiHost(mapOf(pathOf(packageName) to indexDocument(releases)))
    return indexUnderTest()
  }

  /** A name PyPI has never heard of answers 404, which is the same silence as a typo deserves. */
  @Test
  fun aNamePyPiDoesNotListIsNull() = runTest {
    serverRule.server.dispatcher = PyPiHost(emptyMap())

    assertNull(indexUnderTest().releasesOf("duckdbzz"))
  }

  @Test
  fun aHostThatCannotBeReachedIsNull() = runTest {
    val index = indexUnderTest()
    serverRule.server.close()

    assertNull(index.releasesOf("duckdb"))
  }

  /** PEP 503's spelling, because a requirement pip echoes keeps the one it was typed with. */
  @Test
  fun theRequestNamesThePackageTheWayPyPiDoes() = runTest {
    serverRule.server.dispatcher = PyPiHost(mapOf(pathOf("scikit-learn") to indexDocument(duckdb)))

    indexUnderTest().releasesOf("Scikit_Learn")

    assertEquals("/pypi/scikit-learn/json", serverRule.server.takeRequest().url.encodedPath)
  }

  /** An sdist is a file PyPI lists and a Space's pip cannot use, so the model keeps wheels alone. */
  @Test
  fun aSourceDistributionIsNotAWheel() = runTest {
    val document = """
      {"info": {"version": "2.0.1"},
       "releases": {"2.0.1": [{"filename": "duckdb-2.0.1.tar.gz"}, {"filename": "duckdb-2.0.1-cp312-abi3-musllinux_1_2_aarch64.whl"}]}}
    """.trimIndent()
    serverRule.server.dispatcher = PyPiHost(mapOf(pathOf("duckdb") to document))

    val releases = indexUnderTest().releasesOf("duckdb")

    assertEquals(
      listOf("duckdb-2.0.1-cp312-abi3-musllinux_1_2_aarch64.whl"),
      releases?.wheelNamesByVersion?.get("2.0.1"),
    )
  }

  @Test
  fun aBodyThatIsNotTheIndexIsNull() = runTest {
    serverRule.server.dispatcher = PyPiHost(mapOf(pathOf("duckdb") to "<html>502 Bad Gateway</html>"))

    assertNull(indexUnderTest().releasesOf("duckdb"))
  }

  /** The cap is room to answer in, so only a body that is not an index can reach it. */
  @Test
  fun aBodyOverTheCapIsNull() = runTest {
    val oversized = "x".repeat(WheelIndex.MAX_INDEX_BYTES + 1)
    serverRule.server.dispatcher = PyPiHost(mapOf(pathOf("duckdb") to oversized))

    assertNull(indexUnderTest().releasesOf("duckdb"))
  }

  private fun indexUnderTest() = PyPiWheelIndex(
    OkHttpClient(),
    baseUrl = serverRule.server.url("/pypi").toString(),
    ioDispatcher = Dispatchers.IO,
  )

  private fun pathOf(packageName: String): String = "/pypi/${normalizedPackageName(packageName)}/json"

  /** PyPI's own shape, as far as this app reads it: `info.version`, and `releases` of files by name. */
  private fun indexDocument(releases: PackageReleases): String = buildJsonObject {
    put("info", buildJsonObject { put("version", releases.latestVersion) })
    put(
      "releases",
      buildJsonObject {
        releases.wheelNamesByVersion.forEach { (version, names) ->
          put(version, buildJsonArray { names.forEach { add(buildJsonObject { put("filename", it) }) } })
        }
      },
    )
  }.toString()

  /** Serves each path's document; 404 otherwise, which is what PyPI answers for an unknown name. */
  private class PyPiHost(private val documentByPath: Map<String, String>) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
      val document = documentByPath[request.url.encodedPath] ?: return MockResponse.Builder().code(404).build()
      return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(document).build()
    }
  }
}
