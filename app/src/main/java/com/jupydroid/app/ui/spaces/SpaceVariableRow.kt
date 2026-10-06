package com.jupydroid.app.ui.spaces

import com.jupydroid.app.domain.model.variableNameRefusal
import com.jupydroid.app.domain.model.variableValueRefusal

/**
 * One line of the Settings dialog's variable list, as typed - which may not yet be a variable any
 * Space can hold. Kept apart from the composable that draws it, so the rules are a JVM test.
 */
data class SpaceVariableRow(val name: String, val value: String)

/**
 * [variables] as rows, in the order they were added.
 *
 * ```
 * variableRowsOf(space.settings.environmentVariables)
 * ```
 */
fun variableRowsOf(variables: Map<String, String>): List<SpaceVariableRow> =
  variables.map { (name, value) -> SpaceVariableRow(name, value) }

/** The rows as the settings store them; call only once [refusals] holds none. */
fun List<SpaceVariableRow>.toVariables(): Map<String, String> = associate { it.name to it.value }

/**
 * Why each row cannot be saved, or null for one that can - index for index.
 *
 * The name and value rules are the domain's own, so Save is held for exactly what the port refuses.
 * A name already given on an earlier row is refused here, because a map would keep only the last.
 *
 * ```
 * rows.refusals().all { it == null }   // Save enabled
 * ```
 */
fun List<SpaceVariableRow>.refusals(): List<String?> = mapIndexed { index, row ->
  variableNameRefusal(row.name)
    ?: duplicateRefusal(row.name, take(index))
    ?: variableValueRefusal(row.name, row.value)
}

private fun duplicateRefusal(name: String, earlier: List<SpaceVariableRow>): String? =
  if (earlier.any { it.name == name }) "'$name' is already set above; expected each name once" else null
