@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ThemeSettings
import com.jupydroid.app.domain.port.ThemeSettingsRepository
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

/** The file the settings live in, inside [stateDir] - beside the editor's, the terminal's and the explorer's. */
internal const val THEME_SETTINGS_FILE_NAME = "theme-settings.json"

/**
 * The app-wide theme preference, held in [stateDir] - modelled on [DiskExplorerSettingsRepository]
 * beside it, down to why: `stateDir` is `filesDir`, never the workspace, because the workspace is
 * the kernel's `HOME` and a bookkeeping file there would show up in the explorer's own listing and
 * in an `ls` from a cell.
 *
 * ```
 * DiskThemeSettingsRepository(context.filesDir)
 * ```
 */
class DiskThemeSettingsRepository(
  private val stateDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ThemeSettingsRepository {
  private val settings = MutableStateFlow(loadValidated())

  /**
   * One write at a time, and the publish lands after it - the flow only repeats what the file
   * holds. A write the filesystem refuses must not broadcast a value the next launch never
   * kept, and two writes racing on `applicationScope` must not leave the file and the flow
   * disagreeing about which update was last.
   */
  private val writeMutex = Mutex()

  override fun observeSettings(): Flow<ThemeSettings> = settings

  override suspend fun update(settings: ThemeSettings) = withContext(ioDispatcher) {
    writeMutex.withLock {
      stateDir.mkdirs()
      storeFile().writeTextAtomically(json.encodeToString(ThemeSettings.serializer(), settings))
      this@DiskThemeSettingsRepository.settings.value = settings
    }
  }

  /** A store that cannot be parsed reads as the default, the same guard every disk store here carries. */
  private fun loadValidated(): ThemeSettings = try {
    json.decodeFromString(ThemeSettings.serializer(), storeFile().readText())
  } catch (failure: SerializationException) {
    ThemeSettings()
  } catch (failure: java.io.IOException) {
    ThemeSettings()
  }

  private fun storeFile() = File(stateDir, THEME_SETTINGS_FILE_NAME)
}
