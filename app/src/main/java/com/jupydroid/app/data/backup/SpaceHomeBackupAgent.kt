package com.jupydroid.app.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.jupydroid.app.data.space.LogcatSpaceLifecycleLog
import com.jupydroid.app.data.space.SpaceLifecycleLog
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * What the platform asks for a backup: [SpaceHomeBackupScope], and nothing else of `filesDir`.
 *
 * With `android:fullBackupOnly="true"` the platform calls [onFullBackup] and never the key-value
 * pair, so [onBackup] and [onRestore] have nothing to do. [onRestoreFile] keeps the platform's own
 * path: with no `android:dataExtractionRules` declared it restores every file to the path it was
 * backed up from (`isFileEligibleForRestore`,
 * `platform/frameworks/base@6e47c707 core/java/android/app/backup/BackupAgent.java`). A rules file
 * would add nothing here - the overridden [onFullBackup] never reads it - and an `<include>` in it
 * would drop restored files instead. What it changes is the date; see [restoredModifiedMillis].
 *
 * The backup carries sign-ins a program wrote under a home (opencode's `auth.json`), so it goes to
 * the cloud only end-to-end encrypted, which is what `disableIfNoEncryptionCapabilities` does for
 * an app without an agent (https://developer.android.com/identity/data/autobackup); see [mayCarrySignIns].
 *
 * ```
 * <application android:backupAgent=".data.backup.SpaceHomeBackupAgent" android:fullBackupOnly="true">
 * ```
 */
class SpaceHomeBackupAgent(private val log: SpaceLifecycleLog = LogcatSpaceLifecycleLog) : BackupAgent() {
  override fun onFullBackup(data: FullBackupDataOutput) {
    if (!mayCarrySignIns(data.transportFlags)) {
      log.reached("backup-refused", mapOf("transportFlags" to data.transportFlags.toString()))
      return
    }
    val files = SpaceHomeBackupScope(filesDir).filesToBackUp()
    files.forEach { fullBackupFile(it, data) }
    log.reached(
      "backup-written",
      mapOf("files" to files.size.toString(), "bytes" to files.sumOf { it.length() }.toString()),
    )
  }

  override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) =
    Unit

  override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

  override fun onRestoreFile(
    data: ParcelFileDescriptor,
    size: Long,
    destination: File,
    type: Int,
    mode: Long,
    mtime: Long,
  ) = super.onRestoreFile(data, size, destination, type, mode, restoredModifiedMillis(mtime))
}

/**
 * The date to give a restored file whose tar header says [tarMtimeSeconds].
 *
 * The platform's tar reader hands `onRestoreFile` the header's mtime, which is seconds
 * (`platform/frameworks/base@6e47c707 services/backup/java/com/android/server/backup/utils/TarBackupReader.java`),
 * and its default passes it to `File.setLastModified`, which takes milliseconds
 * (`core/java/android/app/backup/FullBackup.java`, `restoreFile`). Unconverted, every restored
 * file is dated 1970-01-21.
 *
 * ```
 * super.onRestoreFile(data, size, destination, type, mode, restoredModifiedMillis(mtime))
 * ```
 */
internal fun restoredModifiedMillis(tarMtimeSeconds: Long): Long = TimeUnit.SECONDS.toMillis(tarMtimeSeconds)

/**
 * Whether a transport with [transportFlags] may be handed the homes: one that encrypts with the
 * device's lock screen secret, or a device-to-device transfer, which never reaches a server.
 *
 * ```
 * if (!mayCarrySignIns(data.transportFlags)) return
 * ```
 */
internal fun mayCarrySignIns(transportFlags: Int): Boolean = transportFlags and SAFE_TRANSPORT_FLAGS != 0

private const val SAFE_TRANSPORT_FLAGS =
  BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED or BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER
