package com.jupydroid.app.ui.environments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.SpaceInterpreter
import com.jupydroid.app.theme.Spacing
import com.jupydroid.extension.api.FocusRingTextButton

/**
 * One of the Space's own interpreters' row: its path as a process in the Space execs it, which C
 * library it runs against - the glibc half of the Space's split or the server's musl one
 * (ADR-0166) - then Register, which hands [onRegister] the path, or the kernel's name once a
 * spec's `argv[0]` is this interpreter. Unlike an environment's row there is no ipykernel half:
 * the register script's own refusal says it when a kernel cannot start.
 */
@Composable
internal fun InterpreterRow(interpreter: SpaceInterpreter, enabled: Boolean, onRegister: (String) -> Unit) {
  Column(Modifier.padding(top = Spacing.xs)) {
    Text(interpreter.path, style = MaterialTheme.typography.bodyMedium)
    Text(if (interpreter.isGnu) "glibc interpreter" else "musl interpreter", style = MaterialTheme.typography.bodySmall)
    interpreter.registeredAs?.let {
      Text(registeredLabel(it), style = MaterialTheme.typography.bodySmall)
      return@Column
    }
    FocusRingTextButton(
      onClick = { onRegister(interpreter.path) },
      enabled = enabled,
      modifier = Modifier.semantics { contentDescription = registerLabel(interpreter.path) },
    ) {
      Text("Register as kernel")
    }
  }
}
