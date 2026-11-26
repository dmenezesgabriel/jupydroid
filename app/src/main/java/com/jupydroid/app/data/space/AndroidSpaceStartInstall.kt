package com.jupydroid.app.data.space

import android.content.Context
import com.jupydroid.app.domain.model.BASE_SPACE

/**
 * What the Space [id]'s start installs before its server: base's versioned image
 * ([BaseSpaceInstaller]), or, for any other Space, the image only where its rootfs is missing -
 * which is how a restore leaves it ([SpaceImageInstaller.extractIfAbsent]) - and then the gnu
 * interpreter payload ([SpaceGnuImageInstaller.ensureInstalled]), which every Space gains
 * additively: an existing one keeps its image and gets the second interpreter over it
 * (ADR-0166). The kernelspec, the gnu pip.conf and the `jupydroid-env` helper are rewritten on
 * every start, not only at extract: a Space whose image predates the glibc kernel still gets
 * today's spec, and one whose APK update carried a newer helper script gets it too.
 *
 * ```
 * SpaceServer(space, installSpace = installOnStartOf(context, space.id), …)
 * ```
 */
internal fun installOnStartOf(context: Context, id: String): suspend () -> Unit {
  val spaceDir = spaceDirIn(spacesDir(context), id)
  val installImage: suspend () -> Unit = if (id == BASE_SPACE.id) {
    { baseSpaceInstaller(context).installIfNeeded() }
  } else {
    { spaceImageInstaller(context).extractIfAbsent(spaceDir) }
  }
  return {
    installImage()
    gnuSpaceImageInstaller(context).ensureInstalled(spaceDir)
    GnuPipConfig.ensure(spaceDir)
    SpaceKernelSpec.install(spaceDir)
    JupydroidEnvHelper.install(spaceDir, openAsset = { context.assets.open(it) })
  }
}
