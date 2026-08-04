package com.jupydroid.app.data.space

import android.content.Context
import java.io.File

/**
 * Wires [BaseSpaceInstaller] to the Android app's own directories.
 *
 * Kept apart from the installer so the installer stays a plain JVM class that unit
 * tests can drive against a temp folder, with no Context in sight.
 */
fun baseSpaceInstaller(context: Context, abi: String = deviceAbi()): BaseSpaceInstaller =
  BaseSpaceInstaller(
    archive = { context.assets.open("spaces/base/rootfs-$abi.tgz") },
    baseDir = File(context.filesDir, "spaces/base"),
    nativeLoader = File(context.applicationInfo.nativeLibraryDir, "libld-linux.so"),
  )

/** The ABI the rootfs was built for; only x86_64 ships today (arm64-v8a is Step 8.7). */
private fun deviceAbi(): String = android.os.Build.SUPPORTED_ABIS.first()
