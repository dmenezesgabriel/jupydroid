package com.jupydroid.app.domain.model

/** A kernel the user can run a notebook against, as advertised by a [Space]. */
data class KernelSpec(val id: String, val displayName: String)

/**
 * A Space: the directory `files/spaces/<id>/` holding its own venv, kernelspecs and
 * notebooks (FEASIBILITY.md §4a). Named after SageMaker Studio's term rather than
 * "environment", which would collide with the Python environments living inside one.
 * The base Space is bundled with the app; user-created Spaces arrive in Step 17.
 */
data class Space(val id: String, val name: String, val kernels: List<KernelSpec>)

/**
 * The one bundled Space - `files/spaces/base` - that every document runs against until P3
 * introduces user-created ones. It used to be declared twice, as a private companion in both
 * `NotebookKernel` and `BaseSpaceServer`; the launcher's standalone console (P6d) is the third
 * answerer of "which Space", which is where one home stops being a preference.
 */
val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())
