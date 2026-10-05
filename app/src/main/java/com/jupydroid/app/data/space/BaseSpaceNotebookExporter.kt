package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ExportFormat
import com.jupydroid.app.domain.port.ExportedDocument
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.ServerConnection

/**
 * The exporter for the base Space's server, brought up on first use.
 *
 * The kernel gateway's shape, one layer thinner: there is no session to keep track of here, so
 * [PerRunningServer] is the whole of it.
 *
 * **Both calls start the Space if it is not up**, which is deliberate and is the caller's cost to
 * manage rather than this class's to hide. nbconvert runs *in* the Space; there is no answer to
 * give without one. What that forbids is asking [formats] to draw a menu that has not been asked
 * for - a dropdown that booted a runtime to populate itself would cost seconds on a tap. The
 * reference has no such problem and so no such rule: its server is always up, and it caches the
 * list on the first notebook (`packages/notebook-extension/src/index.ts:855-890`).
 *
 * ```
 * BaseSpaceNotebookExporter(spaceServers(context, scope)!!.server(BASE_SPACE.id)) { OkHttpNotebookExporter(it) }
 * ```
 */
class BaseSpaceNotebookExporter(server: SpaceServer, exporterFor: (ServerConnection) -> NotebookExporter) :
  NotebookExporter {
  private val live = PerRunningServer(server, exporterFor)

  override suspend fun formats(): List<ExportFormat> = live.get().formats()

  override suspend fun export(documentPath: String, format: String): ExportedDocument =
    live.get().export(documentPath, format)
}
