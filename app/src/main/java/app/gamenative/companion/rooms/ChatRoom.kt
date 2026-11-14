package app.gamenative.companion.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import app.gamenative.companion.CompanionTap
import app.gamenative.companion.Label
import app.gamenative.companion.Panel
import app.gamenative.companion.Snapshot
import app.gamenative.companion.SubTabs
import app.gamenative.companion.Unavailable
import app.gamenative.companion.Wow
import app.gamenative.companion.WowButton
import app.gamenative.companion.objects
import app.gamenative.companion.str
import org.json.JSONObject

/**
 * The chat, in the game's channel colors, and sending: the quick phrases and anything typed (with Android's own
 * keyboard, here on the bottom screen). Chat.lua sends a line from inside the tap on its own pad cells: the
 * phrases on "chq" 0-10, the chat type on "chq" 16-22, the letters spelled one cell each over "chq" 24-39,
 * "chr" and "chs", then Send ("chq" 23). It only listens while the chat holds the controller (Quests.lua's
 * shared guest columns on "qact": 14 hold, 15 let go).
 */
private val TYPES = listOf("Say" to 16, "Party" to 17, "Raid" to 18, "Guild" to 19, "Yell" to 20, "Reply" to 21, "Whisper" to 22)
private val PRESETS = listOf("Hello!", "Thanks!", "On my way", "Ready", "One moment", "Need help?", "GG", "BRB", "Yes", "No", "lol")

private val CHANNEL_COLORS = mapOf(
    "say" to Color(0xFFFFFFFF), "yell" to Color(0xFFFF4040), "party" to Color(0xFFAAAAFF), "raid" to Color(0xFFFF7F00),
    "guild" to Color(0xFF40FF40), "officer" to Color(0xFF40C040), "whisper" to Color(0xFFFF80FF), "bn" to Color(0xFF00FAF6),
    "channel" to Color(0xFFFFC0C0), "loot" to Color(0xFF00AA00), "system" to Color(0xFFFFFF00), "info" to Color(0xFFFFFF00),
    "emote" to Color(0xFFFF8040), "error" to Color(0xFFFF2020), "skill" to Color(0xFF5555FF), "money" to Color(0xFFFFFF00),
    "xp" to Color(0xFF6A5ACD), "combat" to Color(0xFFFFFFFF),
)

private fun lineColor(ch: String?): Color {
    val k = ch?.lowercase() ?: return Wow.Text
    CHANNEL_COLORS[k]?.let { return it }
    return CHANNEL_COLORS.entries.firstOrNull { k.startsWith(it.key) }?.value ?: Wow.Text
}

/** The pad cell that types one character, or null for one the add-on can't type. */
private fun cellFor(c: Char): Pair<String, Int>? {
    val code = c.code
    return when (code) {
        in 32..47 -> "chq" to (code - 8)
        in 48..87 -> "chr" to (code - 48)
        in 88..126 -> if (code == 124) null else "chs" to (code - 88)
        else -> null
    }
}

