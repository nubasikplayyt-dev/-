package app.lingvo.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lingvo.LingvoApp
import app.lingvo.core.VocabEntry
import app.lingvo.images.WordImages
import app.lingvo.ui.rememberCourseSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Картинка к слову на карточке.
 *
 * - картинка есть — она показана крупно; нажатие открывает её на весь экран;
 * - картинка ещё ищется — короткий индикатор;
 * - не нашлась — кнопка «Повторить» (задача ставится в очередь заново);
 * - ничего нет — ничего и не показывается.
 */
@Composable
fun WordImage(entry: VocabEntry, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? LingvoApp ?: return
    val images = app.container.wordImages
    val spec = rememberCourseSpec(app.container, entry.language) ?: return
    val key = remember(entry.id, entry.term, entry.article, spec.languageCode) { images.keyFor(entry, spec) }
    val revision by images.revision.collectAsState()
    val statusFlow = remember(key) { images.observeStatus(key) }
    val status by statusFlow.collectAsStateWithLifecycle(WordImages.Status.NONE)
    val settings by app.container.settings.flow.collectAsStateWithLifecycle(null)
    var open by remember(key) { mutableStateOf(false) }

    val bitmap by produceState<ImageBitmap?>(null, key, revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = images.fileFor(key)
                if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
            }.getOrNull()
        }
    }

    val b = bitmap
    when {
        b != null -> {
            // Картинка видна всегда; нажатие открывает её на весь экран.
            Image(
                b, "Картинка к слову «${entry.displayTerm}»",
                modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { open = true },
                contentScale = ContentScale.Crop,
            )
            if (open) {
                Dialog(onDismissRequest = { open = false }) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface).clickable { open = false }.padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(b, "Картинка к слову «${entry.displayTerm}»", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
        status == WordImages.Status.QUEUED || status == WordImages.Status.RUNNING -> {
            Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status == WordImages.Status.RUNNING) "Картинка ищется…" else "Картинка в очереди",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        status == WordImages.Status.FAILED -> {
            Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Картинка не нашлась", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { settings?.let { images.retry(entry, spec, it) } }) { Text("Повторить") }
            }
        }
    }
}
