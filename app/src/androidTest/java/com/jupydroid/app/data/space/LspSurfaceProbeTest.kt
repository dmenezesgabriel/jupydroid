package com.jupydroid.app.data.space

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.port.LanguagePosition
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.File
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

/**
 * **P5e-0: what the real `pylsp` answers to every method it advertises - the parity audit.**
 *
 * [StdioLanguageServerDeviceTest] proves the port drives a session; this dumps the raw
 * replies the port does not yet ask for - `hover`, `signatureHelp`, `definition`,
 * `references`, `documentHighlight`, `documentSymbol`, `foldingRange`, `formatting`,
 * `rename`, `codeAction`, `completionItem/resolve` - plus the fields a completion item
 * actually carries, what a whole-text `didChange` costs at a small and a large document,
 * and how long a diagnostic takes to arrive after one. The dump lands in
 * `files/lsp-probe.txt`, harvested into `evidence/p5e-0-lsp-surface/`.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.LspSurfaceProbeTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LspSurfaceProbeTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }
  private val out by lazy { File(context.getExternalFilesDir(null), "lsp-probe.txt") }
  private val report = StringBuilder()

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no language server to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  @Test(timeout = 300_000)
  fun probeEveryAdvertisedMethodAndMeasureSync() = runBlocking {
    val runtime = baseSpaceRuntime(context)
    val process = runtime.execProcessBuilder(
      listOf(StdioLanguageServer.pylspIn(baseDir).path),
    ).directory(File(baseDir, "home/jupydroid")).start()
    // An undrained stderr pipe is a deadlock a `window/logMessage` flood could cause; a
    // close under a blocked read is teardown noise, not a failure.
    thread(isDaemon = true, name = "probe-stderr") {
      runCatching { BufferedReader(process.errorStream.reader()).forEachLine { } }
    }
    val channel = JsonRpcChannel(process.inputStream, process.outputStream, LogcatSpaceLifecycleLog)
    // Every notification lands in [pushes] from here on - the channel buffers whether or not
    // a collector is waiting, so no publishDiagnostics can be lost to a subscribe-after race.
    val pushes = Channel<JsonObject>(Channel.UNLIMITED)
    val collector = launch { channel.notifications.collect { pushes.trySend(it) } }
    try {
      val caps = channel.request("initialize", LspWire.initializeParams(), 60.seconds)
      channel.notify("initialized", buildJsonObject {})
      section("capabilities", caps.toString())

      channel.notify("textDocument/didOpen", LspWire.didOpenParams(URI, "python", PROBE_SOURCE))
      val openDiagnostics = nextPush(pushes) { it.contains("publishDiagnostics") && it.contains(URI) }
      section("publishDiagnostics after didOpen", openDiagnostics.toString().take(800))

      probeLanguageFeatures(channel, openDiagnostics)
      measureSyncCost(channel, pushes)
      measureDiagnosticsLatency(channel, pushes)
    } finally {
      runCatching { channel.request("shutdown", buildJsonObject {}) }
      runCatching { channel.notify("exit", buildJsonObject {}) }
      out.writeText(report.toString())
      collector.cancel()
      channel.close()
      process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
      if (process.isAlive) process.destroy()
    }
    assertNotNull(report.toString())
  }

  private suspend fun probeLanguageFeatures(channel: JsonRpcChannel, openDiagnostics: JsonObject) {
    val completions = channel.request(
      "textDocument/completion",
      LspWire.positionParams(URI, LanguagePosition(12, 10)),
      60.seconds,
    )
    section("completion at os.path.jo|", completions.toString().take(1500))
    resolveOneCompletionItem(channel, completions["result"])

    request(channel, "textDocument/hover", position = LanguagePosition(10, 11))
    request(channel, "textDocument/signatureHelp", position = LanguagePosition(11, 14))
    request(channel, "textDocument/definition", position = LanguagePosition(10, 11))
    request(channel, "textDocument/references", position = LanguagePosition(10, 11), includeDeclaration = true)
    request(channel, "textDocument/documentHighlight", position = LanguagePosition(6, 16))
    request(channel, "textDocument/documentSymbol")
    request(channel, "textDocument/foldingRange")
    request(channel, "textDocument/formatting", tabOptions = true)
    request(channel, "textDocument/rename", position = LanguagePosition(10, 11), newName = "summed")
    request(channel, "textDocument/codeAction", diagnosticsPush = openDiagnostics)
  }

  /** `completionItem/resolve` takes the item back whole - the `data` round trip is the point. */
  private suspend fun resolveOneCompletionItem(channel: JsonRpcChannel, result: JsonElement?) {
    val item = when (result) {
      is JsonArray -> result.firstOrNull()
      is JsonObject -> (result["items"] as? JsonArray)?.firstOrNull()
      else -> null
    } as? JsonObject ?: return section("completionItem/resolve", "no item to resolve")
    val reply = runCatching { channel.request("completionItem/resolve", item, 60.seconds) }
    section("completionItem/resolve", reply.map { it.toString().take(1600) }.getOrElse { "THREW: $it" })
  }

  private suspend fun measureSyncCost(channel: JsonRpcChannel, pushes: Channel<JsonObject>) {
    val bigUri = "file:///home/jupydroid/p5e-probe-big.py"
    val bigText = (1..80).joinToString("\n\n") { "def fn_$it(x):\n    return x + $it" }
    channel.notify("textDocument/didOpen", LspWire.didOpenParams(bigUri, "python", bigText))
    val completion = measureTimedValue {
      channel.request("textDocument/completion", LspWire.positionParams(bigUri, LanguagePosition(2, 8)), 60.seconds)
    }
    section("completion inside ~${bigText.length}-char doc", "answered in ${completion.duration}")
    repeat(3) { round ->
      val name = "undefined_round_$round"
      val mark = TimeSource.Monotonic.markNow()
      channel.notify("textDocument/didChange", LspWire.didChangeParams(bigUri, round + 1, "$bigText\n$name\n"))
      nextPush(pushes) { it.contains(name) }
      section("didChange→publishDiagnostics ~${bigText.length} chars", "round $round: ${mark.elapsedNow()}")
    }
  }

  private suspend fun measureDiagnosticsLatency(channel: JsonRpcChannel, pushes: Channel<JsonObject>) {
    val mark = TimeSource.Monotonic.markNow()
    channel.notify("textDocument/didChange", LspWire.didChangeParams(URI, 99, "$PROBE_SOURCE\nanother_undefined\n"))
    val body = nextPush(pushes) { it.contains("another_undefined") }
    section("publishDiagnostics latency on probe doc", "${mark.elapsedNow()}: ${body.toString().take(500)}")
  }

  /** The next push matching [match], scanning past the ones that don't. */
  private suspend fun nextPush(pushes: Channel<JsonObject>, match: (String) -> Boolean): JsonObject =
    withTimeout(60.seconds) {
      var push = pushes.receive()
      while (!match(push.toString())) push = pushes.receive()
      push
    }

  private suspend fun request(
    channel: JsonRpcChannel,
    method: String,
    position: LanguagePosition? = null,
    includeDeclaration: Boolean? = null,
    newName: String? = null,
    tabOptions: Boolean = false,
    diagnosticsPush: JsonObject? = null,
  ) {
    val params = buildJsonObject {
      putJsonObject("textDocument") { put("uri", URI) }
      position?.let { putJsonObject("position") { putPosition(it) } }
      includeDeclaration?.let { putJsonObject("context") { put("includeDeclaration", it) } }
      newName?.let { put("newName", it) }
      if (tabOptions) {
        putJsonObject("options") {
          put("tabSize", 4)
          put("insertSpaces", true)
        }
      }
      diagnosticsPush?.let { codeActionContext(it) }
    }
    val reply = runCatching { channel.request(method, params, 60.seconds) }
    section(method, reply.map { it.toString().take(1600) }.getOrElse { "THREW: $it" })
  }

  /** `codeAction` wants a `range` plus a `context` carrying the real pushed diagnostics. */
  private fun JsonObjectBuilder.codeActionContext(push: JsonObject) {
    putJsonObject("range") {
      putJsonObject("start") {
        put("line", 13)
        put("character", 0)
      }
      putJsonObject("end") {
        put("line", 13)
        put("character", 28)
      }
    }
    putJsonObject("context") {
      putJsonArray("diagnostics") {
        ((push["params"] as? JsonObject)?.get("diagnostics") as? JsonArray)?.forEach { add(it) }
      }
    }
  }

  private fun JsonObjectBuilder.putPosition(position: LanguagePosition) {
    put("line", position.line)
    put("character", position.character)
  }

  /**
   * One dump, two sinks: the report file, and logcat. `connectedDebugAndroidTest` uninstalls
   * the app at the end and the file dies with it - `adb logcat -d -s JupydroidSpace` does not.
   */
  private fun section(name: String, body: String) {
    report.append("\n=== $name ===\n").append(body).append('\n')
    LogcatSpaceLifecycleLog.reached("lsp.probe", mapOf("section" to name, "body" to body))
  }

  private companion object {
    const val URI = "file:///home/jupydroid/p5e-probe.py"

    /** Every line number a probe cites counts off this source - keep it stable. */
    val PROBE_SOURCE = listOf(
      "import os",
      "import numpy as np",
      "",
      "",
      "def compute_total(values):",
      "    total = 0",
      "    for item in values:",
      "        total += item",
      "    return total",
      "",
      "result = compute_total([1, 2, 3])",
      "os.path.join(\"a\", \"b\")",
      "os.path.jo",
      "print(never_defined_lsp_name)",
      "np.arange(10)",
      "",
    ).joinToString("\n")
  }
}
