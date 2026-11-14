package app.gamenative.companion

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * The game -> companion half of the link.
 *
 * The DS Forever add-ons (WowCompanion*) write every document as one or more log lines through
 * C_Log.LogMessage, which land in `_forever_/Logs/General.log`:
 *
 *     11/13 16:31:45.728  [N][Lua] WCB1|<seq>|<part>/<parts>|<json>
 *
 * Long documents are cut into parts carried by consecutive sequence numbers. The game only writes the
 * file in 48 KiB blocks, so the add-ons pad it ("WCB1|pad|xxx…") to push what they wrote out to disk.
 * This reader tails the file, glues the parts back together and keeps the newest document of each type.
 */
object CompanionLink {
    /** Set by the launcher before the game starts: `<game root>/_forever_/Logs/General.log`. */
    @Volatile
    var logFile: File? = null

    private const val PREFS = "wow_companion"
    private const val KEY_LOG = "log_file"

    /** Every doc type -> its newest document. */
    val docs: StateFlow<Snapshot> get() = state
    private val state = MutableStateFlow(Snapshot())

    @Volatile
    private var reader: Thread? = null

    @Volatile
    private var running = false

    fun remember(context: Context, file: File) {
        logFile = file
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LOG, file.path).apply()
    }

    /** Another game: no companion. */
    fun forget(context: Context) {
        logFile = null
        stop()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LOG).apply()
    }

    fun recall(context: Context): File? {
        logFile?.let { return it }
        val path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOG, null) ?: return null
        return File(path).also { logFile = it }
    }

    fun start(context: Context) {
        if (running) return
        val file = recall(context) ?: return
        running = true
        reader = Thread({ tail(file) }, "wow-companion-log").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        reader?.interrupt()
        reader = null
    }

    // ------------------------------------------------------------------ reading

    /** The game's log keeps growing while you move (~60 KB a flush); past this it is cut back to nothing. */
    private const val MAX_LOG_BYTES = 32L * 1024 * 1024

    /** More than this unread (a cold start on an old log, or a hole after a cut): skip to the end. */
    private const val MAX_BACKLOG = 4L * 1024 * 1024

    private fun tail(file: File) {
        var pos = -1L
        val pending = StringBuilder()
        val buffer = ByteArray(256 * 1024)
        val parts = Parts()
        while (running) {
            try {
                if (!file.exists()) {
                    sleep(1000)
                    continue
                }
                var size = file.length()
                if (size > MAX_LOG_BYTES) {
                    cut(file)
                    size = file.length()
                    pos = 0
                    pending.setLength(0)
                }
                if (pos < 0 || pos > size) {
                    // first look, or the game started over (it empties the log at every launch)
                    pos = if (pos < 0 && size > MAX_BACKLOG) size - MAX_BACKLOG else 0
                    pending.setLength(0)
                    state.value = state.value.copy(started = SystemClock.elapsedRealtime())
                } else if (size - pos > MAX_BACKLOG) {
                    pos = size - MAX_BACKLOG
                    pending.setLength(0)
                }
                if (size == pos) {
                    sleep(100)
                    continue
                }
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(pos)
                    while (running) {
                        val n = raf.read(buffer)
                        if (n <= 0) break
                        pos += n
                        pending.append(String(buffer, 0, n, StandardCharsets.UTF_8))
                        var start = 0
                        while (true) {
                            val nl = pending.indexOf("\n", start)
                            if (nl < 0) break
                            line(pending.substring(start, nl), parts)
                            start = nl + 1
                        }
                        pending.delete(0, start)
                        // a hole the cut left behind is all zero bytes: never let it pile up
                        if (pending.length > 1_000_000) pending.setLength(0)
                    }
                }
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                Timber.w(e, "companion: log read")
                sleep(500)
            }
        }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            running = false
        }
    }

    private fun cut(file: File) {
        try {
            RandomAccessFile(file, "rw").use { it.setLength(0) }
        } catch (e: Exception) {
            Timber.w(e, "companion: could not cut the log")
        }
    }

    /** Fragments waiting for the rest of their document. */
    private class Parts {
        var seq = -1
        var want = 0
        val text = StringBuilder()
    }

    private fun line(raw: String, parts: Parts) {
        val at = raw.indexOf("WCB1|")
        if (at < 0) return
        val s = raw.substring(at + 5).trimEnd('\r', '\u0000')
        val bar1 = s.indexOf('|')
        if (bar1 <= 0) return
        val seq = s.substring(0, bar1).toIntOrNull() ?: return // "pad" / "probe…" lines
        val bar2 = s.indexOf('|', bar1 + 1)
        if (bar2 < 0) return
        val frac = s.substring(bar1 + 1, bar2)
        val slash = frac.indexOf('/')
        if (slash < 0) return
        val part = frac.substring(0, slash).toIntOrNull() ?: return
        val total = frac.substring(slash + 1).toIntOrNull() ?: return
        val body = s.substring(bar2 + 1)
        if (part == 1) {
            parts.text.setLength(0)
            parts.text.append(body)
            parts.seq = seq
            parts.want = total
        } else if (parts.seq >= 0 && seq == parts.seq + 1 && part <= parts.want) {
            parts.text.append(body)
            parts.seq = seq
        } else {
            parts.seq = -1 // a part went missing: drop the document
            return
        }
        if (part == total) {
            val json = parts.text.toString()
            parts.seq = -1
            try {
                accept(JSONObject(json))
            } catch (e: Exception) {
                Timber.v("companion: bad document")
            }
        }
    }

    // ------------------------------------------------------------------ the store

    private fun accept(doc: JSONObject) {
        val type = doc.optString("type")
        if (type.isEmpty()) return
        synchronized(this) {
            var s = state.value
            val docs = HashMap(s.docs)
            when (type) {
                "full" -> {
                    // three kinds ride this type (the whole snapshot, bags alone, the bar alone): keep each section
                    val full = HashMap(s.full)
                    for (key in doc.keys()) {
                        if (key != "type" && key != "v" && key != "reason") doc.opt(key)?.let { full[key] = it }
                    }
                    s = s.copy(full = full)
                }
                "pos" -> s = s.copy(pos = doc.optJSONObject("map"))
                "chat" -> s = s.copy(chat = mergeChat(s.chat, doc.optJSONObject("chat")))
                // Social sends one document per list (friends, guild, ignore…): keep each
                "social" -> {
                    docs[type] = doc
                    doc.str("k")?.let { docs["social:$it"] = doc }
                }
                else -> docs[type] = doc
            }
            if (type != "full" && type != "pos" && type != "chat") s = s.copy(docs = docs)
            findPad(doc, 0)?.let { s = s.copy(pad = it) }
            state.value = s.copy(seen = SystemClock.elapsedRealtime(), version = s.version + 1)
        }
    }

    private fun mergeChat(old: ChatLog, chat: JSONObject?): ChatLog {
        if (chat == null) return old
        val session = chat.optLong("s")
        val byIndex: LinkedHashMap<Int, JSONObject> = if (session != old.session) LinkedHashMap() else LinkedHashMap(old.lines)
        val lines = chat.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until lines.length()) {
            val l = lines.optJSONObject(i) ?: continue
            byIndex[l.optInt("i")] = l
        }
        while (byIndex.size > 400) byIndex.remove(byIndex.keys.first())
        return ChatLog(session, byIndex, chat.optJSONObject("can") ?: old.can)
    }

    /** The pad's geometry rides in most documents, somewhere: the newest one wins. */
    private fun findPad(o: JSONObject, depth: Int): JSONObject? {
        val pad = o.optJSONObject("pad")
        if (pad != null && pad.has("rows") && pad.has("cw")) return pad
        if (depth >= 3) return null
        for (key in o.keys()) {
            val child = o.optJSONObject(key) ?: continue
            findPad(child, depth + 1)?.let { return it }
        }
        return null
    }
}

