package com.jupydroid.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.backup.SpaceHomeBackupAgent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Who the platform asks for a backup, read back from the package it parsed rather than the manifest source. */
@RunWith(AndroidJUnit4::class)
class SpaceHomeBackupAgentTest {
  /**
   * Without an agent the platform backs up all of `filesDir`, rootfs included, which is past Auto
   * Backup's 25 MB quota, so nothing would be backed up at all
   * (https://developer.android.com/identity/data/autobackup).
   *
   * `fullBackupOnly` is not asserted here: `dumpsys package` on SM-X216B prints it for no package.
   * Without it the platform calls the agent's key-value `onBackup`, which writes nothing, so a
   * `bmgr backupnow` that logs the agent's `backup-written` line is what shows it took.
   */
  @Test
  fun thePlatformAsksTheSpaceHomeAgentForABackup() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(SpaceHomeBackupAgent::class.java.name, context.applicationInfo.backupAgentName)
  }
}
