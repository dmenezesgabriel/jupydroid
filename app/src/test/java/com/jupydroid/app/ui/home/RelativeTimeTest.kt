package com.jupydroid.app.ui.home

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
  private val now = Instant.parse("2026-08-03T12:00:00Z")
  private val utc = ZoneId.of("UTC")

  @Test
  fun underOneMinute_isJustNow() {
    assertEquals("just now", relativeTime(now.minusSeconds(30), now, utc))
  }

  @Test
  fun minutes_areShownAsMAgo() {
    assertEquals("5m ago", relativeTime(now.minusSeconds(5 * 60), now, utc))
  }

  @Test
  fun hours_areShownAsHAgo() {
    assertEquals("2h ago", relativeTime(now.minusSeconds(2 * 3_600), now, utc))
  }

  @Test
  fun underTwoDays_isYesterday() {
    assertEquals("Yesterday", relativeTime(now.minusSeconds(25 * 3_600), now, utc))
  }

  @Test
  fun days_areShownAsDAgo() {
    assertEquals("3d ago", relativeTime(now.minusSeconds(3 * 86_400), now, utc))
  }

  @Test
  fun overAWeek_showsDate() {
    assertEquals("20 Jul", relativeTime(Instant.parse("2026-07-20T12:00:00Z"), now, utc))
  }
}
