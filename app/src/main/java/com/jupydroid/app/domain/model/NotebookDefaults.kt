package com.jupydroid.app.domain.model

/**
 * The kernel a new notebook is created against when nothing is being chosen.
 *
 * A Jupyter server decides what a *new* notebook runs from its kernelspec list; the server this
 * app ships has one kernel, `python3`. Shared by every screen that creates a notebook, so the id
 * is not carried as a private copy per screen - `HomeViewModel` had one until the explorer grew a
 * `createNotebook` of its own.
 */
const val DEFAULT_KERNEL_ID = "python3"

/**
 * The base name of a brand-new notebook.
 *
 * `new_untitled` supplies `Untitled` for `type: "notebook"` (`services/contents/manager.py`),
 * and `increment_filename` turns a taken one into `Untitled1`, the counter on the stem. Callers
 * pass this and let the repository - like the server - do the incrementing.
 */
const val UNTITLED_NOTEBOOK = "Untitled"
