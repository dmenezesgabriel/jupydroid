package com.jupydroid.app.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The names of the terminals this process believes alive in each Space, and their count.
 *
 * **A set of names, not an integer - because a terminal's end arrives by more roads than
 * `close`.** A shell that exits reports `["disconnect", 1]` and is gone from the server's list
 * (`NamedTermManager.on_eof` pops it); a socket that dies says nothing about the pty
 * (`TermSocket.on_close` drops only the client). Only `running()`'s answer distinguishes those,
 * so [reconciled] replaces the belief outright and [ended] removes a name whose end was
 * witnessed - set removes make "the `Ended` a `close` causes" a free no-op rather than a
 * double decrement.
 *
 * Still state outside the gateways for the reason this class has always been one:
 * `BaseSpaceTerminalGateway` builds a fresh `RealTerminalGateway` per running server, so the
 * count must outlive one server instance. Name-reuse across generations is harmless - the
 * server mints `"%d"` and a re-minted name is a live name.
 *
 * **Keyed by Space as well as by name**, because every Space's server mints `"1"` first: one set of
 * bare names would count base's `"1"` and Thesis's `"1"` as a single shell, and [reconciled] with
 * Thesis's list would erase base's - letting `IdleServerStopper` stop base under an open shell
 * (`ActiveTerminalsTest.reconcilingOneSpaceKeepsAnothersTerminals`).
 *
 * Owned by `AppContainer`, moved by `ActivityTrackedTerminalGateway`, read by `IdleServerStopper`.
 *
 * ```
 * val terminals = ActiveTerminals()
 * terminals.minted("base", "1") // a shell now exists in base
 * terminals.ended("base", "1")  // it doesn't anymore
 * ```
 */
class ActiveTerminals {
  private val liveBySpace = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

  /** How many terminals this process believes alive across every Space - a `Flow`, the map being the only state. */
  val open: Flow<Int> = liveBySpace.map { spaces -> spaces.values.sumOf { it.size } }.distinctUntilChanged()

  fun minted(spaceId: String, name: String) {
    liveBySpace.update { it + (spaceId to it[spaceId].orEmpty() + name) }
  }

  fun ended(spaceId: String, name: String) {
    liveBySpace.update { it + (spaceId to it[spaceId].orEmpty() - name) }
  }

  /** [spaceId]'s server's own answer: whatever it still lists is the whole of what is alive *there*. */
  fun reconciled(spaceId: String, names: Collection<String>) {
    liveBySpace.update { it + (spaceId to names.toSet()) }
  }
}
