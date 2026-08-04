package com.jupydroid.app.data.space

import android.util.Log
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/**
 * Drives [RealKernelGateway] on a device, against a kernel it starts itself over REST.
 *
 * Kept apart from [SpaceProbe] because the two have opposite lifespans: SpaceProbe walks the
 * Step 8 runtime chain through `KernelChannel` and retires with it at Step 10.5, whereas
 * this exercises the path the app will actually use. Filter with
 * `adb logcat -s JupydroidSpace`.
 */
class KernelGatewayProbe(private val connection: ServerConnection) {
  private val gateway = RealKernelGateway(
    opener = OkHttpKernelSocketOpener(connection),
    lifecycle = OkHttpKernelLifecycle(connection),
  )

  suspend fun run() {
    val session = gateway.connect(BASE_SPACE)
    Log.i(TAG, "gateway connect -> kernel=${session.kernelId}")
    Log.i(TAG, "gateway execute 2 + 2 -> ${gateway.execute(session, "cell-1", "2 + 2").toList()}")
    Log.i(TAG, "gateway execute import numpy -> ${gateway.execute(session, "cell-2", NUMPY_CELL).toList()}")

    interruptALongCell(session)
    reportSocketReuse(session)
  }

  /**
   * Step 10.4b, witnessed by the server rather than by our own counter: jupyter_server
   * reports how many connections a kernel has, so four cells over one socket must read as
   * one connection, and disconnect must take it back to zero.
   */
  private suspend fun reportSocketReuse(session: KernelSession) {
    Log.i(TAG, "connections after four cells: ${kernelState(session.kernelId)}")
    gateway.disconnect(session)
    delay(SETTLE_MILLIS)
    Log.i(TAG, "connections after disconnect: ${kernelState(session.kernelId)}")
  }

  /**
   * A 30-second sleep cut short after two. If interrupt worked this returns in seconds with a
   * KeyboardInterrupt; if it did not, the probe sits here for half a minute and says so by
   * how long it took - which is why the elapsed time is logged rather than just the events.
   */
  private suspend fun interruptALongCell(session: KernelSession) = coroutineScope {
    val started = System.currentTimeMillis()
    val running = async { gateway.execute(session, "cell-3", "import time; time.sleep(30)").toList() }
    delay(INTERRUPT_AFTER_MILLIS)
    gateway.interrupt(session)
    val events = running.await()
    Log.i(TAG, "gateway interrupt after ${System.currentTimeMillis() - started}ms -> $events")
  }

  private suspend fun kernelState(kernelId: String): String = withContext(Dispatchers.IO) {
    runCatching {
      (URL("${connection.baseUrl}api/kernels/$kernelId").openConnection() as HttpURLConnection).run {
        try {
          setRequestProperty("Authorization", "token ${connection.token}")
          inputStream.bufferedReader().use { it.readText() }
        } finally {
          disconnect()
        }
      }
    }.getOrElse { "unreadable: ${it.message}" }
  }

  private companion object {
    const val TAG = "JupydroidSpace"
    const val INTERRUPT_AFTER_MILLIS = 2000L

    /** The server drops a connection asynchronously; without this the count reads stale. */
    const val SETTLE_MILLIS = 1500L
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())
    const val NUMPY_CELL = "import numpy; numpy.linalg.det(numpy.array([[4.0, 7.0], [2.0, 6.0]]))"
  }
}
