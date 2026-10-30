package app.portfolio

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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.portfolio.ui.AppRoot
import app.portfolio.ui.AppViewModel
import app.portfolio.work.Notifications

class MainActivity : ComponentActivity() {

    /** Бумага из уведомления: открываем сразу её экран. */
    private var pendingAssetId by mutableStateOf<String?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAssetId = intent?.getStringExtra(EXTRA_ASSET_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(this)) {
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val model: AppViewModel = viewModel(factory = AppViewModel.factory(applicationContext))
            AppRoot(
                model = model,
                openAssetId = pendingAssetId,
                onOpenHandled = { pendingAssetId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAssetId = intent.getStringExtra(EXTRA_ASSET_ID)
    }

    companion object {
        const val EXTRA_ASSET_ID = "asset_id"
    }
}
