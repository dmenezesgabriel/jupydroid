package com.jupydroid.app.data.asset

import com.jupydroid.app.domain.model.ChatHarness
import com.jupydroid.app.domain.port.HarnessScripts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/** The directory inside the APK's assets holding one `<harness id>.sh` per catalog entry. */
private const val HARNESS_SCRIPTS_ASSET_DIR = "chat-harnesses"

/**
 * The install scripts shipped in the APK's assets, so the text the Settings section shows is the
 * text that was in the tree the APK was built from - and the same string `SpaceShell` runs.
 *
 * [openAsset] is `AssetManager.open`, injected so the read is testable without a device.
 *
 * ```
 * AssetHarnessScripts(context.assets::open)
 * ```
 */
class AssetHarnessScripts(
  private val openAsset: (String) -> InputStream,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HarnessScripts {
  override suspend fun installScript(harness: ChatHarness): String = withContext(ioDispatcher) {
    val path = "$HARNESS_SCRIPTS_ASSET_DIR/${harness.id}.sh"
    try {
      openAsset(path).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (failure: IOException) {
      throw IllegalStateException(
        "harness script asset '$path' is not in the APK; a catalog entry ships its script beside it",
        failure,
      )
    }
  }
}
