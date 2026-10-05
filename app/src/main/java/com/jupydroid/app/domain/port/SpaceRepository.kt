package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.SpaceConfig

/**
 * The Spaces on this device: what is there, and how one is made, renamed or removed.
 *
 * A Space **is** a directory under `files/spaces/`, which is Winlator's own model - containers are
 * enumerated off disk and their ids derived from that scan, so nothing can drift out of step with
 * what is actually installed (`container/ContainerManager.java:44-64`). Nothing here starts a
 * process or reads a rootfs: a Space's kernels are [com.jupydroid.app.domain.port.KernelSpecs]'
 * question and its environment is `SpaceRuntime`'s.
 *
 * ```
 * val thesis = spaceRepository.create("Thesis")   // files/spaces/space-1/
 * spaceRepository.spaces()                        // base first, then space-1
 * ```
 */
interface SpaceRepository {
  /** Every Space on this device, base included, in [com.jupydroid.app.domain.model.inSpaceOrder]. */
  suspend fun spaces(): List<SpaceConfig>

  /**
   * Makes a Space called [name] and answers with its record.
   *
   * The id is minted by [com.jupydroid.app.domain.model.nextSpaceId], never from [name]: a rename
   * must not have to move a directory a kernel, a server root or an open document is standing in.
   * Two Spaces may share a name, as two of the reference's containers may.
   *
   * Making one is a ~400 MB image copy (~14 s on the tablet, `ProotRuntimeProbeDeviceTest`), so
   * [onProgress] is handed the fraction done: every emission below `1f` happens while the Space
   * is still unlisted - a half copy must never be mistaken for a Space - and the last emission
   * is `1f`, emitted once the scan could show it.
   */
  suspend fun create(name: String, onProgress: suspend (Float) -> Unit = {}): SpaceConfig

  /**
   * Renames the Space [id] to [name] and answers with its updated record.
   *
   * Writes the name and nothing else - above all not the directory, which keeps its id for life.
   *
   * @throws IllegalArgumentException when no Space has that id.
   */
  suspend fun rename(id: String, name: String): SpaceConfig

  /**
   * Removes the Space [id] and everything inside it.
   *
   * @throws IllegalArgumentException when no Space has that id, or when [id] is the base Space -
   *   it holds the rootfs every other Space shares, and there is no undo for it.
   */
  suspend fun delete(id: String)
}
