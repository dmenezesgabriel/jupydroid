package com.jupydroid.app.data.space

import com.jupydroid.app.data.disk.writeTextAtomically
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * The Space's `jupydroid-env` helper - the script asset the terminal runs by name and
 * `SpaceShell` runs for the app's create, one file so both routes run the same bytes
 * (`app/src/main/assets/space-tools/jupydroid-env.sh`).
 *
 * Written on every Space start beside the kernelspec rewrite, so an APK update refreshes every
 * Space's copy the way it refreshes the spec - ADR-0089's atomic write, and POSIX 0755 because
 * the terminal execs it.
 *
 * ```
 * JupydroidEnvHelper.install(spaceDirIn(spacesDir, space.id), openAsset = context.assets::open)
 * ```
 */
object JupydroidEnvHelper {
  private const val HELPER_PATH = "usr/local/bin/jupydroid-env"
  private const val SCRIPT_ASSET = "space-tools/jupydroid-env.sh"

  /** Writes [spaceDir]'s `jupydroid-env` executable - every call, like the kernelspec rewrite. */
  suspend fun install(
    spaceDir: File,
    openAsset: (String) -> InputStream,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  ) = withContext(ioDispatcher) {
    val helper = File(spaceDir, HELPER_PATH)
    helper.parentFile?.mkdirs()
    helper.writeTextAtomically(scriptOf(openAsset))
    Files.setPosixFilePermissions(helper.toPath(), HELPER_MODE)
  }

  /** The helper's script bytes as the APK ships them; [openAsset] is `AssetManager.open`, injected for tests. */
  private fun scriptOf(openAsset: (String) -> InputStream): String = try {
    openAsset(SCRIPT_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
  } catch (failure: IOException) {
    throw IllegalStateException(
      "helper script asset '$SCRIPT_ASSET' is not in the APK; the create's one script ships beside it",
      failure,
    )
  }

  private val HELPER_MODE = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_EXECUTE,
    PosixFilePermission.GROUP_READ,
    PosixFilePermission.GROUP_EXECUTE,
    PosixFilePermission.OTHERS_READ,
    PosixFilePermission.OTHERS_EXECUTE,
  )
}
