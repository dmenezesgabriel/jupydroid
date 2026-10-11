package com.jupydroid.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.ThemeSettings
import com.jupydroid.app.domain.model.resolvesToDark
import com.jupydroid.app.import.SharedNotebook
import com.jupydroid.app.import.sharedNotebookFrom
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.RemoteImagesResolved

class MainActivity : ComponentActivity() {
  /**
   * The process's container, never this activity's: a rotation relaunches the activity and a
   * second container would be a second `jupyter_server` (see [JupydroidApplication]).
   */
  internal val appContainer: AppContainer get() = (application as JupydroidApplication).container

  private var sharedNotebook by mutableStateOf<SharedNotebook?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sharedNotebook = sharedNotebookIn(intent)
    enableEdgeToEdge()
    setContent {
      val themeSettings by appContainer.themeSettings.observeSettings().collectAsStateWithLifecycle(ThemeSettings())
      JupyterDroidTheme(darkTheme = themeSettings.override.resolvesToDark(isSystemInDarkTheme())) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          RenderedDocumentsServed {
            MainNavigation(
              appContainer = appContainer,
              sharedNotebook = sharedNotebook,
              onImportConsumed = { sharedNotebook = null },
            )
          }
        }
      }
    }
  }

  /** What a rendered document reaches past itself on every screen: where its links go, and its web images. */
  @Composable
  private fun RenderedDocumentsServed(content: @Composable () -> Unit) {
    RenderedLinksFollowed(appContainer.externalLinkOpener) {
      RemoteImagesResolved(appContainer.remoteImageReader, content)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    sharedNotebook = sharedNotebookIn(intent)
  }

  /** Only the URI and its name here: the bytes are read off the main thread, by the import itself. */
  private fun sharedNotebookIn(intent: Intent): SharedNotebook? {
    val uri = intent.data ?: return null
    return sharedNotebookFrom(uri.toString(), displayNameOf(uri))
  }

  private fun displayNameOf(uri: Uri): String? = contentResolver
    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    ?: uri.lastPathSegment
}
