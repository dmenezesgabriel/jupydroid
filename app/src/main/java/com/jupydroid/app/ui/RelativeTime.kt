package com.jupydroid.app.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_WEEK = 604_800L

/** Human-readable age for a notebook's [updatedAt], e.g. "5m ago", "Yesterday". */
fun relativeTime(
  updatedAt: Instant,
  now: Instant,
  zone: ZoneId = ZoneId.systemDefault(),
): String {
  val seconds = Duration.between(updatedAt, now).seconds
  return when {
    seconds < SECONDS_PER_MINUTE -> "just now"
    seconds < SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_MINUTE}m ago"
    seconds < SECONDS_PER_DAY -> "${seconds / SECONDS_PER_HOUR}h ago"
    seconds < 2 * SECONDS_PER_DAY -> "Yesterday"
    seconds < SECONDS_PER_WEEK -> "${seconds / SECONDS_PER_DAY}d ago"
    else -> updatedAt.atZone(zone).format(dayFormatter)
  }
}
