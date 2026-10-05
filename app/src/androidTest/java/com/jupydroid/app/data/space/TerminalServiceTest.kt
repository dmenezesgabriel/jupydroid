package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * The terminal service answers, and the shell it spawns is the one this app pinned.
 *
 * **It deliberately goes further than a `GET`.** `GET /api/terminals` returning
 * `200 []` proves only that the extension loaded; it would look identical if terminado were about
 * to spawn Android's own shell out of a `PATH` search with `-l` appended. That is the same shape
 * of hollow gate the busybox work caught, where `sh -c 'echo ok'` passed while all 304 applets
 * were broken.
 * So the terminal is actually **created**, and the
 * argv of the process behind it is read back.
 *
 * **Instrumented, and the first test here that starts a real `jupyter_server`.** It starts one
 * through [BaseSpaceServer] rather than a second launcher, for the reason that class documents:
 * `JupyterServerLauncher.start` begins with `stop()`, so a second starter kills the first's server.
 *
 * `Assume`-skipped where no rootfs ships, so it is green-by-skip on x86_64 - the same rule
 * [PtySupportTest] follows, and for the same reason.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.TerminalServiceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TerminalServiceTest {
  @After
  fun sayWhatTheServerMadeOfIt() {
    if (runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isFailure) return
    reportForTheRecord()
  }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no server to ask",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  /**
   * The extension loaded at all. It is enabled by a config drop-in the rootfs carries at
   * `usr/etc/jupyter/jupyter_server_config.d/jupyter_server_terminals.json`, found because
   * `sys.prefix` is `/usr` inside the translated root and `jupyter_core/paths.py` puts
   * `sys.prefix/etc/jupyter` on the config path. A 404 here would mean that chain broke.
   */
  @Test
  fun theTerminalServiceIsRegisteredAndHasNothingRunning() {
    val (status, body) = terminals.request("GET", "")

    assertEquals("GET /api/terminals answered $status: $body", HTTP_OK, status)
    assertEquals("expected no terminals before this test made one, got $body", 0, body.asArray().size)
  }

  /**
   * A terminal is created, which means the pinned shell was **`execv`'d**.
   *
   * `ptyprocess` carries an exec failure back to the parent over an `FD_CLOEXEC` pipe and re-raises
   * it there (`ptyprocess.py:227-334`), so this is not a case of the parent getting a pty whatever
   * happens to the child: a shell that could not be executed answers 500, not 200.
   */
  @Test
  fun creatingATerminalSpawnsTheShell() {
    val name = terminals.create()

    try {
      val (status, listed) = terminals.request("GET", "")
      assertEquals(HTTP_OK, status)
      assertTrue(
        "the terminal just created is not in the listing: $listed",
        listed.asArray().any { it.jsonObject.getValue("name").jsonPrimitive.content == name },
      )
    } finally {
      terminals.delete(name)
    }
  }

  /**
   * **The pin took effect**, read off the running process rather than off our own argument list.
   *
   * Two things at once, and neither is reachable from the REST model: the argv is the Space's
   * `bin/sh` rather than whatever `which("sh")` found, and it carries **no `-l`** - the login-shell
   * flag `jupyter_server_terminals` appends whenever the shell is not overridden and stdout is not
   * a tty, which this server's never is.
   *
   * It also proves the shell is *alive*, which the 200 from `create` does not: a process can exec
   * successfully and die a moment later - under a bad `LD_PRELOAD`, say - and the REST model would
   * read the same either way.
   */
  @Test
  fun theRunningShellIsThePinnedOneAndIsNotALoginShell() {
    val name = terminals.create()

    try {
      val argv = shellProcessArgv()
      assumeTrue(
        "no process argv was readable, so this device cannot answer; see the log for the fallback",
        argv != null,
      )
      assertEquals("the pinned shell is not what is running", listOf(pinnedShell.path), argv)
    } finally {
      terminals.delete(name)
    }
  }

  /**
   * **The shell is still there a second later**, which a faster assertion would have let the
   * busybox defect slip past.
   *
   * Alpine's busybox died of SIGSYS about seven milliseconds after exec, so terminado had already
   * reaped the terminal by the time anything asked - but a shell that lived half a second and then
   * died would satisfy every other test here while being just as useless. The wait is what makes
   * "created" mean "running": `EOF on FD n; stopping reading` in the server log inside this window
   * is the failure, and the empty listing is how it surfaces.
   */
  @Test
  fun aTerminalOutlivesTheRequestThatMadeIt() {
    val name = terminals.create()

    try {
      Thread.sleep(SETTLE.inWholeMilliseconds)
      val (_, listed) = terminals.request("GET", "")
      assertTrue(
        "the shell died within $SETTLE of being started; the listing is $listed",
        listed.asArray().any { it.jsonObject.getValue("name").jsonPrimitive.content == name },
      )
    } finally {
      terminals.delete(name)
    }
  }

  /** `DELETE` sets 204 and the terminal is gone, which is what makes the tests above independent. */
  @Test
  fun aTerminalCanBeClosedAgain() {
    val name = terminals.create()

    assertEquals(HTTP_NO_CONTENT, terminals.delete(name))
    assertTrue("terminal $name survived its DELETE", terminals.request("GET", "").second.asArray().isEmpty())
  }

  /**
   * Every `/proc` entry this uid can see whose argv starts with the pinned shell, or null when
   * `/proc` is unreadable at all - Android mounts it `hidepid`, and whether a sibling process of
   * the same uid is visible is a property of the device rather than something to assume.
   *
   * The fallback oracle is logged rather than asserted here: terminado writes
   * `EOF on FD n; stopping reading` when a pty dies (`terminado/management.py:on_eof`), so the
   * server log says whether the shell survived even where this returns null.
   */
  private fun shellProcessArgv(): List<String>? {
    val processes = File("/proc").listFiles { entry -> entry.name.toIntOrNull() != null } ?: return null
    val argvs = processes.mapNotNull { it.argv() }.filter { it.firstOrNull() == pinnedShell.path }
    Log.i(TAG, "processes running the pinned shell: $argvs")
    return argvs.firstOrNull()
  }

  /** `/proc/<pid>/cmdline` is NUL-separated and NUL-terminated, hence the empty trailing field. */
  private fun File.argv(): List<String>? = runCatching { File(this, "cmdline").readText() }
    .getOrNull()?.split('\u0000')?.filter { it.isNotEmpty() }

  private fun String.asArray() = Json.parseToJsonElement(this).jsonArray

  private companion object {
    /** Long enough that a shell dying on its first syscall has already gone. */
    val SETTLE = 2.seconds

    const val HTTP_OK = 200
    const val HTTP_NO_CONTENT = 204

    /** `adb logcat -s JupydroidSpace`, the filter every Space investigation already uses. */
    const val TAG = "JupydroidSpace"

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    val pinnedShell: File get() = File(SPACE_SHELL)

    /**
     * One server for the whole **run**, not merely this class - see [BaseSpaceUnderTest]. Starting
     * `jupyter_server` out of the Space costs seconds, and the tests are independent inside it:
     * each closes whatever it opened.
     */
    val terminals: TerminalsApi by lazy {
      TerminalsApi(runBlocking { BaseSpaceUnderTest.require().connection() }.connection)
    }

    /**
     * The pinned argument and the server's own account of what it did with it, on the device.
     *
     * Logged rather than left in a file, for [PtySupportTest]'s reason: `connectedAndroidTest`
     * uninstalls both APKs when it finishes and takes the Space with them, so `run-as` afterwards
     * finds no package and there is nothing left to pull.
     */
    fun reportForTheRecord() {
      val inImage = File(baseSpaceDir(context), pinnedShell.path.removePrefix("/"))
      Log.i(TAG, "terminal shell pinned at: ${pinnedShell.path} (exists in image=${inImage.exists()})")
      val log = File(baseSpaceDir(context), "run/jupyter_server.log").takeIf { it.isFile }?.readText().orEmpty()
      // terminado's own account: `EOF on FD n; stopping reading` is what it logs when a pty dies
      // (`terminado/management.py:on_eof`), and it is the only place a shell that exec'd and then
      // exited is distinguishable from one that never started.
      log.lines().filter { "erminal" in it || "EOF on FD" in it || "TermSocket" in it }
        .forEach { Log.i(TAG, "server: $it") }
      val refusals = File(baseSpaceDir(context), "tmp/sigsys.log").takeIf { it.isFile }?.readText()
      Log.i(TAG, "terminal seccomp refusals: ${refusals?.trim()?.ifBlank { "(none)" } ?: "(no log)"}")
    }
  }
}

