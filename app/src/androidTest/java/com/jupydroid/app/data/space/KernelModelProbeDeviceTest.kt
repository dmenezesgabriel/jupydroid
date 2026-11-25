package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_DISPLAY_NAME
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * **The kernel model the Space's `jupyter_server` keeps, measured on the device** rather than read
 * off `jupyter_server/services/sessions/sessionmanager.py` and
 * `usr/lib/python3.12/site-packages/jupyter_server/services/kernelspecs/handlers.py` in the
 * shipped rootfs: a kernel per session rather than per spec, `kernel.name` client-supplied - the
 * session POST's `kernel` field, seeded from `metadata.kernelspec`
 * (`domain/port/KernelChoice.kt`) with an absent one falling back to `default_kernel_name` - and
 * one server-global `GET api/kernelspecs` catalogue every document shares. `GET api/kernels` is
 * read raw because it is not in the port contract.
 *
 * The second arm measures what a glibc environment's spec does today:
 * `SpaceKernelSpec.installEnvironment` (`data/space/SpaceKernelSpec.kt`, ADR-0078) writes the
 * musl-flavoured env map for every environment - no `PROOT_SIGSYS_ENOSYS`, no emptied `LD_PRELOAD`
 * the way `DEFAULT_KERNEL_ENV` gives the gnu default (ADR-0166) - and a venv under
 * `/opt/gnu/python` is the first glibc interpreter that path has been asked to start a kernel
 * under. Whether that start survives is the measurement, not an assumption: a failure here is
 * the finding the spec-env repair is argued from.
 *
 * Both arms drive the plain `OkHttpKernelLifecycle`, not `SpareBindingKernelLifecycle`
 * (ADR-0080): the spare is the app's own optimisation, and these questions are about the server's
 * model underneath it.
 *
 * The reading is the log, not a file: Gradle uninstalls both APKs when the run ends, so a file
 * under `files/` is gone before anyone can pull it - `adb logcat -s JupydroidSpace` keeps it, the
 * same reasoning `PipImportProbeDeviceTest` records.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.KernelModelProbeDeviceTest
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs. The environment arm needs the tablet online -
 * pip downloads ipykernel into the venv.
 */
