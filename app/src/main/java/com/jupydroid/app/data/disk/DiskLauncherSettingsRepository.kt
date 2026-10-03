@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.port.LauncherSettingsRepository
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

/** The file the settings live in, inside [stateDir] - beside the explorer's and the theme's, not merged. */
internal const val LAUNCHER_SETTINGS_FILE_NAME = "launcher-settings.json"

/**
 * The app-wide launcher bookkeeping, held in [stateDir] - modelled on
 * [DiskExplorerSettingsRepository] beside it, down to why: `stateDir` is `filesDir`, never the
 * workspace, because the workspace is the kernel's `HOME` and a bookkeeping file there would show
 * up in the explorer's own listing and in an `ls` from a cell.
 *
 * ```
 * DiskLauncherSettingsRepository(context.filesDir)
 * ```
 */
class DiskLauncherSettingsRepository(
  private val stateDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LauncherSettingsRepository {
  private val settings = MutableStateFlow(loadValidated())

  /**
   * One write at a time, and the publish lands after it - the flow only repeats what the file
   * holds. A `writeText` the filesystem refuses must not broadcast a value the next launch never
   * kept, and two writes racing on `applicationScope` must not leave the file and the flow
   * disagreeing about which update was last.
   */
  private val writeMutex = Mutex()

  override fun observeSettings(): Flow<LauncherSettings> = settings

  override suspend fun update(settings: LauncherSettings) = withContext(ioDispatcher) {
    writeMutex.withLock {
      stateDir.mkdirs()
      storeFile().writeText(json.encodeToString(LauncherSettings.serializer(), settings))
      this@DiskLauncherSettingsRepository.settings.value = settings
    }
  }

  /**
   * A store that cannot be parsed reads as the default. This runs before the first screen draws,
   * the same guard [DiskExplorerSettingsRepository.loadValidated] carries for the same reason.
   */
  private fun loadValidated(): LauncherSettings = try {
    json.decodeFromString(LauncherSettings.serializer(), storeFile().readText())
  } catch (failure: SerializationException) {
    LauncherSettings()
  } catch (failure: java.io.IOException) {
    LauncherSettings()
  }

  private fun storeFile() = File(stateDir, LAUNCHER_SETTINGS_FILE_NAME)
}
