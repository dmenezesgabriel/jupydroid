package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHarnessTest {
  private val opencode = CHAT_HARNESS_CATALOG.first { it.id == "opencode" }
  private val claude = CHAT_HARNESS_CATALOG.first { it.id == "claude-agent-acp" }

  /**
   * The two harnesses the tablet's probes ran, in the order the section lists them; a third
   * persona joins the catalog only after its own harness has run there.
   */
  @Test
  fun catalog_holdsExactlyTheMeasuredHarnesses() {
    assertEquals(listOf("opencode", "claude-agent-acp"), CHAT_HARNESS_CATALOG.map { it.id })
    assertEquals("OpenCode", opencode.name)
    assertEquals("opencode", opencode.executable)
    assertEquals("Claude", claude.name)
    assertEquals("claude-agent-acp", claude.executable)
  }

  /**
   * Each entry carries its persona's own requirement: opencode's `_check_opencode` refuses at
   * import unless `opencode --version` reads a version `>=1.0.0,<2`
   * (`usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/opencode.py` in the
   * shipped rootfs), while claude's checks `shutil.which` alone (`acp_personas/claude.py`).
   */
  @Test
  fun catalog_pinsEachPersonasOwnVersionRequirement() {
    val range = requireNotNull(opencode.requiredVersion)
    assertTrue(range.contains(HarnessVersion(1, 0, 0)))
    assertTrue(range.contains(HarnessVersion(1, 19, 3)))
    assertFalse(range.contains(HarnessVersion(0, 9, 9)))
    assertFalse(range.contains(HarnessVersion(2, 0, 0)))
    assertNull(claude.requiredVersion)
  }

  /**
   * What a person reads before installing: the harness at its pinned version and the size the
   * tablet measured - "a plain summary of what it adds", shipped as catalog data.
   */
  @Test
  fun catalog_statesWhatEachInstallAdds() {
    assertTrue(opencode.whatItAdds.contains("1.19.3"))
    assertTrue(opencode.whatItAdds.contains("MiB"))
    assertTrue(claude.whatItAdds.contains("0.97.0"))
    assertTrue(claude.whatItAdds.contains("MiB"))
  }

  /** The persona compares version tuples, so `1.9.9` precedes `1.19.3` where a string compare would not. */
  @Test
  fun harnessVersion_ordersNumericallyNotLexically() {
    assertTrue(HarnessVersion(1, 9, 9) < HarnessVersion(1, 19, 3))
    assertTrue(HarnessVersion(1, 99, 99) < HarnessVersion(2, 0, 0))
    assertTrue(HarnessVersion(2, 0, 0) > HarnessVersion(1, 19, 3))
  }

  /** What a person reads beside "Installed". */
  @Test
  fun harnessVersion_rendersAsMajorMinorPatch() {
    assertEquals("1.19.3", HarnessVersion(1, 19, 3).toString())
  }

  /** `>=1.0.0,<2` holds its inclusive floor and refuses everything from the exclusive ceiling up. */
  @Test
  fun harnessVersionRange_holdsAndRefusesItsBounds() {
    val range = HarnessVersionRange(HarnessVersion(1, 0, 0), HarnessVersion(2, 0, 0))
    assertTrue(range.contains(HarnessVersion(1, 0, 0)))
    assertTrue(range.contains(HarnessVersion(1, 999, 999)))
    assertFalse(range.contains(HarnessVersion(0, 9, 9)))
    assertFalse(range.contains(HarnessVersion(2, 0, 0)))
    assertFalse(range.contains(HarnessVersion(10, 0, 0)))
  }

  /** A range with no ceiling - the shape a `>=X` requirement takes - holds all the way up. */
  @Test
  fun harnessVersionRange_withoutACeilingHoldsAboveItsFloor() {
    val range = HarnessVersionRange(HarnessVersion(1, 0, 0))
    assertTrue(range.contains(HarnessVersion(1, 0, 0)))
    assertTrue(range.contains(HarnessVersion(99, 0, 0)))
    assertFalse(range.contains(HarnessVersion(0, 99, 99)))
  }
}
