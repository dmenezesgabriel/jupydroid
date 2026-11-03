package com.jupydroid.app.data.asset

import com.jupydroid.app.domain.model.LicenceDocument
import com.jupydroid.app.domain.port.LicenceDocuments
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/** The directory inside the APK's assets that the `bundleLicences` task in `app/build.gradle.kts` fills. */
private const val LICENCES_ASSET_DIR = "licences"

/**
 * The licence files the build copied into the APK's assets from the repository root, so the text
 * shown is the text that was in the tree the APK was built from.
 *
 * [openAsset] is `AssetManager.open` and [listAssets] is `AssetManager.list`; both are injected so
 * the read is testable without a device. The documents follow the order a reader meets them: the
 * app's own licence and notices, the libraries it links, then the rootfs listing and source offer.
 *
 * ```
 * AssetLicenceDocuments(context.assets::open, { path -> context.assets.list(path).orEmpty().asList() })
 * ```
 */
class AssetLicenceDocuments(
  private val openAsset: (String) -> InputStream,
  private val listAssets: (String) -> List<String>,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LicenceDocuments {
  private val gradleLibraries = GradleLibraryLicences(::readText, listAssets)

  override suspend fun documents(): List<LicenceDocument> = withContext(ioDispatcher) {
    listOf(
      repositoryFile("Licence", "LICENSE"),
      repositoryFile("Notices", "NOTICE"),
      LicenceDocument("Gradle libraries", gradleLibraries.render()),
      repositoryFile("Third-party software", "THIRD_PARTY.md"),
      repositoryFile("Source offer", "SOURCE_OFFER.md"),
    )
  }

  private fun repositoryFile(title: String, fileName: String): LicenceDocument =
    LicenceDocument(title, read("$LICENCES_ASSET_DIR/$fileName"), isMarkdown = fileName.endsWith(".md"))

  private fun readText(path: String): String = openAsset(path).use { it.readBytes().toString(Charsets.UTF_8) }

  private fun read(path: String): String = try {
    readText(path)
  } catch (failure: IOException) {
    throw IllegalStateException(
      "licence asset '$path' is not in the APK; the bundleLicences task copies it from the repository root",
      failure,
    )
  }
}
