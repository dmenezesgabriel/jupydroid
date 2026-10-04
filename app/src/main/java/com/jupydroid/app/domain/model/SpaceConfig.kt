package com.jupydroid.app.domain.model

import kotlinx.serialization.json.JsonObject
import java.time.Instant

/** The Python the bundled rootfs ships, stated once - `usr/bin/python3.12` is the same fact. */
const val BUNDLED_PYTHON_VERSION: String = "3.12"

/** What a user-created Space's directory is called, and the only id shape [nextSpaceId] mints. */
const val SPACE_ID_PREFIX: String = "space-"

/**
 * A Space's own record - what was configured about it, as against what its rootfs advertises.
 *
 * The split from [Space] is deliberate: [Space] is the identity a kernel, a server and a language
 * server are keyed by, and [kernels] there is read off the rootfs. This is the part a person
 * chose, and it is what `files/spaces/<id>/config.json` holds.
 *
 * **[id] is the directory's own name and is not in the file.** It cannot be, or the scan and the
 * file would be two sources for one fact and could disagree - which is why Winlator derives
 * `maxContainerId` from `loadContainers`' scan rather than storing it, and why its own `loadData`
 * has no `case "id"` for the id it writes (`container/ContainerManager.java:44-64`,
 * `container/Container.java:288`, `:314-388`). `Notebook`/`NotebookFile` split the same way.
 *
 * @property name what a row shows; a rename writes this and never moves the directory, so a
 *   notebook path, a running kernel and a server root all survive it.
 * @property created when the Space was made - the one field nothing but [SpaceRepository] writes.
 * @property requestedPackages what was asked for by name, which is not what is installed: a later
 *   and removes these, and pip's own answer is the record of what landed.
 * @property extra anything a later version of the app puts in `config.json` that this one does not
 *   model, kept verbatim so writing cannot shred it. `Container.extraData` is the same nested
 *   object (`Container.java:370-373`, `:306`) - an explicit field rather than nbformat's
 *   whole-object retention, because `config.json` is ours and is not an open format.
 */
data class SpaceConfig(
  val id: String,
  val name: String,
  val created: Instant,
  val pythonVersion: String = BUNDLED_PYTHON_VERSION,
  val requestedPackages: List<String> = emptyList(),
  val extra: JsonObject = JsonObject(emptyMap()),
)

/**
 * The id the next created Space takes: one above the highest number on disk.
 *
 * Winlator's own rule, and the reason nothing is stored - `loadContainers` recomputes
 * `maxContainerId` from the directories it finds, so a gap is never filled and deleting the
 * highest hands its id back (`container/ContainerManager.java:44-64`, `:98`). Ids with no number,
 * `base` included, cannot move the counter.
 *
 * ```
 * repository.spaces().nextSpaceId()   // "space-3", over base + space-1 + space-2
 * ```
 */
fun List<SpaceConfig>.nextSpaceId(): String = SPACE_ID_PREFIX + ((mapNotNull { it.number }.maxOrNull() ?: 0) + 1)

/**
 * The order a list of Spaces is shown in: the base Space, then the numbered ones, then the rest.
 *
 * Numbered by their **number**, so `space-2` precedes `space-10` where sorting the ids as text
 * would not - the same natural-order rule [inWorkspaceOrder] applies to file names, for the same
 * reason. The base Space leads because it is the one every other Space shares a rootfs with.
 * Unnumbered directories sort last, **by id** - the name can change and the id cannot.
 *
 * ```
 * repository.spaces().inSpaceOrder()
 * ```
 */
fun List<SpaceConfig>.inSpaceOrder(): List<SpaceConfig> = sortedWith(
  compareBy<SpaceConfig> { it.id != BASE_SPACE.id }
    .thenBy { it.number == null }
    .thenBy { it.number ?: 0 }
    .thenBy { it.id },
)

/** The number in a `space-<n>` id, or null for `base` and anything else nobody here minted. */
private val SpaceConfig.number: Int?
  get() = if (id.startsWith(SPACE_ID_PREFIX)) id.removePrefix(SPACE_ID_PREFIX).toIntOrNull() else null

/**
 * The record a Space directory has when it carries no readable `config.json`.
 *
 * Every installed device is in exactly this state for `base`: the rootfs extraction made the
 * directory and no code has ever written a config beside it, so a missing file is the norm rather
 * than corruption - and [created] is the directory's own mtime, the only honest answer available.
 * The base Space is the one id whose name the app already ships, so it keeps it.
 *
 * ```
 * defaultSpaceConfig(BASE_SPACE.id, Instant.ofEpochMilli(dir.lastModified()))   // name "Base"
 * ```
 */
fun defaultSpaceConfig(id: String, created: Instant): SpaceConfig =
  SpaceConfig(id = id, name = if (id == BASE_SPACE.id) BASE_SPACE.name else id, created = created)

/**
 * The Space [id] names, or a refusal saying what was asked for and what there was.
 *
 * One home so the fake and the disk adapter refuse identically - a caller that catches one has to
 * work against the other, which is what `SpaceRepositoryContract` holds them both to.
 *
 * ```
 * spaces().spaceWithId("space-1")
 * ```
 */
fun List<SpaceConfig>.spaceWithId(id: String): SpaceConfig = firstOrNull { it.id == id }
  ?: throw IllegalArgumentException("no Space with id '$id'; expected one of ${map { it.id }}")

/**
 * Refuses to delete the base Space, whatever asks.
 *
 * It holds the 268 MiB rootfs every other Space shares and every open document runs against, and
 * there is no undo. A screen can decline to draw the control; only this can stop a caller.
 *
 * ```
 * requireDeletableSpace("space-1")
 * ```
 */
fun requireDeletableSpace(id: String) {
  require(id != BASE_SPACE.id) {
    "cannot delete the bundled base Space '$id'; expected a user-created id like '${SPACE_ID_PREFIX}1'"
  }
}
