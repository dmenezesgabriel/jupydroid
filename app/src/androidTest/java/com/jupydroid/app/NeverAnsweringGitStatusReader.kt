package com.jupydroid.app

import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.RepositoryStatus
import kotlinx.coroutines.awaitCancellation

/**
 * A git status reader whose read never finishes, so a panel stays in the state it draws while reading.
 *
 * ```
 * GitExtension(readerFor = { NeverAnsweringGitStatusReader() })
 * ```
 */
class NeverAnsweringGitStatusReader : GitStatusReader {
  override suspend fun status(path: String): RepositoryStatus = awaitCancellation()
}
