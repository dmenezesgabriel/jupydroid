package com.jupydroid.app.domain.model

/**
 * A kernel the user can run a notebook against, as advertised by a [Space].
 *
 * The fields are the kernel.json/gateway model's own names: `name` is the spec's id - the key
 * `kernelspecs` maps it under and what `kernel.name` sends back - `display_name` is what a row
 * shows (`KernelSpec.ISpecModel`, `packages/services/src/kernelspec/`); `language` is what the
 * picker's groups split on (`kernelOptions`, `sessioncontext.tsx:1666-1672`).
 */
data class KernelSpec(val name: String, val displayName: String, val language: String)

/**
 * A Space: the directory `files/spaces/<id>/` holding its own packages, kernelspecs and
 * notebooks. Named after SageMaker Studio's term rather than "environment", which would collide
 * with the Python environments living inside one.
 *
 * The id is the directory's own name and is deliberately not stored in the Space's `config.json`,
 * so the two cannot disagree - see ADR-0010. The base Space is bundled with the app.
 */
data class Space(val id: String, val name: String, val kernels: List<KernelSpec>)

/**
 * The one bundled Space - `files/spaces/base` - that every document runs against. It is declared
 * **here and only here**: it used to be a private companion in both `NotebookKernel` and
 * `SpaceServer`, and the launcher's standalone console is a third answerer of "which Space",
 * which is where one home stops being a preference.
 *
 * `kernels` names what the rootfs actually ships - `SpaceKernelSpec` writes that exact spec into
 * `usr/share/jupyter/kernels/python3` - so a device that cannot run a Space still has an honest
 * kernel picker (`data/fake/FakeKernelSpecs.kt`). A Space that can run answers from its disk while
 * stopped (`data/disk/DiskKernelSpecs.kt`), which also lists what was installed into it since.
 */
val BASE_SPACE = Space(
  id = "base",
  name = "Base",
  kernels = listOf(
    KernelSpec(name = DEFAULT_KERNEL_ID, displayName = DEFAULT_KERNEL_DISPLAY_NAME, language = "python"),
  ),
)

/**
 * The Space whose directory is named [id]. Every Space but base was copied from base's image when
 * it was created (`DiskSpaceRepository`), so it declares base's kernels; its name reads as its id.
 *
 * ```
 * spaceWithId("space-1").kernels   // base's python3
 * ```
 */
fun spaceWithId(id: String): Space = if (id == BASE_SPACE.id) BASE_SPACE else Space(id, id, BASE_SPACE.kernels)