@RunWith(AndroidJUnit4::class)
class KernelModelProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val spaceDir by lazy { baseSpaceDir(context) }
  private val dirs by lazy { SpaceDirs(spaceDir) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no interpreter to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  @Test(timeout = 900_000)
  fun eachSessionOnOneSpecStartsItsOwnKernelAndTheCatalogueIsShared() {
    runBlocking { sessionKernelModelArm() }
  }

  @Test(timeout = 900_000)
  fun aGnuEnvironmentRegistersAsAKernelSpecAndItsStartIsMeasured() {
    runBlocking { environmentKernelArm() }
  }

  private suspend fun sessionKernelModelArm() {
    val connection = spaceConnection()
    val lifecycle = spaceLifecycle(connection)
    val started = mutableListOf<StartedKernelSession>()
    val report = StringBuilder(KERNEL_MODEL_HEADER)
    try {
      collectKernelModel(lifecycle, connection, started, report)
      assertKernelModel(lifecycle, connection, started)
    } finally {
      Log.i(SPACE_LOG_TAG, report.toString())
      started.forEach { runCatching { lifecycle.shutdownSession(it.serverSessionId) } }
      PROBE_NOTEBOOKS.forEach { File(dirs.workspace, it).delete() }
    }
  }

  private suspend fun collectKernelModel(
    lifecycle: KernelLifecycle,
    connection: ServerConnection,
    started: MutableList<StartedKernelSession>,
    report: StringBuilder,
  ) {
    started += startOnSpec(lifecycle, PROBE_NOTEBOOK_A)
    started += startOnSpec(lifecycle, PROBE_NOTEBOOK_B)
    started += lifecycle.start(PROBE_NOTEBOOK_C)
    val specs = lifecycle.kernelSpecs()
    report.append("== kernelspecs default=${specs.default} names=${specs.specs.map { it.name }}\n")
    report.append("== GET api/kernels\n${restGet(connection, "api/kernels")}\n")
    report.append(sessionsReport(lifecycle, started))
  }

  private suspend fun startOnSpec(lifecycle: KernelLifecycle, notebook: String): StartedKernelSession {
    writeNotebook(notebook, DEFAULT_KERNEL_ID, DEFAULT_KERNEL_DISPLAY_NAME)
    return lifecycle.start(notebook, KernelChoice.Spec(kernelspecNameOf(notebook)))
  }

  private suspend fun sessionsReport(lifecycle: KernelLifecycle, started: List<StartedKernelSession>): String {
    val ours = started.mapTo(mutableSetOf()) { it.serverSessionId }
    val rows = lifecycle.sessions().filter { it.serverSessionId in ours }
    return "== GET api/sessions\n" + rows.joinToString("\n") { sessionLine(it) } + "\n"
  }

  private fun sessionLine(model: KernelSessionModel): String =
    "${model.serverSessionId} kernel=${model.kernelId}(${model.kernelName}) " +
      "path=${model.path} type=${model.type}"

  private suspend fun assertKernelModel(
    lifecycle: KernelLifecycle,
    connection: ServerConnection,
    started: List<StartedKernelSession>,
  ) {
    assertEquals("the probe started three sessions", 3, started.size)
    assertNotEquals("two sessions on one spec share a kernel", started[0].kernelId, started[1].kernelId)
    assertEquals(DEFAULT_KERNEL_ID, started[0].kernelName)
    assertEquals(DEFAULT_KERNEL_ID, started[1].kernelName)
    assertEquals(
      "a session asked for no kernel starts the server's default",
      lifecycle.kernelSpecs().default,
      started[2].kernelName,
    )
    assertKernelsAndSessions(lifecycle, connection, started)
  }

  private suspend fun assertKernelsAndSessions(
    lifecycle: KernelLifecycle,
    connection: ServerConnection,
    started: List<StartedKernelSession>,
  ) {
    val kernels = restGet(connection, "api/kernels")
    started.forEach { session ->
      assertTrue("GET api/kernels lacks ${session.kernelId}", kernels.contains(session.kernelId))
    }
    val sessions = lifecycle.sessions().associateBy { it.serverSessionId }
    started.forEach { session ->
      assertEquals(session.kernelId, sessions[session.serverSessionId]?.kernelId)
      assertEquals(session.kernelName, sessions[session.serverSessionId]?.kernelName)
    }
  }

  private suspend fun environmentKernelArm() {
    val connection = spaceConnection()
    val lifecycle = spaceLifecycle(connection)
    val report = StringBuilder(ENVIRONMENT_HEADER)
    try {
      collectEnvironmentReading(lifecycle, connection, report)
    } finally {
      Log.i(SPACE_LOG_TAG, report.toString())
      cleanupEnvironment()
    }
  }

  private suspend fun collectEnvironmentReading(
    lifecycle: KernelLifecycle,
    connection: ServerConnection,
    report: StringBuilder,
  ) {
    val venv = appendVenv(report)
    appendPip(report, venv)
    SpaceKernelSpec.installEnvironment(spaceDir, ENV_SPEC_NAME, "$venv/bin/python", ENV_DISPLAY_NAME)
    appendRegisteredSpec(lifecycle, report)
    writeNotebook(ENV_NOTEBOOK, ENV_SPEC_NAME, ENV_DISPLAY_NAME)
    report.append(environmentKernelReading(lifecycle, connection))
  }

  private fun appendVenv(report: StringBuilder): String {
    val inRoot = dirs.inRoot(File(dirs.tmp, VENV_NAME))
    val created = runInSpace(GNU_INTERPRETER, "-m", "venv", inRoot)
    report.append("== $GNU_INTERPRETER -m venv $inRoot\nexit=${created.exitCode}\n")
    report.append("${created.output.take(OUTPUT_EXCERPT)}\n")
    assertEquals("the gnu venv was not created: ${created.output.take(OUTPUT_EXCERPT)}", 0, created.exitCode)
    return inRoot
  }

  /**
   * pip under the Space's own environment first - a glibc interpreter inherits the musl preloads
   * and no `PROOT_SIGSYS_ENOSYS`, so a refused syscall is fatal rather than ENOSYS (ADR-0166) -
   * then under the marker the kernel env carries, so the venv exists whichever way the bare
   * attempt went. Both outcomes stay in the report: their difference is the finding. The exit
   * code cannot be trusted here - a tracee killed by SIGSYS still reports 0 through proot
   * (measured: `pip install` ended mid-download, exit 0, nothing installed) - so the check is
   * the import, not the status.
   */
  private fun appendPip(report: StringBuilder, venv: String) {
    appendPipAttempt(report, venv, "Space env", emptyMap())
    val bareProbe = ipykernelProbe(venv)
    report.append(
      "== ipykernel check (Space env): exit=${bareProbe.exitCode} " +
        "${bareProbe.output.trim().take(OUTPUT_EXCERPT)}\n",
    )
    if (bareProbe.exitCode == 0) return
    appendPipAttempt(report, venv, "PROOT_SIGSYS_ENOSYS=1", ENOSYS_EXTRA_ENV)
    val enosysProbe = ipykernelProbe(venv)
    report.append(
      "== ipykernel check (enosys): exit=${enosysProbe.exitCode} " +
        "${enosysProbe.output.trim().take(OUTPUT_EXCERPT)}\n",
    )
    assertEquals("ipykernel did not land in the venv", 0, enosysProbe.exitCode)
  }

  private fun appendPipAttempt(report: StringBuilder, venv: String, label: String, extraEnv: Map<String, String>) {
    val installed = runInSpace("$venv/bin/python", "-m", "pip", "install", "ipykernel", extraEnv = extraEnv)
    report.append("== pip install ipykernel ($label)\nexit=${installed.exitCode}\n")
    report.append("${installed.output.takeLast(OUTPUT_TAIL)}\n")
  }

  private fun ipykernelProbe(venv: String): SpaceProcessRun =
    runInSpace("$venv/bin/python", "-c", IPYTHON_VERSION_PROBE)

  private suspend fun appendRegisteredSpec(lifecycle: KernelLifecycle, report: StringBuilder) {
    val specs = lifecycle.kernelSpecs()
    report.append("== kernelspecs after installEnvironment\n")
    report.append("default=${specs.default} names=${specs.specs.map { it.name }}\n")
    assertTrue(
      "kernelspec $ENV_SPEC_NAME absent from the live catalogue: ${specs.specs.map { it.name }}",
      specs.specs.any { it.name == ENV_SPEC_NAME },
    )
  }

  private suspend fun environmentKernelReading(lifecycle: KernelLifecycle, connection: ServerConnection): String {
    val gateway = environmentGateway(lifecycle, connection)
    val connected = connectEnvironmentKernel(gateway)
    val session = connected.getOrNull() ?: return "connect failed: ${connected.exceptionOrNull()}"
    assertEquals("the env spec's name is the session's kernel.name", ENV_SPEC_NAME, session.kernelName)
    val header = "kernel=${session.kernelName}(${session.kernelId}) session=${session.serverSessionId}\n"
    return try {
      header + cellEvents(gateway, session)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      header + "cell failed: ${e.javaClass.simpleName}: ${e.message}"
    } finally {
      runCatching { gateway.shutdown(session) }
    }
  }

  private fun environmentGateway(lifecycle: KernelLifecycle, connection: ServerConnection): KernelGateway =
    RealKernelGateway(
      KernelSockets(
        opener = OkHttpKernelSocketOpener(connection),
        lifecycle = lifecycle,
        log = LogcatSpaceLifecycleLog,
      ),
      log = LogcatSpaceLifecycleLog,
    )

  private suspend fun connectEnvironmentKernel(gateway: KernelGateway): Result<KernelSession> = try {
    withTimeoutOrNull(KERNEL_START_TIMEOUT) {
      gateway.connect(BASE_SPACE, ENV_NOTEBOOK, KernelChoice.Spec(kernelspecNameOf(ENV_NOTEBOOK)))
    }?.let { Result.success(it) }
      ?: Result.failure(IllegalStateException("no session within $KERNEL_START_TIMEOUT"))
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Result.failure(e)
  }

  private suspend fun cellEvents(gateway: KernelGateway, session: KernelSession): String {
    val events = withTimeoutOrNull(CELL_TIMEOUT) {
      gateway.execute(session, ENV_CELL_ID, ENV_PROBE).toList()
    } ?: return "no events within $CELL_TIMEOUT"
    return textOf(events)
  }

  /** What the kernel's own stdout and errors said, in order - a traceback's last line included. */
  private fun textOf(events: List<KernelEvent>): String = events
    .mapNotNull { (it as? KernelEvent.Output)?.output }
    .joinToString("") { output ->
      when (output) {
        is CellOutput.Stream -> output.text
        is CellOutput.Error -> "${output.name}: ${output.value}\n"
        is CellOutput.ExecuteResult -> ""
        is CellOutput.DisplayData -> ""
        is CellOutput.DisplayUpdate -> ""
        is CellOutput.ClearOutput -> ""
      }
    }.trim()

  private fun cleanupEnvironment() {
    File(spaceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$ENV_SPEC_NAME").deleteRecursively()
    File(dirs.tmp, VENV_NAME).deleteRecursively()
    File(dirs.workspace, ENV_NOTEBOOK).delete()
  }

  private fun writeNotebook(notebook: String, specName: String, displayName: String) {
    File(dirs.workspace, notebook).writeText(notebookJson(specName, displayName))
  }

  private fun notebookJson(specName: String, displayName: String) = """
    {"cells": [], "metadata": {"kernelspec": {"name": "$specName", "display_name": "$displayName"}},
     "nbformat": 4, "nbformat_minor": 5}
  """.trimIndent()

  private fun kernelspecNameOf(notebook: String): String =
    Json.parseToJsonElement(File(dirs.workspace, notebook).readText())
      .jsonObject.getValue("metadata").jsonObject.getValue("kernelspec").jsonObject
      .getValue("name").jsonPrimitive.content

  private fun restGet(connection: ServerConnection, path: String): String {
    val request = Request.Builder()
      .url(connection.baseUrl + path)
      .header("Authorization", "token ${connection.token}")
      .get()
      .build()
    return OkHttpClient().newCall(request).execute().use { it.body.string() }
  }

  /**
   * The plain lifecycle on a client that waits out a cold kernel start: OkHttp's default 10 s
   * read timeout fires while `POST api/sessions` is still provisioning the first kernel on a
   * fresh rootfs (measured: `SocketTimeoutException` 10 s into the first start), where the app
   * itself never sees it because the spare kernel answers first (ADR-0080).
   */
  private fun spaceLifecycle(connection: ServerConnection): KernelLifecycle = OkHttpKernelLifecycle(
    connection,
    client = OkHttpClient.Builder()
      .callTimeout(KERNEL_START_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
      .readTimeout(KERNEL_START_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
      .build(),
  )

  private suspend fun spaceConnection(): ServerConnection = BaseSpaceUnderTest.require().connection().connection

  private companion object {
    const val PROBE_NOTEBOOK_A = "probe-a.ipynb"
    const val PROBE_NOTEBOOK_B = "probe-b.ipynb"
    const val PROBE_NOTEBOOK_C = "probe-c.ipynb"
    const val ENV_NOTEBOOK = "probe-env.ipynb"
    const val ENV_SPEC_NAME = "probe-gnu-env"
    const val ENV_DISPLAY_NAME = "Python (probe-gnu-env)"
    const val ENV_CELL_ID = "env-probe"
    const val VENV_NAME = "probe-gnu-env"
    const val OUTPUT_EXCERPT = 400
    const val OUTPUT_TAIL = 800

    val PROBE_NOTEBOOKS = listOf(PROBE_NOTEBOOK_A, PROBE_NOTEBOOK_B, PROBE_NOTEBOOK_C)
    val KERNEL_START_TIMEOUT = 120.seconds
    val CELL_TIMEOUT = 90.seconds

    const val KERNEL_MODEL_HEADER = "== the kernel model, read from the server's own REST\n"
    const val ENVIRONMENT_HEADER = "== a gnu environment registered as a spec; its kernel start measured\n"
    const val IPYTHON_VERSION_PROBE =
      "import importlib.metadata; print(importlib.metadata.version('ipykernel'))"

    val ENOSYS_EXTRA_ENV = mapOf("PROOT_SIGSYS_ENOSYS" to "1")

    val ENV_PROBE = """
      import importlib.metadata, json, sys
      print(json.dumps({"exe": sys.executable, "prefix": sys.prefix,
                        "ipykernel": importlib.metadata.version("ipykernel")}))
    """.trimIndent()
  }
}