private fun send(text: String, typeCol: Int, target: String?): String? {
    val cells = ArrayList<Pair<String, Int>>()
    cells += "qact" to 14 // the chat takes the controller
    cells += "chq" to 13 // clear
    if (target != null) {
        cells += "chq" to 14 // letters now spell the whisper target
        target.forEach { ch -> cellFor(ch)?.let { cells += it } }
        cells += "chq" to 15 // back to the message
    }
    cells += "chq" to typeCol
    var skipped = false
    text.forEach { ch -> cellFor(ch)?.let { cells += it } ?: run { skipped = true } }
    cells += "chq" to 23 // send
    cells += "qact" to 15 // and lets go
    if (!CompanionTap.taps(*cells.toTypedArray())) return "The chat isn't taking messages right now."
    return if (skipped) "Some characters can't be sent from here and were left out." else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatRoom(snap: Snapshot) {
    val lines = snap.chat.lines.values.toList()
    val state = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var keyboard by remember { mutableStateOf(false) }
    var typingTarget by remember { mutableStateOf(false) }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.size - 1) }
    val ack = snap.doc("chatt")
    Column(Modifier.fillMaxSize()) {
        Panel(Modifier.weight(1f).fillMaxWidth()) {
            if (lines.isEmpty()) {
                Unavailable("No chat yet.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    items(lines) { l -> ChatLine(l) }
                }
            }
        }
        Panel(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column {
                SubTabs(TYPES.map { it.first }, type) { type = it }
                val whisper = TYPES[type].first == "Whisper"
                if (whisper) {
                    Field(target, "To…", typingTarget) { typingTarget = true; keyboard = true }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(draft, "Message…", !typingTarget || !whisper, Modifier.weight(1f)) { typingTarget = false; keyboard = true }
                    WowButton("Send", Modifier.padding(start = 6.dp), enabled = draft.isNotBlank()) {
                        note = send(draft, TYPES[type].second, target.takeIf { whisper && it.isNotBlank() })
                        draft = ""
                        keyboard = false
                    }
                }
                if (keyboard) {
                    Keyboard(
                        onKey = { k -> if (typingTarget && whisper) target = (target + k).take(48) else draft = (draft + k).take(255) },
                        onBack = { if (typingTarget && whisper) target = target.dropLast(1) else draft = draft.dropLast(1) },
                    )
                }
                if (!keyboard) FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PRESETS.forEachIndexed { i, p ->
                        WowButton(p, color = Wow.Label) {
                            note = if (CompanionTap.taps("qact" to 14, "chq" to TYPES[type].second, "chq" to i, "qact" to 15)) null
                            else "The chat isn't taking messages right now."
                        }
                    }
                }
                val why = ack?.takeIf { !it.optBoolean("ok", true) }?.str("why")
                (note ?: why)?.let { Unavailable(it) }
            }
        }
    }
}

/** The text being typed (the second screen can't take Android's keyboard without taking the controller). */
@Composable
private fun Field(value: String, hint: String, active: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (active) Color(0xFF16161A) else Color(0xFF0E0E10))
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (value.isEmpty()) Label(hint, 14f, Wow.Dim) else Label(value + if (active) "|" else "", 14f, Wow.Text, lines = 2)
    }
}

private val ROWS_LOWER = listOf("1234567890", "qwertyuiop", "asdfghjkl'", "zxcvbnm,.?")
private val ROWS_UPPER = listOf("!@#$%&*()-", "QWERTYUIOP", "ASDFGHJKL\"", "ZXCVBNM;:/")

/** A small keyboard in the UI's look. */
@Composable
private fun Keyboard(onKey: (String) -> Unit, onBack: () -> Unit) {
    var shift by remember { mutableStateOf(false) }
    val rows = if (shift) ROWS_UPPER else ROWS_LOWER
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { ch -> Key(ch.toString(), Modifier.weight(1f)) { onKey(ch.toString()) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key(if (shift) "abc" else "Shift", Modifier.weight(1.5f)) { shift = !shift }
            Key("Space", Modifier.weight(5f)) { onKey(" ") }
            Key("Del", Modifier.weight(1.5f)) { onBack() }
        }
    }
}

@Composable
private fun Key(text: String, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier
            .background(Color(0xFF1C1C21))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Label(text, 15f, Wow.Text) }
}

@Composable
private fun ChatLine(l: JSONObject) {
    val color = lineColor(l.str("ch"))
    val text: AnnotatedString = buildAnnotatedString {
        l.str("hm")?.let { withStyle(SpanStyle(color = Wow.Dim)) { append("$it ") } }
        val who = l.str("who")
        if (who != null && l.str("style") != "plain") {
            withStyle(SpanStyle(color = Wow.classColor(l.str("cls")))) { append("[$who]") }
            append(": ")
        }
        // item / spell / quest links in their own colors
        val body = l.str("text") ?: ""
        val links = l.optJSONArray("lc").objects()
        if (links.isEmpty()) {
            withStyle(SpanStyle(color = color)) { append(body) }
        } else {
            var rest = body
            links.forEach { link ->
                val name = "[" + (link.str("n") ?: "") + "]"
                val at = rest.indexOf(name)
                if (at >= 0) {
                    withStyle(SpanStyle(color = color)) { append(rest.substring(0, at)) }
                    withStyle(SpanStyle(color = Wow.hex(link.str("c")))) { append(name) }
                    rest = rest.substring(at + name.length)
                }
            }
            withStyle(SpanStyle(color = color)) { append(rest) }
        }
    }
    Text(text, style = Wow.style(14f), modifier = Modifier.padding(vertical = 1.dp))
}
