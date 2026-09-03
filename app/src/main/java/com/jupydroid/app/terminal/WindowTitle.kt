package com.jupydroid.app.terminal

/**
 * The window title a shell sets with `OSC 0`, `1` or `2`, and the stack `CSI 22 t` puts it on.
 *
 * Transcribed from `TerminalEmulator.setTitle`, `getTitle` and the `22`/`23` arms of `doCsi`'s `t`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 *
 * **A class of its own rather than two fields on [TerminalScreen]**, because two callers reach it
 * from opposite directions - [OperatingSystemCommand] sets it and [TerminalReporting] stacks it -
 * and because it is the one piece of terminal state that survives `reset`: the reference's
 * `reset()` clears the colours and the modes and leaves the title alone.
 *
 * ```
 * val title = WindowTitle(notices)
 * title.set("~/work")       // the notice fires, because the title changed
 * title.push(); title.pop() // and again, only if the pop lands on a different one
 * ```
 */
class WindowTitle(private val notices: TerminalNotices) {
  /** What the title is, or `null` if nothing has set one. */
  var current: String? = null
    private set

  private val stack = ArrayDeque<String?>()

  /** Set the title, telling [TerminalNotices] **only when it actually changed**. */
  fun set(newTitle: String?) {
    val oldTitle = current
    current = newTitle
    if (oldTitle != newTitle) notices.onTitleChanged(oldTitle, newTitle)
  }

  /**
   * `CSI 22 t`. The stack holds [MAX_DEPTH] and **drops the oldest** when it would hold more, which
   * is `mTitleStack.remove(0)` upstream rather than refusing the push.
   */
  fun push() {
    stack.addLast(current)
    if (stack.size > MAX_DEPTH) stack.removeFirst()
  }

  /** `CSI 23 t`. A pop with nothing on the stack does nothing at all. */
  fun pop() {
    if (stack.isNotEmpty()) set(stack.removeLast())
  }

  private companion object {
    /** Upstream's limit, and upstream's reason: "Limit size". */
    const val MAX_DEPTH = 20
  }
}
