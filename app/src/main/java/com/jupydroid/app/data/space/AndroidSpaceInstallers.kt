package com.jupydroid.app.data.space

import android.content.Context

/**
 * The image installers wired to the app's assets, kept apart from the installers themselves
 * so those stay plain JVM classes unit tests can drive against a temp folder, with no
 * Context in sight.
 */

/**
 * Wires [BaseSpaceInstaller] to the Android app's own directories.
 */
fun baseSpaceInstaller(context: Context, abi: SpaceAbi = deviceAbi()): BaseSpaceInstaller = BaseSpaceInstaller(
  archive = { context.assets.open("spaces/base/rootfs-${abi.androidAbi}.tzst") },
  baseDir = baseSpaceDir(context),
)

/**
 * The bundled image a create copies into a new Space, wired to the app's assets.
 *
 * [deviceAbi] is resolved inside the lambda rather than here, so on an ABI with no rootfs
 * (x86_64) the refusal lands when a Space is *created* - as the act's on-screen failure - and
 * not while the composition root is being built.
 */
fun spaceImageInstaller(context: Context): SpaceImageInstaller = SpaceImageInstaller(
  archive = ArchiveSource { context.assets.open("spaces/base/rootfs-${deviceAbi().androidAbi}.tzst") },
)

/**
 * The gnu interpreter payload a Space gains beside its image, wired to the app's assets. Its
 * archive resolves [deviceAbi] inside the lambda for the same reason [spaceImageInstaller]'s
 * does: on an ABI with no payload the refusal lands when a Space starts, not while the
 * composition root is built.
 */
fun gnuSpaceImageInstaller(context: Context): SpaceGnuImageInstaller = SpaceGnuImageInstaller(
  archive = ArchiveSource { context.assets.open("spaces/gnu/gnu-${deviceAbi().androidAbi}.tzst") },
)
