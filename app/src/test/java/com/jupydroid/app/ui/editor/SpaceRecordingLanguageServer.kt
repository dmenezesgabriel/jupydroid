package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageCapabilities
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageLocation
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import com.jupydroid.app.domain.port.LanguageWorkspaceEdit

/** A Space that is not base, for the asks that must name the entered Space and never base's. */
internal val THESIS_SPACE = Space(id = "space-1", name = "Thesis", kernels = BASE_SPACE.kernels)

/**
 * Any [LanguageServerPort], with the id of the Space each document call named recorded in
 * [spaceIds] - which `pylsp` the ask would reach, since `StdioLanguageServer` runs one per Space.
 *
 * ```
 * val server = SpaceRecordingLanguageServer(RecordingHoverServer())
 * NotebookHover(session, server, NotebookLanguageDocument(server, THESIS_SPACE) { "Analysis.ipynb" })
 * ```
 */
internal class SpaceRecordingLanguageServer(private val answering: LanguageServerPort) :
  LanguageServerPort by answering {
  val spaceIds = mutableListOf<String>()

  override suspend fun capabilities(space: Space): LanguageCapabilities =
    answering.capabilities(space).also { spaceIds += space.id }

  override suspend fun openDocument(space: Space, uri: String, languageId: String, text: String) =
    answering.openDocument(space, uri, languageId, text).also { spaceIds += space.id }

  override suspend fun changeDocument(space: Space, uri: String, text: String) =
    answering.changeDocument(space, uri, text).also { spaceIds += space.id }

  override suspend fun closeDocument(space: Space, uri: String) =
    answering.closeDocument(space, uri).also { spaceIds += space.id }

  override suspend fun completion(space: Space, uri: String, position: LanguagePosition): List<LanguageCompletion> =
    answering.completion(space, uri, position).also { spaceIds += space.id }

  override suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover? =
    answering.hover(space, uri, position).also { spaceIds += space.id }

  override suspend fun signatureHelp(space: Space, uri: String, position: LanguagePosition): LanguageSignatureHelp? =
    answering.signatureHelp(space, uri, position).also { spaceIds += space.id }

  override suspend fun definition(space: Space, uri: String, position: LanguagePosition): List<LanguageLocation> =
    answering.definition(space, uri, position).also { spaceIds += space.id }

  override suspend fun rangeFormatting(
    space: Space,
    uri: String,
    range: LanguageRange,
    options: LanguageFormatOptions,
  ): List<LanguageEdit> = answering.rangeFormatting(space, uri, range, options).also { spaceIds += space.id }

  override suspend fun rename(
    space: Space,
    uri: String,
    position: LanguagePosition,
    newName: String,
  ): LanguageWorkspaceEdit = answering.rename(space, uri, position, newName).also { spaceIds += space.id }
}
