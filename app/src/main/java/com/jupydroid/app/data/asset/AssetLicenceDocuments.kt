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

/** Title, then asset file name: the order a reader meets them, which is the order of the repository's own files. */
private val BUNDLED_DOCUMENTS = listOf(
  "Licence" to "LICENSE",
  "Notices" to "NOTICE",
  "Third-party software" to "THIRD_PARTY.md",
  "Source offer" to "SOURCE_OFFER.md",
)

/**
 * The licence files the build copied into the APK's assets from the repository root, so the text
 * shown is the text that was in the tree the APK was built from.
 *
 * [openAsset] is `AssetManager.open` and is injected so the read is testable without a device.
 *
 * ```
 * AssetLicenceDocuments(context.assets::open)
 * ```
 */
class AssetLicenceDocuments(
  private val openAsset: (String) -> InputStream,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LicenceDocuments {
  override suspend fun documents(): List<LicenceDocument> = withContext(ioDispatcher) {
    BUNDLED_DOCUMENTS.map { (title, fileName) -> LicenceDocument(title, read("$LICENCES_ASSET_DIR/$fileName")) }
  }

  private fun read(path: String): String = try {
    openAsset(path).use { it.readBytes().toString(Charsets.UTF_8) }
  } catch (failure: IOException) {
    throw IllegalStateException(
      "licence asset '$path' is not in the APK; the bundleLicences task copies it from the repository root",
      failure,
    )
  }
}
