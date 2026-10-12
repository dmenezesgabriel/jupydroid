@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
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

/** The file the settings live in, inside [stateDir] - beside [RECENTS_FILE_NAME], not merged with it. */
internal const val EDITOR_SETTINGS_FILE_NAME = "editor-settings.json"

/**
 * The app-wide editor preferences, held in [stateDir] - modelled on [DiskRecentDocumentsRepository]
 * beside it, down to why: `stateDir` is `filesDir`, never the workspace, because the workspace is
 * the kernel's `HOME` and a bookkeeping file there would show up in the explorer's own listing and
 * in an `ls` from a cell.
 *
 * ```
 * DiskEditorSettingsRepository(context.filesDir)
 * ```
 */
class DiskEditorSettingsRepository(
  private val stateDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EditorSettingsRepository {
  private val settings = MutableStateFlow(loadValidated())

  /**
   * One write at a time, and the publish lands after it - the flow only repeats what the file
   * holds. A write the filesystem refuses must not broadcast a value the next launch never
   * kept, and two writes racing on `applicationScope` must not leave the file and the flow
   * disagreeing about which update was last.
   */
  private val writeMutex = Mutex()

  override fun observeSettings(): Flow<EditorSettings> = settings

  override suspend fun update(settings: EditorSettings) = withContext(ioDispatcher) {
    writeMutex.withLock {
      stateDir.mkdirs()
      storeFile().writeTextAtomically(json.encodeToString(EditorSettings.serializer(), settings))
      this@DiskEditorSettingsRepository.settings.value = settings
    }
  }

  /**
   * A store that cannot be parsed reads as the default. This runs before the first screen draws,
   * the same guard [DiskRecentDocumentsRepository.loadValidated] carries for the same reason.
   */
  private fun loadValidated(): EditorSettings = try {
    json.decodeFromString(EditorSettings.serializer(), storeFile().readText())
  } catch (failure: SerializationException) {
    EditorSettings()
  } catch (failure: java.io.IOException) {
    EditorSettings()
  }

  private fun storeFile() = File(stateDir, EDITOR_SETTINGS_FILE_NAME)
}
