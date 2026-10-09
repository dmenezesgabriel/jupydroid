package com.jupydroid.app.data.space

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One line of `jupyter_server.log`: when the server wrote it, and what it said. */
data class ServerLogLine(val epochMs: Long, val message: String)

/**
 * The lines of `jupyter_server.log` that carry a timestamp. A message's continuation lines carry
 * none and are skipped.
 *
 * The server's format is `[%(levelname)1.1s %(asctime)s.%(msecs).03d %(name)s] %(message)s`
 * (jupyter_server 2.20.0 `serverapp.py`, `_default_log_format`). Its clock reads UTC, because the
 * image ships no `/etc/localtime` and a Space process is handed no `TZ` (`SpaceRuntime.environment`).
 *
 * ```
 * serverLogLines(jupyterServerLogFile(dirs.runtime).readLines())
 * ```
 */
fun serverLogLines(log: List<String>): List<ServerLogLine> = log.mapNotNull { line ->
  LOG_LINE.matchEntire(line)?.destructured?.let { (stamp, message) ->
    ServerLogLine(LocalDateTime.parse(stamp, LOG_STAMP).toInstant(ZoneOffset.UTC).toEpochMilli(), message)
  }
}

private val LOG_LINE = Regex("""\[[A-Z] (\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}) [^\]]+] (.*)""")
private val LOG_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/**
 * **One server boot, cut where the server's own log can cut it:**
 * - the spawn to its first log line: the interpreter under proot and `import jupyter_server`,
 *   up to the config search in `ServerApp.initialize`
 * - that line to "is running at", which `ServerApp.start_app` logs right before it writes the
 *   runtime file: the extensions, the web app, the HTTP server
 * - that to the launcher seeing the runtime file, which it polls for
 *
 * ```
 * ServerBootSplit.of(spawnedAt, totalMs, serverLogLines(log)).toRunningMs
 * ```
 */
class ServerBootSplit private constructor(
  val totalMs: Long,
  val toFirstLineMs: Long,
  val toRunningMs: Long,
  val toReadyMs: Long,
  private val lines: List<ServerLogLine>,
) {
  /** The [count] widest gaps between two consecutive lines, each named by the line that ends it. */
  fun widestGaps(count: Int): String = lines.zipWithNext { before, after -> after.epochMs - before.epochMs to after }
    .sortedByDescending { it.first }
    .take(count)
    .joinToString("") { (gap, line) ->
      "  ${gap.toString().padStart(GAP_WIDTH)} ms before: ${line.message.take(MESSAGE_WIDTH)}\n"
    }

  companion object {
    private const val GAP_WIDTH = 5
    private const val MESSAGE_WIDTH = 100
    private const val RUNNING = "is running at"

    /** A boot spawned at [spawnedAtMs] whose port the launcher saw [totalMs] later, and the log it wrote meanwhile. */
    fun of(spawnedAtMs: Long, totalMs: Long, lines: List<ServerLogLine>): ServerBootSplit {
      val running = lines.firstOrNull { RUNNING in it.message }
      checkNotNull(running) { "no '$RUNNING' line in the server's log; expected one, got ${lines.map { it.message }}" }
      val first = lines.first()
      check(first.epochMs >= spawnedAtMs) {
        "first log line at ${first.epochMs} precedes the spawn at $spawnedAtMs; expected the log in UTC, after it"
      }
      val toFirstLine = first.epochMs - spawnedAtMs
      val toRunning = running.epochMs - first.epochMs
      return ServerBootSplit(totalMs, toFirstLine, toRunning, totalMs - toFirstLine - toRunning, lines)
    }
  }
}
