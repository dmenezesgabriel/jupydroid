package com.jupydroid.app.data.backup

import com.jupydroid.app.data.disk.EDITOR_SETTINGS_FILE_NAME
import com.jupydroid.app.data.disk.EXPLORER_SETTINGS_FILE_NAME
import com.jupydroid.app.data.disk.LAUNCHER_SETTINGS_FILE_NAME
import com.jupydroid.app.data.disk.RECENTS_FILE_NAME
import com.jupydroid.app.data.disk.SPACE_CONFIG_FILE_NAME
import com.jupydroid.app.data.disk.TERMINAL_SETTINGS_FILE_NAME
import com.jupydroid.app.data.disk.THEME_SETTINGS_FILE_NAME
import com.jupydroid.app.data.space.notebookWorkspaceIn
import com.jupydroid.app.data.space.spacesDirIn
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * The files a backup of [filesDir] carries: what the user made, and nothing a reinstall rebuilds.
 *
 * That is each Space's home (its documents, and the sign-ins a program wrote under it), each
 * Space's `config.json`, and the app's own settings and recents. Never the rootfs, `pkgs`, `run`,
 * `tmp` or the rootfs stamp - the walk only enters the home, so they are out by construction.
 *
 * The filter is what makes a backup happen at all: Auto Backup's quota is 25 MB per app, and over
 * it "doesn't back up data to the cloud" - documents included
 * (https://developer.android.com/identity/data/autobackup). base's rootfs alone is far past it, a
 * numpy venv is too, and opencode's binary in `/usr/local` is 195 MB (ADR-0106). So a directory
 * holding `pyvenv.cfg` (the test `app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceEnvironments.kt`
 * applies, here at any depth) and `~/.cache` are left out: one `pip install` rebuilds them.
 *
 * A symlink is neither returned nor followed: it points at something the backup does not mean to
 * carry, often the rootfs.
 *
 * ```
 * SpaceHomeBackupScope(filesDir).filesToBackUp().forEach(::fullBackupFile)
 * ```
 */
class SpaceHomeBackupScope(private val filesDir: File) {
  /** Regular files only: an empty directory is recreated by `SpaceDirs.ensure()` and the workspace. */
  fun filesToBackUp(): List<File> = appStateFiles() + spaceDirs().flatMap(::spaceFiles)

  private fun appStateFiles(): List<File> = APP_STATE_FILE_NAMES.map { File(filesDir, it) }.filter(::isRegularFile)

  // Every directory under `spaces/` is a Space, `config.json` or not (ADR-0010).
  private fun spaceDirs(): List<File> =
    spacesDirIn(filesDir).listFiles().orEmpty().filter { it.isDirectory && !isSymlink(it) }

  private fun spaceFiles(spaceDir: File): List<File> =
    listOf(File(spaceDir, SPACE_CONFIG_FILE_NAME)).filter(::isRegularFile) + homeFiles(notebookWorkspaceIn(spaceDir))

  private fun homeFiles(home: File): List<File> {
    val cache = File(home, ".cache")
    return home.walkTopDown()
      .onEnter { dir -> !isSymlink(dir) && dir != cache && !File(dir, "pyvenv.cfg").isFile }
      .filter(::isRegularFile)
      .toList()
  }

  private fun isRegularFile(file: File): Boolean = Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)

  private fun isSymlink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

  private companion object {
    val APP_STATE_FILE_NAMES = listOf(
      RECENTS_FILE_NAME,
      THEME_SETTINGS_FILE_NAME,
      EDITOR_SETTINGS_FILE_NAME,
      TERMINAL_SETTINGS_FILE_NAME,
      EXPLORER_SETTINGS_FILE_NAME,
      LAUNCHER_SETTINGS_FILE_NAME,
    )
  }
}
