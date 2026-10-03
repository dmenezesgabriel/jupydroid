@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.TerminalSettings
import com.jupydroid.app.domain.port.TerminalSettingsRepository
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

/** The file the settings live in, inside [stateDir] - beside [EDITOR_SETTINGS_FILE_NAME], not merged with it. */
internal const val TERMINAL_SETTINGS_FILE_NAME = "terminal-settings.json"

/**
 * The app-wide terminal preferences, held in [stateDir] - modelled on [DiskEditorSettingsRepository]
 * beside it, down to why: `stateDir` is `filesDir`, never the workspace, because the workspace is
 * the kernel's `HOME` and a bookkeeping file there would show up in the explorer's own listing and
 * in an `ls` from a cell.
 *
 * ```
 * DiskTerminalSettingsRepository(context.filesDir)
 * ```
 */
class DiskTerminalSettingsRepository(
  private val stateDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TerminalSettingsRepository {
  private val settings = MutableStateFlow(loadValidated())

  /**
   * One write at a time, and the publish lands after it - the flow only repeats what the file
   * holds. A `writeText` the filesystem refuses must not broadcast a value the next launch never
   * kept, and two writes racing on `applicationScope` must not leave the file and the flow
   * disagreeing about which update was last.
   */
  private val writeMutex = Mutex()

  override fun observeSettings(): Flow<TerminalSettings> = settings

  override suspend fun update(settings: TerminalSettings) = withContext(ioDispatcher) {
    writeMutex.withLock {
      stateDir.mkdirs()
      storeFile().writeText(json.encodeToString(TerminalSettings.serializer(), settings))
      this@DiskTerminalSettingsRepository.settings.value = settings
    }
  }

  /**
   * A store that cannot be parsed reads as the default. This runs before the first screen draws,
   * the same guard [DiskEditorSettingsRepository.loadValidated] carries for the same reason.
   */
  private fun loadValidated(): TerminalSettings = try {
    json.decodeFromString(TerminalSettings.serializer(), storeFile().readText())
  } catch (failure: SerializationException) {
    TerminalSettings()
  } catch (failure: java.io.IOException) {
    TerminalSettings()
  }

  private fun storeFile() = File(stateDir, TERMINAL_SETTINGS_FILE_NAME)
}
