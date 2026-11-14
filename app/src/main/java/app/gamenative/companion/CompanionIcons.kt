package app.gamenative.companion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The game's icons. The add-ons name an icon by its file ID (the game's own number for it); a table made from
 * the community list file turns that into the icon's name, and the picture comes from Wowhead's icon CDN,
 * kept on disk after the first time.
 */
object CompanionIcons {
    private var names: Map<Int, String>? = null
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val client by lazy { OkHttpClient() }
    private val failed = HashSet<String>()

    @Synchronized
    private fun names(context: Context): Map<Int, String> {
        names?.let { return it }
        val map = HashMap<Int, String>(40000)
        try {
            context.assets.open("companion/icons.tsv.gz").use { raw ->
                GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) line.substring(0, tab).toIntOrNull()?.let { map[it] = line.substring(tab + 1) }
                }
            }
        } catch (_: Exception) {
        }
        names = map
        return map
    }

    fun name(context: Context, fileId: Int): String? = names(context)[fileId]

    /** The icon's picture by name ("inv_misc_bag_08") or file ID, or null if it can't be had. */
    suspend fun load(context: Context, key: String): Bitmap? {
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val name = key.toIntOrNull()?.let { name(context, it) } ?: key.takeIf { it.toIntOrNull() == null } ?: return@withContext null
            if (name in failed) return@withContext null
            val dir = File(context.cacheDir, "wow-icons").apply { mkdirs() }
            val file = File(dir, "$name.jpg")
            var bmp: Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
            if (bmp == null) {
                try {
                    val req = Request.Builder().url("https://wow.zamimg.com/images/wow/icons/large/$name.jpg").build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bytes = resp.body?.bytes()
                            if (bytes != null) {
                                file.writeBytes(bytes)
                                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        } else if (resp.code == 404) {
                            synchronized(failed) { failed.add(name) }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            bmp?.also { memory.put(key, it) }
        }
    }
}

/**
 * A game icon in a Tukui slot: a 1px black edge (or the item quality's color), the picture cropped a little like
 * the game's buttons. [icon] is a file ID or an icon name.
 */
@Composable
fun WowIcon(
    icon: Any?,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    border: Color = Color.Black,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val key = when (icon) {
        is Number -> icon.toInt().takeIf { it > 0 }?.toString()
        is String -> icon.takeIf { it.isNotEmpty() }
        else -> null
    }
    var bitmap by remember(key) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) {
        if (key != null) bitmap = CompanionIcons.load(context, key)
    }
    Box(
        modifier
            .size(size)
            .background(Color(0xFF0E0E10))
            .border(1.dp, border),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(1.dp).size(size - 2.dp),
            )
        }
        content()
    }
}
