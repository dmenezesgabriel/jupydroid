package com.jupydroid.extensions.ai.ui

/**
 * The width each chip of a toolbar row gets, given the width its name needs ([natural]) and the [room] the
 * row has. Chips that fit keep their whole width. When they do not, the shortest keep theirs and the
 * longest share what is left evenly, so a long model name gives way before a short mode does. That is the
 * fair share CSS's `flex-shrink` approximates, and what VS Code's chat input does before it compacts a
 * picker (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/widget/input/chatInputPickerResponsiveLayout.ts`).
 *
 * ```
 * chipWidths(listOf(100, 500, 80), room = 400) // [100, 220, 80]
 * ```
 */
internal fun chipWidths(natural: List<Int>, room: Int): List<Int> {
  if (natural.sum() <= room) return natural
  val widths = natural.toMutableList()
  var left = room
  natural.indices.sortedBy { natural[it] }.forEachIndexed { rank, chip ->
    val share = left / (natural.size - rank)
    widths[chip] = minOf(natural[chip], share)
    left -= widths[chip]
  }
  return widths
}
