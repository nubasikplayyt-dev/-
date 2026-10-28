package app.lingvo.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lingvo.LingvoApp
import app.lingvo.core.VocabEntry
import app.lingvo.ui.rememberCourseSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Картинка к слову из кэша. Пока её нет (ещё рисуется, генерация выключена или не удалась) — ничего не
 * показывается. Как только фоновая задача сохранит файл, картинка появится сама.
 */
@Composable
fun WordImage(entry: VocabEntry, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? LingvoApp ?: return
    val images = app.container.wordImages
    val spec = rememberCourseSpec(app.container, entry.language) ?: return
    val revision by images.revision.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, entry.id, entry.term, entry.article, spec.languageCode, revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = images.fileFor(entry, spec)
                if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    val b = bitmap ?: return
    Image(
        b, contentDescription = "Картинка к слову «${entry.displayTerm}»",
        modifier = modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Fit,
    )
}
