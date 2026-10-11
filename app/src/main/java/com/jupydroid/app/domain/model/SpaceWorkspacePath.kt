package com.jupydroid.app.domain.model

/**
 * The workspace as a Space's own processes see it: the kernel's `HOME`, the server's root, and so
 * what a traceback prints a notebook's file as (`com.jupydroid.app.data.space.SpaceDirs.inRoot`).
 * A Space is its own `/` under proot (ADR-0073), so the path is the same in every Space.
 */
const val SPACE_WORKSPACE_PATH = "/home/jupydroid"
