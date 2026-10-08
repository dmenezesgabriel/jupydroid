package com.jupydroid.app.data.space

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.TimeSource

/**
 * **Where the rootfs extraction's time actually goes - measured, not guessed.**
 *
 * The cold-start timeline names `space.installing` → `server.starting` as its dominator.
 * This probe splits it on the device it is slow on: decompress+walk the tar (no writes),
 * the shipped serial [TarZstdExtractor.extract], the same serial walk through 1 MiB buffers
 * on both sides of the decoder, and a channel fan-out that keeps the decompressor serial but
 * hands each entry's bytes to N writer workers - which is the ceiling of the obvious fix,
 * measured before it is built.
 *
 * The gzip run measured decompress+walk 8.9 s, serial 12.0 s, fan-out 7.5-8.2 s:
 * the codec was the floor, so the archive is now zstd.
 * The parallel probe's 13308 counts files only; the serial 15237 counts every tar
 * entry including directories and links.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ExtractProbeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ExtractProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no tarball to extract",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  private fun asset() = context.assets.open("spaces/base/rootfs-arm64-v8a.tzst")

  /** What a drain-only walk saw, so archives measured on different days can be compared. */
  private data class ArchiveShape(
    val entries: Int,
    val fileNames: List<String>,
    val fileBytes: Long,
    val seconds: Double,
  )

  /** Read+decompress only: the tar stream walked, every entry's bytes drained, nothing written. */
  private fun drainOnly(): ArchiveShape {
    var entries = 0
    val fileNames = mutableListOf<String>()
    var fileBytes = 0L
    val sink = ByteArray(READ_CHUNK)
    val start = TimeSource.Monotonic.markNow()
    TarArchiveInputStream(ZstdCompressorInputStream(asset().buffered())).use { tar ->
      while (true) {
        val entry = tar.nextEntry ?: break
        if (entry.isFile) {
          fileNames += entry.name
          while (true) fileBytes += tar.read(sink).takeIf { it >= 0 } ?: break
        }
        entries++
      }
    }
    return ArchiveShape(entries, fileNames, fileBytes, start.elapsedNow().inWholeMilliseconds / 1000.0)
  }

  /** Every file the archive holds, created empty: what a write costs before its first byte. */
  private fun createEmpty(fileNames: List<String>, targetDir: File): Double {
    val start = TimeSource.Monotonic.markNow()
    for (name in fileNames) {
      val destination = File(targetDir, name)
      destination.parentFile?.mkdirs()
      destination.createNewFile()
    }
    return start.elapsedNow().inWholeMilliseconds / 1000.0
  }

  /**
   * The shipped walk's shape through 1 MiB buffers on both sides of the decoder: the cheapest
   * codec-side lever, measured before it is built. Files and directories only, so it is compared
   * with [drainOnly] and not with the shipped extractor's link handling.
   */
  private fun largeBufferExtract(targetDir: File): Pair<Int, Double> {
    val root = targetDir.canonicalFile
    var files = 0
    val start = TimeSource.Monotonic.markNow()
    val decoded = ZstdCompressorInputStream(asset().buffered(READ_CHUNK)).buffered(READ_CHUNK)
    TarArchiveInputStream(decoded).use { tar ->
      while (true) {
        val entry = tar.nextEntry ?: break
        val destination = File(root, entry.name)
        if (entry.isDirectory) destination.mkdirs()
        if (!entry.isFile) continue
        destination.parentFile?.mkdirs()
        destination.outputStream().use { tar.copyTo(it, READ_CHUNK) }
        files++
      }
    }
    return files to start.elapsedNow().inWholeMilliseconds / 1000.0
  }

  /** Decompress serially, hand each file's bytes to [workers] writer coroutines. */
  private suspend fun parallelWrites(workers: Int, targetDir: File): Pair<Int, Double> = coroutineScope {
    val root = targetDir.canonicalFile
    var entries = 0
    val queue = Channel<Pair<File, ByteArray>>(capacity = workers * 8)
    val start = TimeSource.Monotonic.markNow()
    val writers = (1..workers).map {
      launch(Dispatchers.IO) {
        for ((destination, bytes) in queue) {
          destination.parentFile?.mkdirs()
          destination.writeBytes(bytes)
        }
      }
    }
    TarArchiveInputStream(ZstdCompressorInputStream(asset().buffered())).use { tar ->
      while (true) {
        val entry = tar.nextEntry ?: break
        if (!entry.isFile) continue
        val destination = File(root, entry.name).normalize()
        if (!destination.toPath().startsWith(root.toPath())) continue
        queue.send(destination to tar.readBytes())
        entries++
      }
    }
    queue.close()
    writers.joinAll()
    entries to start.elapsedNow().inWholeMilliseconds / 1000.0
  }

  /** The shipped path, verbatim. */
  private suspend fun serialExtract(targetDir: File): Pair<Int, Double> {
    val start = TimeSource.Monotonic.markNow()
    val written = TarZstdExtractor().extract({ asset() }, targetDir)
    return written to start.elapsedNow().inWholeMilliseconds / 1000.0
  }

  @Test
  fun whereTheExtractionTimeGoes() = runBlocking {
    val scratch = File(context.cacheDir, "extract-probe").apply {
      deleteRecursively()
      mkdirs()
    }
    val report = StringBuilder("== rootfs extraction split (arm64 tarball)\n")

    val shape = drainOnly()
    report.append(
      "decompress+walk only : ${"%.1f".format(shape.seconds)}s over ${shape.entries} entries " +
        "(${shape.fileNames.size} files, ${shape.fileBytes} bytes)\n",
    )

    val serialDir = File(scratch, "serial")
    val (wroteSerial, serialSeconds) = serialExtract(serialDir)
    serialDir.deleteRecursively()
    report.append("serial extract       : ${"%.1f".format(serialSeconds)}s ($wroteSerial written)\n")
    report.append("  of which writing   : ${"%.1f".format(serialSeconds - shape.seconds)}s\n")

    val emptyDir = File(scratch, "empty")
    report.append("empty files only     : ${"%.1f".format(createEmpty(shape.fileNames, emptyDir))}s\n")
    emptyDir.deleteRecursively()

    val largeDir = File(scratch, "large-buffers")
    val (wroteLarge, largeSeconds) = largeBufferExtract(largeDir)
    report.append("1 MiB buffers        : ${"%.1f".format(largeSeconds)}s ($wroteLarge files)\n")
    largeDir.deleteRecursively()

    for (workers in listOf(1, 4)) {
      val dir = File(scratch, "parallel-$workers")
      val (wrote, seconds) = parallelWrites(workers, dir)
      report.append("parallel writers=$workers : ${"%.1f".format(seconds)}s ($wrote written)\n")
      dir.deleteRecursively()
    }

    val out = File(context.getExternalFilesDir(null), "extract-probe.txt")
    out.writeText(report.toString())
    android.util.Log.i(SPACE_LOG_TAG, report.toString())
    scratch.deleteRecursively()

    // The probe's job is to print the split; fail only if it produced nonsense.
    assertTrue("tarball had no file entries", shape.fileNames.size > 1000)
  }

  private companion object {
    const val READ_CHUNK = 1 shl 20
  }
}
