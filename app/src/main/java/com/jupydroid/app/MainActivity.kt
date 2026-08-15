package com.jupydroid.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.import.importTitleFrom
import com.jupydroid.app.theme.JupyterDroidTheme

class MainActivity : ComponentActivity() {
  /**
   * The process's container, never this activity's: a rotation relaunches the activity and a
   * second container would be a second `jupyter_server` (see [JupydroidApplication]).
   */
  internal val appContainer: AppContainer get() = (application as JupydroidApplication).container

  private var importTitle by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    importTitle = importTitleFrom(intent)
    enableEdgeToEdge()
    setContent {
      JupyterDroidTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            appContainer = appContainer,
            importTitle = importTitle,
            onImportConsumed = { importTitle = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    importTitle = importTitleFrom(intent)
  }

  private fun importTitleFrom(intent: Intent): String? {
    val name = intent.data?.let(::displayNameOf) ?: return null
    return name.takeIf { it.endsWith(".ipynb", ignoreCase = true) }?.let(::importTitleFrom)
  }

  private fun displayNameOf(uri: Uri): String? =
    contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      ?: uri.lastPathSegment
}
