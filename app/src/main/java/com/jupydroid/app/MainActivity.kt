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
import androidx.lifecycle.lifecycleScope
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.space.SpaceProbe
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.import.importTitleFrom
import com.jupydroid.app.theme.JupyterDroidTheme
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val appContainer: AppContainer by lazy {
    AppContainer(
      notebookRepository = DiskNotebookRepository(File(applicationContext.filesDir, "notebooks")),
    )
  }
  private var importTitle by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    installBaseSpace()
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

  /**
   * Walks the Step 8 runtime chain and logs each stage. Off the main thread and not
   * awaited: no UI depends on it yet, and every stage is idempotent. Replaced by real
   * Space UI as those screens land.
   */
  private fun installBaseSpace() {
    lifecycleScope.launch { SpaceProbe(applicationContext).run() }
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
