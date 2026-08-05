package com.jupydroid.app.data.space

import android.util.Log

/**
 * Where the Space runtime says what it did.
 *
 * It exists because deleting the Step 8 probes left the app silent about a runtime that can
 * still fail in ways only a device shows - and the first time that happened, there was
 * nothing in logcat to read and the server log had to be dug out through `run-as`.
 *
 * An interface rather than a direct [Log] call so the classes that report through it stay
 * plain JVM code that unit tests can drive; `android.util.Log` throws when it is not mocked.
 *
 * ```
 * adb logcat -s JupydroidSpace
 * ```
 */
interface SpaceLifecycleLog {
  fun reached(stage: String)

  fun failed(stage: String, cause: Throwable)

  /** Discards everything, for tests and for any caller that does not want the noise. */
  object Silent : SpaceLifecycleLog {
    override fun reached(stage: String) = Unit

    override fun failed(stage: String, cause: Throwable) = Unit
  }
}

/** Logcat, under one tag so the whole runtime can be followed with a single filter. */
object LogcatSpaceLifecycleLog : SpaceLifecycleLog {
  override fun reached(stage: String) {
    Log.i(TAG, stage)
  }

  override fun failed(stage: String, cause: Throwable) {
    Log.e(TAG, stage, cause)
  }

  private const val TAG = "JupydroidSpace"
}
