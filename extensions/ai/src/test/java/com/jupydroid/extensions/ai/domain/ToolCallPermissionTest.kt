package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** jupyter-chat-components@992c6fb `src/components/grouped-tool-calls.tsx`, `ToolCallRow`'s `isRejected`. */
class ToolCallPermissionTest {
  private val allow = PermissionOption(id = "once", name = "Allow once", rejects = false)
  private val reject = PermissionOption(id = "reject", name = "Reject", rejects = true)
  private val call = ToolCall(id = "c", title = "echo probe", status = ToolCallStatus.Completed)

  @Test
  fun aCallRejectedIsShownFailedWhateverItsStatus() {
    assertEquals(ToolCallStatus.Failed, call.copy(permission = ToolCallPermission.Resolved(reject)).shownStatus)
  }

  @Test
  fun aCallAllowedIsShownByItsStatus() {
    assertEquals(ToolCallStatus.Completed, call.copy(permission = ToolCallPermission.Resolved(allow)).shownStatus)
  }

  @Test
  fun aCallAwaitingApprovalIsShownRunning() {
    val pending = ToolCall(
      id = "c",
      title = "echo probe",
      status = ToolCallStatus.Running,
      permission = ToolCallPermission.Pending(sessionId = "s", options = listOf(allow, reject)),
    )

    assertEquals(ToolCallStatus.Running, pending.shownStatus)
  }
}
