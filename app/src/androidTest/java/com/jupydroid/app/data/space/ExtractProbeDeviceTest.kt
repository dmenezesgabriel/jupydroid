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
 * **P8c-1: where the rootfs extraction's ~16.5 s actually goes - measured, not guessed.**
 *
 * P8a's table names `space.installing` → `server.starting` as the cold-start dominator.
 * This probe splits it on the device it is slow on: decompress+walk the tar (no writes),
 * the shipped serial [TarZstdExtractor.extract], and a channel fan-out that keeps the
 * decompressor serial but hands each entry's bytes to N writer workers - which is the
 * ceiling of the obvious fix, measured before it is built.
 *
 * The gzip run measured decompress+walk 8.9 s, serial 12.0 s, fan-out 7.5-8.2 s
 *: the codec was the floor, so the archive is now zstd.
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

  /** Read+decompress only: the tar stream walked, every entry's bytes drained, nothing written. */
  private fun drainOnly(): Pair<Int, Double> {
    var entries = 0
    val start = TimeSource.Monotonic.markNow()
    TarArchiveInputStream(ZstdCompressorInputStream(asset().buffered())).use { tar ->
      while (true) {
        val entry = tar.nextEntry ?: break
        if (entry.isFile) {
          val sink = ByteArray(READ_CHUNK)
          while (tar.read(sink) >= 0) Unit
        }
        entries++
      }
    }
    return entries to start.elapsedNow().inWholeMilliseconds / 1000.0
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
    val scratch = File(context.cacheDir, "p8c-1-probe").apply {
      deleteRecursively()
      mkdirs()
    }
    val report = StringBuilder("== p8c-1 extraction split (arm64 tarball)\n")

    val (drained, drainSeconds) = drainOnly()
    report.append("decompress+walk only : ${"%.1f".format(drainSeconds)}s over $drained entries\n")

    val (wroteSerial, serialSeconds) = serialExtract(File(scratch, "serial"))
    report.append("serial extract       : ${"%.1f".format(serialSeconds)}s ($wroteSerial written)\n")

    for (workers in listOf(4, 8)) {
      val dir = File(scratch, "parallel-$workers")
      val (wrote, seconds) = parallelWrites(workers, dir)
      report.append("parallel writers=$workers : ${"%.1f".format(seconds)}s ($wrote written)\n")
      dir.deleteRecursively()
    }

    val out = File(context.getExternalFilesDir(null), "p8c-1-extract-probe.txt")
    out.writeText(report.toString())
    android.util.Log.i(SPACE_LOG_TAG, report.toString())
    scratch.deleteRecursively()

    // The probe's job is to print the split; fail only if it produced nonsense.
    assertTrue("tarball had no file entries", drained > 1000)
  }

  private companion object {
    const val READ_CHUNK = 1 shl 20
  }
}
