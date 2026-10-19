package com.jupydroid.app.data.backup

import android.app.backup.BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED
import android.app.backup.BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which transports a backup of the user's homes, sign-ins included, may be handed to. */
class SpaceHomeBackupTransportTest {
  /** A cloud backup encrypted with the device's lock screen secret is readable by no one else. */
  @Test
  fun anEncryptedCloudTransportMayCarrySignIns() {
    assertTrue(mayCarrySignIns(FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED))
  }

  /** A device-to-device transfer never reaches a server, so it runs with or without a lock screen. */
  @Test
  fun aDeviceToDeviceTransferMayCarrySignIns() {
    assertTrue(mayCarrySignIns(FLAG_DEVICE_TO_DEVICE_TRANSFER))
  }

  /** A cloud backup the device cannot encrypt carries nothing, as `disableIfNoEncryptionCapabilities` would. */
  @Test
  fun anUnencryptedCloudTransportCarriesNothing() {
    assertFalse(mayCarrySignIns(0))
  }
}
