package app.gamenative.companion.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.companion.CompanionTap
import app.gamenative.companion.Header
import app.gamenative.companion.Label
import app.gamenative.companion.Panel
import app.gamenative.companion.Snapshot
import app.gamenative.companion.Unavailable
import app.gamenative.companion.Wow
import app.gamenative.companion.WowButton
import app.gamenative.companion.objects
import app.gamenative.companion.str
import org.json.JSONObject

/**
 * The quest log by zone, like the PC's quest tracker / log: level, title, objectives, then the quest's text and
 * what you can do with it. Actions go through Quests.lua's two rows: "qpick" (column = the quest's slot) picks
 * the quest, then "qact" does something with the pick (0 watch, 1 unwatch, 2 focus, 3 arm abandon,
 * 4 abandon, 38 share, 39 unfocus).
 */
@Composable
fun QuestsRoom(snap: Snapshot) {
    val q = snap.doc("quests")?.optJSONObject("q")
    val list = q?.optJSONArray("list").objects()
    if (list.isEmpty()) {
        Panel(Modifier.fillMaxSize()) { Unavailable("Your quest log is empty, or the game hasn't sent it yet.") }
        return
    }
    var openId by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val open = list.firstOrNull { it.optInt("id") == openId }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Panel(Modifier.weight(1f).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Label("${list.size} / ${q?.optInt("maxQ", 25) ?: 25} quests", 13f, Wow.Label)
                }
                list.groupBy { it.str("zone") ?: "Other" }.forEach { (zone, quests) ->
                    item { Header(zone) }
                    items(quests) { quest ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openId = quest.optInt("id")
                                    confirm = false
                                    note = null
                                }
                                .padding(vertical = 3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Label("[${quest.optInt("lv")}] ", 13f, levelColor(quest.optInt("lv"), q?.optInt("plv") ?: 0))
                                val title = quest.str("title") ?: ""
                                Label(
                                    title + if (quest.optBoolean("ready")) "  (Complete)" else if (quest.optBoolean("failed")) "  (Failed)" else "",
                                    14f,
                                    if (quest.optInt("id") == openId) Wow.Gold else if (quest.optBoolean("ready")) Wow.Good else Wow.Text,
                                    Modifier.weight(1f),
                                )
                                if (quest.optInt("watch") > 0) Label("◆", 11f, Wow.Mid)
                            }
                            if (quest.optInt("watch") > 0 || quest.optInt("id") == openId) {
                                quest.optJSONArray("obj").objects().forEach { o ->
                                    Label("- " + (o.str("t") ?: ""), 12f, if (o.optBoolean("f")) Wow.Good else Wow.Label, Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (open != null) {
            Panel(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Label(open.str("title") ?: "", 17f, Wow.Gold, lines = 2)
                        Label("Level ${open.optInt("lv")}  -  ${open.str("zone") ?: ""}", 12f, Wow.Dim)
                        open.str("otext")?.let {
                            Header("Objectives")
                            Label(it, 13f, Wow.Text, lines = 12)
                        }
                        open.optJSONArray("obj").objects().forEach { o ->
                            Label("- " + (o.str("t") ?: ""), 13f, if (o.optBoolean("f")) Wow.Good else Wow.Text, lines = 2)
                        }
                        open.str("desc")?.let {
                            Header("Description")
                            Label(it, 13f, Wow.Label, lines = 40)
                        }
                    }
                    note?.let { Unavailable(it) }
                    QuestActions(open, confirm, onConfirm = { confirm = it }, onNote = { note = it })
                }
            }
        }
    }
}

private fun levelColor(level: Int, mine: Int): androidx.compose.ui.graphics.Color = when {
    mine == 0 -> Wow.Dim
    level >= mine + 5 -> Wow.Bad
    level >= mine + 3 -> androidx.compose.ui.graphics.Color(0xFFFF8040)
    level >= mine - 2 -> Wow.Gold
    level >= mine - 8 -> Wow.Good
    else -> Wow.Dim
}

private fun act(quest: JSONObject, col: Int): Boolean = CompanionTap.taps("qpick" to quest.optInt("slot"), "qact" to col)

@Composable
private fun QuestActions(quest: JSONObject, confirm: Boolean, onConfirm: (Boolean) -> Unit, onNote: (String?) -> Unit) {
    val fail = "The game isn't taking quest actions right now."
    if (confirm) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("Abandon this quest?", 14f, Wow.Bad, Modifier.weight(1f))
            WowButton("Abandon", color = Wow.Bad) {
                // arm with the pick, then the confirm (which needs no pick)
                val ok = act(quest, 3) && CompanionTap.tap("qact", 4)
                onNote(if (ok) null else fail)
                onConfirm(false)
            }
            WowButton("Keep", color = Wow.Label) { onConfirm(false) }
        }
        return
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val watched = quest.optInt("watch") > 0
        WowButton(if (watched) "Untrack" else "Track") { onNote(if (act(quest, if (watched) 1 else 0)) null else fail) }
        WowButton("Focus") { onNote(if (act(quest, 2)) null else fail) }
        if (quest.optBoolean("push")) WowButton("Share") { onNote(if (act(quest, 38)) null else fail) }
        if (quest.optBoolean("canAbandon")) WowButton("Abandon", color = Wow.Bad) { onConfirm(true) }
    }
}
