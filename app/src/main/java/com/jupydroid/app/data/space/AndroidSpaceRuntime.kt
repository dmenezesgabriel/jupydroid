package com.jupydroid.app.data.space

import android.content.Context
import android.net.ConnectivityManager
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Space
import java.io.File

/** Command line and environment for the installed base Space's interpreter. */
fun baseSpaceRuntime(context: Context): SpaceRuntime = spaceRuntime(context, BASE_SPACE)

/**
 * Command line and environment for [space]: its own directory for everything it writes, base's
 * rootfs for the interpreter, stdlib and shell. For `base` the two are one directory.
 */
fun spaceRuntime(context: Context, space: Space): SpaceRuntime = SpaceRuntime(
  spaceDir = spaceDirIn(spacesDir(context), space.id),
  nativeInterpreter = File(context.applicationInfo.nativeLibraryDir, "libpython3.so"),
  dnsServers = androidDnsServerSource(context),
  rootfsDir = baseSpaceDir(context),
)

/**
 * The device's current DNS servers, read fresh on every call rather than cached: they can
 * legitimately differ across networks (home Wi-Fi vs. mobile data), and [SpaceRuntime] reads
 * this on every process launch rather than once at install.
 */
fun androidDnsServerSource(context: Context): DnsServerSource = DnsServerSource {
  val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
  val link = connectivityManager?.getLinkProperties(connectivityManager.activeNetwork)
  link?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
}
