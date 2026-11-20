package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **Why an `import` in the cell below a `%pip install` says the package is not found**, and why
 * selecting the kernel again makes it work.
 *
 * The report is exact: after `%pip install <package>` in one cell, `import <package>` in the next
 * fails, and restarting the kernel fixes it. Two readings predict different repairs - the import
 * system's directory cache, or the install landing off the kernel's `sys.path` - and this probe
 * separates them on the device:
 *
 * - `site.addusersitepackages` adds the user site **only when the directory already exists**
 *   (`usr/lib/python3.12/site.py`: `if ENABLE_USER_SITE and os.path.isdir(user_site)`), and the
 *   image ships no `pkgs/` at all while `SpaceDirs.ensure` creates `pkgs` but not the
 *   `lib/python3.12/site-packages` under it. A kernel started before the Space's first install
 *   therefore has no user site on its path, and the install that creates it happens in a
 *   subprocess that never re-runs `site`.
 * - The cache reading is excluded by the same interpreter importing the package after
 *   `importlib.invalidate_caches()`: if that still fails, the path was the problem.
 *
 * Two arms, because they answer different questions: one long-lived interpreter started with
 * `pkgs/` cleared, and the user's own path - the app's gateway and a real kernel, three cells.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.PipImportProbeDeviceTest
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs. Needs the tablet online - pip downloads.
 */
@RunWith(AndroidJUnit4::class)
class PipImportProbeDeviceTest {
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
  fun anImportInARunningInterpreterSeesWhatPipJustInstalled() {
    val report = StringBuilder("== a %pip install, read from the interpreter that ran it\n")
    report.append(runningInterpreterArm())
    report.append(kernelArm())
    // The reading is the log, not a file: Gradle uninstalls both APKs when the run ends, so a file
    // under `files/` is gone before anyone can pull it. `adb logcat -s JupydroidSpace` keeps it.
    Log.i(TAG, report.toString())

    // The probe's job is to print the readings; fail only if one of the arms never ran.
    assertTrue("the interpreter arm measured nothing: $report", "install_exit:" in report)
    assertTrue("the kernel arm measured nothing: $report", KERNEL_ARM_MARKER in report)
  }

  /**
   * One long-lived interpreter, the shape of a kernel: it read `sys.path` once, at start, before
   * anything was installed. Then a second interpreter, which is the restart the reporter used.
   */
  private fun runningInterpreterArm(): String {
    clearUserBase()
    val running = runInSpace("-c", PROBE)
    val restarted = runInSpace("-c", "import $PROBE_PACKAGE; print('restarted ->', $PROBE_PACKAGE.__file__)")
    runInSpace("-m", "pip", "uninstall", "-y", PROBE_PACKAGE)
    return "== one interpreter, started before the install\n${running.output}\n" +
      "== a second interpreter, started after it\n${restarted.output}\n"
  }

  /**
   * The user's path: the app's own gateway, a real kernel, three cells. The kernel is restarted
   * after it binds, so its `sys.path` is read after `pkgs/` was cleared - the server's spare
   * kernel may have been started before that (ADR-0080), and its path would be the stale one.
   */
  private fun kernelArm(): String = runBlocking {
    clearUserBase()
    val sockets = baseSpaceKernelSockets(BaseSpaceUnderTest.require())
    val gateway = baseSpaceKernelGateway(sockets, KernelSessionRegistry())
    writeProbeNotebook()
    val session = gateway.connect(BASE_SPACE, PROBE_NOTEBOOK)
    try {
      gateway.restart(session)
      KERNEL_ARM_MARKER + "\n" + kernelCells(gateway, session)
    } finally {
      gateway.shutdown(session)
      File(dirs.workspace, PROBE_NOTEBOOK).delete()
    }
  }

  private suspend fun kernelCells(gateway: KernelGateway, session: KernelSession): String {
    val report = StringBuilder()
    for ((cellId, code) in KERNEL_CELLS) {
      val events = gateway.execute(session, cellId, code).toList()
      report.append("cell $cellId: ${code.take(SOURCE_EXCERPT)}\n${textOf(events)}\n")
    }
    return report.toString()
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

  /**
   * The state every Space starts in: `pkgs/` exists (the app creates it) and the user site under it
   * does not, which is what the shipped image carries and what `SpaceDirs.ensure` leaves.
   */
  private fun clearUserBase() {
    dirs.pkgs.deleteRecursively()
    dirs.ensure()
  }

  private fun runInSpace(vararg args: String): ProcessOutcome =
    runBlocking { PythonRunner(baseSpaceRuntime(context)).run(*args) }

  private fun writeProbeNotebook() {
    File(dirs.workspace, PROBE_NOTEBOOK).writeText(PROBE_NOTEBOOK_JSON)
  }

  private companion object {
    const val TAG = "JupydroidSpace"

    /** Small, pure-Python, wheel-published - and deliberately absent from `requirements.lock`. */
    const val PROBE_PACKAGE = "tabulate"

    const val PROBE_NOTEBOOK = "pip-import-probe.ipynb"
    const val KERNEL_ARM_MARKER = "== the real kernel, through the app's own gateway"
    const val SOURCE_EXCERPT = 80

    val PROBE_NOTEBOOK_JSON = """
      {"cells": [], "metadata": {"kernelspec": {"name": "python3", "display_name": "Python 3"}},
       "nbformat": 4, "nbformat_minor": 5}
    """.trimIndent()

    /** The kernel's own account of its path, before anything is installed in it. */
    val KERNEL_DIAGNOSTICS = "import os, site, sys; user_site = site.getusersitepackages(); " +
      "print('user_site:', user_site); print('user_site_exists:', os.path.isdir(user_site)); " +
      "print('user_site_on_path:', user_site in sys.path)"

    /** The three cells the user's path runs, in order: what the kernel's path is, install, import. */
    val KERNEL_CELLS = listOf(
      "kernel-path" to KERNEL_DIAGNOSTICS,
      "install" to "%pip install $PROBE_PACKAGE",
      "import" to "import $PROBE_PACKAGE; print('kernel ->', $PROBE_PACKAGE.__file__)",
    )

    /**
     * `%pip`'s own command line, transcribed from `IPython/core/magics/packaging.py`: the magic runs
     * `self.shell.system(sys.executable + " -m pip …")`, and `system` here is that same call.
     */
    val PROBE = """
      import importlib, os, shlex, site, sys
      from IPython.utils.process import system

      package = "$PROBE_PACKAGE"
      user_site = site.getusersitepackages()
      print("exe:", sys.executable)
      print("user_site:", user_site)
      print("user_site_exists:", os.path.isdir(user_site))
      print("enable_user_site:", site.ENABLE_USER_SITE)
      print("user_site_on_path:", user_site in sys.path)

      def attempt(label):
          try:
              module = importlib.import_module(package)
          except Exception as error:
              print(label, "->", type(error).__name__)
              return
          print(label, "-> ok", module.__file__)

      attempt("import_before_install")
      print("install_exit:", system(" ".join([shlex.quote(sys.executable), "-m", "pip", "install", package])))
      attempt("import_after_install")
      importlib.invalidate_caches()
      attempt("import_after_invalidate")
      if user_site not in sys.path:
          sys.path.append(user_site)
      attempt("import_after_append")
    """.trimIndent()
  }
}
