package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a name that is already taken becomes a free one.
 *
 * Not invented: this is `FileContentsManager.increment_filename`, read out of the jupyter_server
 * that ships inside our own rootfs
 * (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/manager.py`):
 *
 * ```python
 * for i in itertools.count():
 *     insert_i = f"{insert}{i}" if i else ""
 *     name = f"{basename}{insert_i}{suffix}"
 *     if not self.exists(f"{path}/{name}"):
 *         break
 * ```
 *
 * So the first free name is the bare one, and the counter starts at 1 with **no separator** -
 * `Untitled1`, not `Untitled-1`. The server that serves this workspace names files this way, and
 * a second convention living beside it would be two answers to one question.
 */
class IncrementedNameTest {
  @Test
  fun aFreeNameIsUsedAsIs() {
    assertEquals("Untitled", incrementedName("Untitled") { false })
  }

  @Test
  fun aTakenNameCountsFromOne() {
    assertEquals("Untitled1", incrementedName("Untitled") { it == "Untitled" })
  }

  @Test
  fun itKeepsCountingPastTheOnesTaken() {
    val taken = setOf("Untitled", "Untitled1", "Untitled2")

    assertEquals("Untitled3", incrementedName("Untitled") { it in taken })
  }

  /** No hyphen, because the server does not use one - see the loop above. */
  @Test
  fun theCounterHasNoSeparator() {
    assertEquals("Report1", incrementedName("Report") { it == "Report" })
  }

  /** `insert` is the server's own parameter; a duplicate uses it, which is what X2 will want. */
  @Test
  fun anInsertGoesBeforeTheCounter() {
    assertEquals("Report-Copy1", incrementedName("Report", insert = "-Copy") { it in setOf("Report", "Report-Copy") })
  }
}