data class ChatLog(
    val session: Long = 0,
    val lines: Map<Int, JSONObject> = emptyMap(),
    val can: JSONObject? = null,
)

/** Everything the companion knows, replaced as a whole on every document (cheap: the maps are shallow). */
data class Snapshot(
    val docs: Map<String, JSONObject> = emptyMap(),
    val full: Map<String, Any> = emptyMap(),
    val pos: JSONObject? = null,
    val chat: ChatLog = ChatLog(),
    val pad: JSONObject? = null,
    val started: Long = 0,
    val seen: Long = 0,
    val version: Long = 0,
) {
    fun doc(type: String): JSONObject? = docs[type]
    fun player(): JSONObject? = full["player"] as? JSONObject
    fun bags(): JSONObject? = full["bags"] as? JSONObject
    fun map(): JSONObject? = full["map"] as? JSONObject
    fun equipped(): JSONObject? = (full["equipped"] as? JSONObject) ?: bags()?.optJSONObject("equipped")
}

// ---------------------------------------------------------------------- small JSON helpers

fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(it) }
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(optString(i))
    return out
}

/** A string field, or null when it's missing / JSON null / empty. */
fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotEmpty() }
}

fun JSONObject.num(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val v = opt(key)
    return (v as? Number)?.toDouble() ?: v?.toString()?.toDoubleOrNull()
}
