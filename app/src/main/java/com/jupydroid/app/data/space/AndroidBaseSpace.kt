package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Wires [BaseSpaceInstaller] to the Android app's own directories.
 *
 * Kept apart from the installer so the installer stays a plain JVM class that unit
 * tests can drive against a temp folder, with no Context in sight.
 */
fun baseSpaceInstaller(context: Context, abi: SpaceAbi = deviceAbi()): BaseSpaceInstaller =
  BaseSpaceInstaller(
    archive = { context.assets.open("spaces/base/rootfs-${abi.androidAbi}.tgz") },
    baseDir = baseSpaceDir(context),
    nativeLoader = File(context.applicationInfo.nativeLibraryDir, "libloader.so"),
    nativeInterpreter = File(context.applicationInfo.nativeLibraryDir, "libpython3.so"),
    loaderLinkPath = abi.loaderLinkPath,
  )

/** Command line and environment for the installed base Space's interpreter. */
fun baseSpaceRuntime(context: Context): SpaceRuntime =
  SpaceRuntime(
    baseDir = baseSpaceDir(context),
    nativeInterpreter = File(context.applicationInfo.nativeLibraryDir, "libpython3.so"),
  )

fun baseSpaceDir(context: Context): File = File(context.filesDir, "spaces/base")

/** Runs jupyter_server out of the installed base Space, on loopback. */
fun baseSpaceServerLauncher(context: Context): JupyterServerLauncher =
  JupyterServerLauncher(
    runtime = baseSpaceRuntime(context),
    runtimeDir = File(baseSpaceDir(context), "run"),
  )

/**
 * The rootfs must match the ABI Android picked for the APK's native libraries, which
 * is the first supported one - not necessarily the only one the device can run.
 */
private fun deviceAbi(): SpaceAbi = SpaceAbi.of(Build.SUPPORTED_ABIS.first())
