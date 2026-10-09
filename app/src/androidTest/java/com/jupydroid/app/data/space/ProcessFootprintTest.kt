package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading a process's CPU time from `/proc/<pid>/stat`, on lines shaped as proc(5) documents them. */
class ProcessFootprintTest {
  @Test
  fun cpuTicksAreUserTimePlusSystemTime() {
    val stat = "4242 (python3) S 1 4242 4242 0 -1 4194560 900 0 0 0 151 37 0 0 20 0 3 0 812 0 0"

    assertEquals(188, cpuTicksOf(stat))
  }

  @Test
  fun aCommandWithSpacesAndParenthesesDoesNotShiftTheFields() {
    val stat = "77 (a (b) c) S 1 77 77 0 -1 4194560 900 0 0 0 5 6 0 0 20 0 1 0 812 0 0"

    assertEquals(11, cpuTicksOf(stat))
  }
}
