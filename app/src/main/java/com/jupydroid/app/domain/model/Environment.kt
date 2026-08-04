package com.jupydroid.app.domain.model

/** A kernel the user can run a notebook against, as advertised by an [Environment]. */
data class KernelSpec(
  val id: String,
  val displayName: String,
)

/**
 * A first-class environment: the directory `files/containers/<id>/` holding its own
 * venv, kernelspecs and notebooks (FEASIBILITY.md §4a). The base env is bundled with
 * the app; further environments are added in Step 17.
 */
data class Environment(
  val id: String,
  val name: String,
  val kernels: List<KernelSpec>,
)
