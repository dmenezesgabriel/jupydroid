package com.jupydroid.app.data.space

import android.content.Context
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.SpaceRepository

/**
 * Every Space's server import read once into the page cache, base first ([ServerImportPreReader],
 * ADR-0081). Only base can be listed before its image is in place, since it installs on first use;
 * any other Space is listed only once its copy is renamed into place (`DiskSpaceRepository.create`).
 *
 * The Space's own variables are left out: the read warms the image's files, which they do not move.
 */
fun serverImportPreReader(
  context: Context,
  spaceRepository: SpaceRepository,
  servers: JupyterSpaceServers,
): ServerImportPreReader = ServerImportPreReader(
  spaceIds = { spaceRepository.spaces().map { it.id } },
  isInstalled = { id -> id != BASE_SPACE.id || baseSpaceInstaller(context).isInstalled() },
  running = servers.running,
  importServer = { id ->
    PythonRunner(spaceRuntime(context, spaceWithId(id))).run("-c", ServerImportPreReader.SERVER_IMPORT)
  },
  log = LogcatSpaceLifecycleLog,
)
