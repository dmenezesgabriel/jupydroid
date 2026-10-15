package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Checkpoint
import com.jupydroid.app.domain.port.DocumentCheckpoints
import com.jupydroid.app.domain.port.WorkspaceRepository
import java.time.Instant

/**
 * Checkpoints held in memory over whatever [workspace] holds, for a container with no disk.
 *
 * One per path and under the disk's id, so a test written against this hands a restore what the
 * disk would. Each is made at its ordinal, so no clock is read.
 *
 * ```
 * FakeDocumentCheckpoints(FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "saved")))
 * ```
 */
class FakeDocumentCheckpoints(private val workspace: WorkspaceRepository = FakeWorkspaceRepository()) :
  DocumentCheckpoints {
  private val held = mutableMapOf<String, Pair<Checkpoint, ByteArray>>()
  private var made = 0L

  override suspend fun create(path: String): Checkpoint {
    val bytes = workspace.readBytes(path)
    return Checkpoint(CHECKPOINT_ID, Instant.ofEpochSecond(++made)).also { held[path] = it to bytes }
  }

  override suspend fun list(path: String): List<Checkpoint> = listOfNotNull(held[path]?.first)

  override suspend fun restore(path: String, id: String) {
    val (checkpoint, bytes) = requireNotNull(held[path]) { "no checkpoint for '$path' in the workspace" }
    require(checkpoint.id == id) { "no checkpoint '$id' for '$path'; it has '${checkpoint.id}'" }
    workspace.writeBytes(path, bytes)
  }

  private companion object {
    const val CHECKPOINT_ID = "checkpoint"
  }
}
