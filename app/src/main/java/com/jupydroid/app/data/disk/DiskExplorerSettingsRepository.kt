@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ExplorerSettings
import com.jupydroid.app.domain.port.ExplorerSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/** The file the settings live in, inside [stateDir] - beside the editor's and the terminal's, not merged. */
internal const val EXPLORER_SETTINGS_FILE_NAME = "explorer-settings.json"

/**
 * The app-wide explorer preferences, held in [stateDir] - modelled on
 * [DiskTerminalSettingsRepository] beside it, down to why: `stateDir` is `filesDir`, never the
 * workspace, because the workspace is the kernel's `HOME` and a bookkeeping file there would show
 * up in the explorer's own listing and in an `ls` from a cell.
 *
 * ```
 * DiskExplorerSettingsRepository(context.filesDir)
 * ```
 */
class DiskExplorerSettingsRepository(
  private val stateDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExplorerSettingsRepository {
  private val settings = MutableStateFlow(loadValidated())

  /**
   * One write at a time, and the publish lands after it - the flow only repeats what the file
   * holds. A write the filesystem refuses must not broadcast a value the next launch never
   * kept, and two writes racing on `applicationScope` must not leave the file and the flow
   * disagreeing about which update was last.
   */
  private val writeMutex = Mutex()

  override fun observeSettings(): Flow<ExplorerSettings> = settings

  override suspend fun update(settings: ExplorerSettings) = withContext(ioDispatcher) {
    writeMutex.withLock {
      stateDir.mkdirs()
      storeFile().writeTextAtomically(json.encodeToString(ExplorerSettings.serializer(), settings))
      this@DiskExplorerSettingsRepository.settings.value = settings
    }
  }

  /**
   * A store that cannot be parsed reads as the default. This runs before the first screen draws,
   * the same guard [DiskTerminalSettingsRepository.loadValidated] carries for the same reason.
   */
  private fun loadValidated(): ExplorerSettings = try {
    json.decodeFromString(ExplorerSettings.serializer(), storeFile().readText())
  } catch (failure: SerializationException) {
    ExplorerSettings()
  } catch (failure: java.io.IOException) {
    ExplorerSettings()
  }

  private fun storeFile() = File(stateDir, EXPLORER_SETTINGS_FILE_NAME)
}
