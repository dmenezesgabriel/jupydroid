package com.jupydroid.app.domain.model

/** A kernel the user can run a notebook against, as advertised by a [Space]. */
data class KernelSpec(
  val id: String,
  val displayName: String,
)

/**
 * A Space: the directory `files/spaces/<id>/` holding its own venv, kernelspecs and
 * notebooks (FEASIBILITY.md §4a). Named after SageMaker Studio's term rather than
 * "environment", which would collide with the Python environments living inside one.
 * The base Space is bundled with the app; user-created Spaces arrive in Step 17.
 */
data class Space(
  val id: String,
  val name: String,
  val kernels: List<KernelSpec>,
)
