package app.investwatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.investwatch.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Бумага из уведомления или deep link investwatch://asset/<id>. */
    private var openAssetId by mutableStateOf<String?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openAssetId = assetIdOf(intent)

        setContent {
            AppRoot(
                openAssetId = openAssetId,
                onOpenHandled = { openAssetId = null },
                onRequestNotifications = ::requestNotifications,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAssetId = assetIdOf(intent)
    }

    /**
     * Разрешение на уведомления спрашиваем при первом добавлении в избранное —
     * там понятно, зачем оно нужно.
     */
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun assetIdOf(intent: Intent?): String? {
        intent?.getStringExtra(EXTRA_ASSET_ID)?.let { return it }
        val data = intent?.data ?: return null
        if (data.scheme != "investwatch") return null
        return data.lastPathSegment?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val EXTRA_ASSET_ID = "asset_id"
    }
}
