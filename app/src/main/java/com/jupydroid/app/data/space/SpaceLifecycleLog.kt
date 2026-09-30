package com.jupydroid.app.data.space

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where the Space runtime says what it did.
 *
 * It exists because deleting the runtime probes left the app silent about a runtime that can
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
  /**
   * One thing that happened, and the values that make it mean something.
   *
   * [fields] carries the ids rather than being formatted into [stage], so a stage stays one
   * greppable constant and the values stay machine-readable. Three sightings of the execution
   * stall produced no diagnosis because the execute path said nothing at all; what it says now
   * is meant to be read with `jq`, not with the eye.
   */
  fun reached(stage: String, fields: Map<String, String> = emptyMap())

  fun failed(stage: String, cause: Throwable)

  /** Discards everything, for tests and for any caller that does not want the noise. */
  object Silent : SpaceLifecycleLog {
    override fun reached(stage: String, fields: Map<String, String>) = Unit

    override fun failed(stage: String, cause: Throwable) = Unit
  }
}

/**
 * The stage and its fields as one JSON object, in the order given.
 *
 * Separate from the adapter so it can be tested: `android.util.Log` throws off-device. Built with
 * the serializer rather than by hand because a `reason` here routinely carries quotes and
 * newlines - a close frame's text is whatever the server said.
 */
internal fun spaceLogLine(stage: String, fields: Map<String, String> = emptyMap()): String = JsonObject(
  linkedMapOf<String, JsonPrimitive>("stage" to JsonPrimitive(stage)).apply {
    fields.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
  },
).toString()

/** Logcat, under one tag so the whole runtime can be followed with a single filter. */
object LogcatSpaceLifecycleLog : SpaceLifecycleLog {
  override fun reached(stage: String, fields: Map<String, String>) {
    Log.i(TAG, spaceLogLine(stage, fields))
  }

  override fun failed(stage: String, cause: Throwable) {
    Log.e(TAG, stage, cause)
  }

  private const val TAG = "JupydroidSpace"
}
