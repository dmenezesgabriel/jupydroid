package com.jupydroid.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Every window the accessibility service reports, which is where a `uiautomator` dump reads the screen.
 *
 * A dialog is a window of its own and the system pans it when the keyboard opens, so a Compose
 * node's bounds in its root say nothing about whether the keyboard covers it: only the window
 * manager's frames do.
 */
private fun windows(): List<AccessibilityWindowInfo> {
  val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
  // The default connection does not retrieve windows, so `windows` is empty until it is asked to.
  automation.serviceInfo = automation.serviceInfo.apply {
    flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
  }
  return automation.windows
}

private fun boundsInScreen(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

/**
 * The top edge, in screen pixels, of the soft keyboard's window, or `null` while none is showing.
 *
 * ```
 * val top = softKeyboardTop() ?: return // no keyboard up yet
 * ```
 */
internal fun softKeyboardTop(): Int? = windows()
  .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
  ?.let { window -> Rect().also(window::getBoundsInScreen).top }

private fun AccessibilityNodeInfo.selfAndDescendants(): Sequence<AccessibilityNodeInfo> = sequence {
  yield(this@selfAndDescendants)
  for (index in 0 until childCount) getChild(index)?.let { yieldAll(it.selfAndDescendants()) }
}

private fun AccessibilityNodeInfo.selfAndAncestors(): Sequence<AccessibilityNodeInfo> =
  generateSequence(this) { it.parent }

private fun nodesOnScreen(): Sequence<AccessibilityNodeInfo> =
  windows().asSequence().mapNotNull { it.root }.flatMap { it.selfAndDescendants() }

/** Every text a screen reader would find, for a failure message that says what was there instead. */
internal fun textsOnScreen(): List<String> = nodesOnScreen().mapNotNull { it.text?.toString() }.toList()

/**
 * The on-screen bounds of the control labelled exactly [label], or `null` if no window has one: a
 * dialog's title or a hint that merely contains the word is not the button. Compose puts the label
 * on a child of the clickable node, so the control is the label's nearest clickable ancestor.
 *
 * ```
 * clickableBoundsInScreen("Rename")?.bottom
 * ```
 */
internal fun clickableBoundsInScreen(label: String): Rect? = nodesOnScreen()
  .filter { it.text?.toString() == label }
  .mapNotNull { labelNode -> labelNode.selfAndAncestors().firstOrNull { it.isClickable } }
  .firstOrNull()
  ?.let(::boundsInScreen)
