package com.jupydroid.app.data.space

import com.jupydroid.app.data.disk.filesToPrune
import com.jupydroid.app.domain.model.ErrorLogCaps
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The stamp an archive's name carries, UTC: `jupyter_server-yyyyMMdd-HHmmss-SSS.log`. */
private val ARCHIVE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)

/**
 * One run's archive: the stamp, and the `-N` suffix a same-millisecond collision takes. The whole
 * name is matched so a file that only looks like an archive - `jupyter_server-2026-13-45.log` -
 * is never ours to delete.
 */
private val ARCHIVE_NAME = Regex("""jupyter_server-(\d{8})-\d{6}-\d{3}(?:-\d+)?\.log""")

/**
 * Archives the server log a start finds in `run/` and prunes what the caps no longer allow, so the
 * restart after a crash does not overwrite the crash's account of itself (ADR-0167).
 *
 * Called before `SpaceDirs.clearRuntime()` empties `run/`: the live `jupyter_server.log` is renamed
 * into [SpaceDirs.serverLogs] rather than deleted, and the redirect that opens the next run's file
 * then finds the path free. Only a non-empty log is worth a file; the prune runs every start
 * regardless, the way the error log's does.
 *
 * ```
 * rotateServerLog(dirs, ErrorLogCaps.DEFAULT, clock()) // inside prepareWritableDirs, before clearRuntime()
 * ```
 */
internal fun rotateServerLog(dirs: SpaceDirs, caps: ErrorLogCaps, nowMillis: Long) {
  val live = jupyterServerLogFile(dirs.runtime)
  if (live.isFile && live.length() > 0) archive(live, dirs.serverLogs, nowMillis)
  filesToPrune(dirs.serverLogs, caps, nowMillis, ::archiveDayOf).forEach(File::delete)
}

/** The finished run's log under its stamp; a same-stamp collision takes `-2`, `-3`, … */
private fun archive(live: File, dir: File, nowMillis: Long) {
  dir.mkdirs()
  val target = archiveTarget(dir, nowMillis)
  check(live.renameTo(target)) { "could not move '$live' to '$target': expected the finished run's log archived" }
}

private fun archiveTarget(dir: File, nowMillis: Long): File {
  val stamp = ARCHIVE_STAMP.format(Instant.ofEpochMilli(nowMillis))
  var suffix = 1
  while (true) {
    val name = "jupyter_server-$stamp" + if (suffix == 1) ".log" else "-$suffix.log"
    val candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    suffix++
  }
}

/** The UTC day an archive's stamp names, or null for a name that is not a run's archive. */
private fun archiveDayOf(fileName: String): LocalDate? {
  val stamp = ARCHIVE_NAME.matchEntire(fileName)?.groupValues?.get(1) ?: return null
  return try {
    LocalDate.parse(stamp, DateTimeFormatter.BASIC_ISO_DATE)
  } catch (notADay: DateTimeParseException) {
    null
  }
}
