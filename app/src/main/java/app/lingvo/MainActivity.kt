package app.lingvo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.lingvo.notify.Notifications
import app.lingvo.ui.LingvoNavHost
import app.lingvo.ui.theme.LingvoTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** Внешние данные для импорта (файл из Sharesheet / файлового менеджера или текст). */
sealed interface IncomingImport {
    data class FromUri(val uri: Uri) : IncomingImport
    data class FromText(val text: String) : IncomingImport
}

class MainActivity : ComponentActivity() {
    private val pendingRoute = MutableStateFlow<String?>(null)
    private val pendingImport = MutableStateFlow<IncomingImport?>(null)

    val container: AppContainer get() = (application as LingvoApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            LingvoTheme {
                LingvoNavHost(
                    container = container,
                    pendingRoute = pendingRoute,
                    onRouteConsumed = { pendingRoute.value = null },
                    pendingImport = pendingImport,
                    onImportConsumed = { pendingImport.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifications.EXTRA_OPEN)?.let { pendingRoute.value = it }
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { pendingImport.value = IncomingImport.FromUri(it) }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> pendingImport.value = IncomingImport.FromUri(stream)
                    !text.isNullOrBlank() -> pendingImport.value = IncomingImport.FromText(text)
                }
            }
        }
    }
}
