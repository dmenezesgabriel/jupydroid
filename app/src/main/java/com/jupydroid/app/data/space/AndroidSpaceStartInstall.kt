package com.jupydroid.app.data.space

import android.content.Context
import com.jupydroid.app.domain.model.BASE_SPACE

/**
 * What the Space [id]'s start installs before its server: base's versioned image
 * ([BaseSpaceInstaller]), or, for any other Space, the image only where its rootfs is missing -
 * which is how a restore leaves it ([SpaceImageInstaller.extractIfAbsent]) - and then the gnu
 * interpreter payload ([SpaceGnuImageInstaller.ensureInstalled]), which every Space gains
 * additively: an existing one keeps its image and gets the second interpreter over it
 * (ADR-0166).
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
  }
}
