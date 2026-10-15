package com.jupydroid.app.domain.port

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchingRefusalTest {
  @Test
  fun anAnswerComesBack() = assertEquals(Result.success(3), catchingRefusal { 3 })

  @Test
  fun aRefusalIsHeld() {
    val held = catchingRefusal { error("the disk said no") }

    assertEquals("the disk said no", held.exceptionOrNull()?.message)
  }

  /** A cancelled coroutine must still end, not carry on as if its call had merely been refused. */
  @Test
  fun aCancellationIsRethrown() {
    val thrown = runCatching { catchingRefusal { throw CancellationException("left the screen") } }

    assertTrue(thrown.exceptionOrNull() is CancellationException)
  }
}
