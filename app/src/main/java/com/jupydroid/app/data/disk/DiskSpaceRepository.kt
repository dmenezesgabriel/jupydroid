@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.BUNDLED_PYTHON_VERSION
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.domain.model.inSpaceOrder
import com.jupydroid.app.domain.model.nextSpaceId
import com.jupydroid.app.domain.model.requireDeletableSpace
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.SpaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.Clock
import java.time.Instant

/** The file a Space's own record lives in, inside the Space's directory. */
internal const val SPACE_CONFIG_FILE_NAME = "config.json"

/**
 * The Spaces under [spacesDir], read off the directory tree on every call.
 *
 * **The scan is the source of truth**, which is Winlator's model rather than a cache of it:
 * `loadContainers` enumerates the directories and derives every id from their names
 * (`container/ContainerManager.java:44-64`), so there is nothing to invalidate and nothing that can
 * disagree with what is installed. No `StateFlow` here for the same reason the reads are `suspend`:
 * a caller re-lists after a mutation, as the explorer already does for the workspace.
 *
 * A directory whose `config.json` is absent or unreadable is still a Space, read as
 * [defaultSpaceConfig] - the state every installed device is in for `base`, whose config nothing
 * has ever written.
 *
 * ```
 * DiskSpaceRepository(spacesDir(context))
 * ```
 */
class DiskSpaceRepository(
  private val spacesDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val clock: Clock = Clock.systemUTC(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpaceRepository {
  override suspend fun spaces(): List<SpaceConfig> = withContext(ioDispatcher) { scan() }

  override suspend fun create(name: String): SpaceConfig = withContext(ioDispatcher) {
    val created = SpaceConfig(id = scan().nextSpaceId(), name = name, created = clock.instant())
    val directory = File(spacesDir, created.id)
    require(directory.mkdirs()) { "could not create the Space directory '$directory'" }
    write(created)
    created
  }

  override suspend fun rename(id: String, name: String): SpaceConfig = withContext(ioDispatcher) {
    val renamed = scan().spaceWithId(id).copy(name = name)
    write(renamed)
    renamed
  }

  /**
   * The directory and everything in it - a Space's packages and documents live inside it.
   *
   * [requireDeletableSpace] runs before the id is even looked up, so the base Space is refused for
   * what it is rather than for whether it happens to be listed.
   */
  override suspend fun delete(id: String) = withContext(ioDispatcher) {
    requireDeletableSpace(id)
    val directory = File(spacesDir, scan().spaceWithId(id).id)
    require(directory.deleteRecursively()) { "could not delete the Space directory '$directory'" }
  }

  /** Directories only, dotfiles left out - `workspaceEntriesUnder`'s own two rules, for one tree. */
  private fun scan(): List<SpaceConfig> = (spacesDir.listFiles() ?: emptyArray())
    .filter { it.isDirectory && !it.name.startsWith(".") }
    .map { read(it) }
    .inSpaceOrder()

  /**
   * As much of the config as the file actually states, over [defaultSpaceConfig] for the rest.
   *
   * Field by field rather than all-or-nothing, and that is not a detail: a config written by a
   * later version of the app, or one hand-edited in a terminal, must not lose its *name* because
   * one field is missing - which is the same reason `EditorSettings` requires a default on every
   * new field of its own.
   */
  private fun read(directory: File): SpaceConfig {
    val default = defaultSpaceConfig(directory.name, Instant.ofEpochMilli(directory.lastModified()))
    return try {
      json.decodeFromString(StoredSpaceConfig.serializer(), File(directory, SPACE_CONFIG_FILE_NAME).readText())
        .toDomain(default)
    } catch (failure: SerializationException) {
      default
    } catch (failure: java.io.IOException) {
      default
    }
  }

  private fun write(config: SpaceConfig) {
    File(File(spacesDir, config.id), SPACE_CONFIG_FILE_NAME)
      .writeText(json.encodeToString(StoredSpaceConfig.serializer(), config.stored()))
  }
}

/**
 * The stored shape, kept apart from the domain type so the file format can move on its own.
 *
 * **No `id`**: it is the directory's name, and a second copy of it in the file could disagree with
 * the scan. The reference writes one and then ignores it on read for that reason
 * (`Container.java:288` against `:314-388`, whose key switch has no `case "id"`).
 * [created] is ISO-8601 text so the file is readable by eye and needs no `Instant` serializer.
 *
 * **Every field is optional**, the rule `EditorSettings` states for its own store: a config written
 * before a field existed has nothing to say about it, and a file that omits one must still yield
 * everything it does say.
 */
@Serializable
private data class StoredSpaceConfig(
  val name: String? = null,
  val created: String? = null,
  val pythonVersion: String = BUNDLED_PYTHON_VERSION,
  val requestedPackages: List<String> = emptyList(),
  val extra: JsonObject = JsonObject(emptyMap()),
)

private fun SpaceConfig.stored() = StoredSpaceConfig(
  name = name,
  created = created.toString(),
  pythonVersion = pythonVersion,
  requestedPackages = requestedPackages,
  extra = extra,
)

/** [default] answers for anything the file leaves out, a `created` nobody can parse included. */
private fun StoredSpaceConfig.toDomain(default: SpaceConfig) = default.copy(
  name = name ?: default.name,
  created = created?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: default.created,
  pythonVersion = pythonVersion,
  requestedPackages = requestedPackages,
  extra = extra,
)
