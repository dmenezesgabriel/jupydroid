package com.jupydroid.app.domain.port

/**
 * The name is taken - the one refusal a caller can offer to resolve.
 *
 * Its own type because the caller branches on it, exactly as JupyterLab branches on the status
 * code: `if (error.response.status !== 409) { throw error }`, and only a 409 becomes an offer to
 * overwrite. An invalid name is not offered anything; it is simply not a name.
 *
 * One type for both ports. The workspace and the notebook repository write into the same tree and
 * the same server answers both with 409, so a second exception would be two answers to one
 * question - and the dialog that asks "already exists, overwrite?" would have to know which.
 *
 * ```
 * val taken = if (failure is FileNameTakenException) name else null
 * ```
 */
class FileNameTakenException(name: String) : IllegalStateException("'$name' already exists in that folder")
