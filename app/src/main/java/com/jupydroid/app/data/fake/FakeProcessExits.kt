package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ProcessExit
import com.jupydroid.app.domain.port.ProcessExits

/**
 * Deaths held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default, so a device the platform
 * cannot be asked on, and every test, reports none unless told of some.
 *
 * ```
 * FakeProcessExits(listOf(ProcessExit(1L, pid = 7, reason = ProcessExitReason.ANR, description = "")))
 * ```
 */
class FakeProcessExits(private val exits: List<ProcessExit> = emptyList()) : ProcessExits {
  private var reportedUpTo = Long.MIN_VALUE

  override suspend fun unreported(): List<ProcessExit> = exits.filter { it.timestampMillis > reportedUpTo }

  override suspend fun markReported(exits: List<ProcessExit>) {
    val newest = exits.maxOfOrNull { it.timestampMillis } ?: return
    reportedUpTo = maxOf(reportedUpTo, newest)
  }
}