/**
 * The three `/api/terminals` calls this gate needs, and nothing more.
 *
 * **Not a port, and deliberately so.** `TerminalGateway` is the port; this is the raw REST surface
 * used to prove the *server's* configuration, so it stays inside the test that asks the question
 * rather than becoming an abstraction the port then has to replace.
 */
private class TerminalsApi(private val connection: ServerConnection) {
  private val client = OkHttpClient()

  fun request(method: String, path: String): Pair<Int, String> {
    val url = connection.baseUrl.toHttpUrl().newBuilder().addPathSegments("api/terminals$path").build()
    val body = if (method == "POST") "{}".toRequestBody(JSON) else null
    val request = Request.Builder().url(url).method(method, body)
      // Token auth rather than a cookie: `check_xsrf_cookie` returns early for a token-authenticated
      // request (`jupyter_server/base/handlers.py:530-538`), which is what lets this POST through.
      .header("Authorization", "token ${connection.token}")
      .header("Content-Type", "application/json")
      .build()
    return client.newCall(request).execute().use { it.code to it.body.string() }
  }

  /** POST answers **200 with the model**, not a redirect - `restapi.ts` errors on `status !== 200`. */
  fun create(): String {
    val (status, body) = request("POST", "")
    check(status == 200) { "POST /api/terminals answered $status: $body" }
    return Json.parseToJsonElement(body).jsonObject.getValue("name").jsonPrimitive.content
  }

  fun delete(name: String): Int = request("DELETE", "/$name").first

  private companion object {
    val JSON = "application/json".toMediaType()
  }
}
