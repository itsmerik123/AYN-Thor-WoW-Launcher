package app.gamenative.companion.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.companion.FlatBar
import app.gamenative.companion.Header
import app.gamenative.companion.Label
import app.gamenative.companion.Panel
import app.gamenative.companion.ROW_ICON
import app.gamenative.companion.Snapshot
import app.gamenative.companion.SubTabs
import app.gamenative.companion.Unavailable
import app.gamenative.companion.Wow
import app.gamenative.companion.WowIcon
import app.gamenative.companion.num
import app.gamenative.companion.objects
import app.gamenative.companion.str
import app.gamenative.companion.strings
import org.json.JSONObject

/** You at a glance: name, level, where you are, gold, XP, buffs, your group and the quests you follow. */
@Composable
fun HomeRoom(snap: Snapshot) {
    val player = snap.player()
    val who = snap.doc("character")?.optJSONObject("c")?.optJSONObject("who")
    val xp = snap.doc("character")?.optJSONObject("c")?.optJSONObject("xp")
    val pos = snap.pos
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val cls = who?.str("clsFile") ?: player?.str("class")
                        Label(who?.str("title") ?: player?.str("name") ?: "…", 22f, Wow.classColor(cls))
                        Spacer(Modifier.weight(1f))
                        Label(Wow.money(player?.optLong("money") ?: 0L), 15f, Wow.Gold)
                    }
                    Label(who?.str("line") ?: "Level ${player?.optInt("level") ?: ""} ${player?.str("class") ?: ""}", 14f, Wow.Label)
                    val zone = pos?.str("zone") ?: player?.str("zone")
                    val sub = pos?.str("subzone") ?: player?.str("subzone")
                    if (zone != null) Label(if (sub != null && sub != zone) "$zone - $sub" else zone, 14f, Wow.Text)
                    if (xp != null && xp.optBoolean("show", true)) {
                        Spacer(Modifier.height(8.dp))
                        XpBar(xp)
                    }
                }
            }
        }
        val auras = snap.doc("auras")
        val buffs = auras?.optJSONArray("b").objects()
        val debuffs = auras?.optJSONArray("d").objects()
        if (buffs.isNotEmpty() || debuffs.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Header("Buffs")
                        AuraRow(buffs, auras?.optDouble("now") ?: 0.0, Wow.Border)
                        if (debuffs.isNotEmpty()) {
                            Header("Debuffs")
                            AuraRow(debuffs, auras?.optDouble("now") ?: 0.0, Wow.Bad)
                        }
                    }
                }
            }
        }
        val party = snap.doc("party")?.optJSONObject("p")?.optJSONArray("m").objects()
        if (party.size > 1) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Header("Group")
                        party.forEach { m ->
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Label(m.str("n") ?: "?", 15f, Wow.classColor(m.str("c")), Modifier.weight(1f))
                                Label("${m.optInt("lv")}", 13f, Wow.Label)
                                Spacer(Modifier.width(8.dp))
                                Label(
                                    when {
                                        m.optBoolean("off") -> "Offline"
                                        m.optBoolean("dead") -> "Dead"
                                        else -> m.str("role")?.takeIf { it != "NONE" }?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                                    },
                                    13f,
                                    if (m.optBoolean("dead") || m.optBoolean("off")) Wow.Bad else Wow.Dim,
                                )
                            }
                        }
                    }
                }
            }
        }
        val watched = snap.doc("quests")?.optJSONObject("q")?.optJSONArray("list").objects().filter { it.optInt("watch") > 0 }
        if (watched.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Header("Following")
                        watched.take(6).forEach { q ->
                            QuestLine(q)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XpBar(xp: JSONObject) {
    val cur = xp.optDouble("cur")
    val max = xp.optDouble("max", 1.0).coerceAtLeast(1.0)
    val rest = xp.optDouble("rest")
    Column {
        FlatBar((cur / max).toFloat(), Wow.Mid, height = 16.dp, brush = Wow.Sweep) {
            Label("${Wow.short(cur)} / ${Wow.short(max)}  (${(cur * 100 / max).toInt()}%)", 12f)
        }
        if (rest > 0) Label("Rested", 12f, Wow.Rested, Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun AuraRow(list: List<JSONObject>, now: Double, border: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        list.take(10).forEach { a ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WowIcon(a.opt("i"), 34.dp, border = border) {
                    val count = a.optInt("c")
                    if (count > 1) Label("$count", 11f, Wow.Text, Modifier.align(Alignment.BottomEnd))
                }
                val left = a.num("x")?.let { it - now } ?: a.num("exp")?.let { it - now }
                if (left != null && left > 0) Label(Wow.duration(left), 11f, if (left < 60) Wow.Gold else Wow.Label)
            }
        }
    }
}

@Composable
fun QuestLine(q: JSONObject) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("[${q.optInt("lv")}] ", 13f, Wow.Dim)
            Label(q.str("title") ?: "", 14f, if (q.optBoolean("ready")) Wow.Good else Wow.Gold, Modifier.weight(1f))
        }
        q.optJSONArray("obj").objects().forEach { o ->
            Label("- " + (o.str("t") ?: ""), 12f, if (o.optBoolean("f")) Wow.Good else Wow.Text, Modifier.padding(start = 10.dp))
        }
    }
}

/** The character window: gear, stats, reputation, skills, currency. */
@Composable
fun CharacterRoom(snap: Snapshot) {
    val c = snap.doc("character")?.optJSONObject("c")
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Gear", "Stats", "Reputation", "Skills", "Currency", "Statistics"), tab) { tab = it }
        Panel(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> GearPage(snap)
                1 -> StatsPage(c)
                2 -> RepPage(c)
                3 -> SkillsPage(c)
                4 -> CurrencyPage(c)
                else -> StatisticsPage(c)
            }
        }
    }
}

@Composable
private fun GearPage(snap: Snapshot) {
    val eq = snap.equipped()
    val slots = eq?.optJSONArray("slots").objects()
    if (slots.isEmpty()) {
        Unavailable("Your gear shows once the game sends it (equip or swap something).")
        return
    }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val ilvl = eq?.num("ilvl")
            if (ilvl != null) Label("Item level ${String.format("%.1f", ilvl)}", 15f, Wow.Gold, Modifier.padding(bottom = 4.dp))
        }
        items(slots) { s ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = if (open === s) null else s }
                    .padding(vertical = 3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WowIcon(s.opt("icon"), ROW_ICON, border = Wow.quality(s.optInt("quality", 1)))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Label(s.str("item") ?: "(empty)", 14f, Wow.quality(s.optInt("quality", 1)))
                        Label(s.str("name") ?: "", 12f, Wow.Dim)
                    }
                    s.num("ilvl")?.let { Label("${it.toInt()}", 13f, Wow.Label) }
                    s.num("dur")?.let { d ->
                        Spacer(Modifier.width(8.dp))
                        Label("${d.toInt()}%", 12f, if (d < 25) Wow.Bad else if (d < 50) Wow.Warn else Wow.Dim)
                    }
                }
                if (open === s) Tooltip(s.optJSONArray("tip").objects(), Modifier.padding(start = 38.dp, top = 4.dp))
            }
        }
    }
}

/** The game's tooltip lines, colored as the game colors them. */
@Composable
fun Tooltip(lines: List<JSONObject>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Wow.Background)
            .padding(6.dp),
    ) {
        lines.forEachIndexed { i, l ->
            Row {
                Label(l.str("t") ?: "", if (i == 0) 15f else 13f, Wow.hex(l.str("c")), Modifier.weight(1f), lines = 4)
                l.str("r")?.let { Label(it, 13f, Wow.hex(l.str("rc"))) }
            }
        }
    }
}

@Composable
private fun StatsPage(c: JSONObject?) {
    val groups = c?.optJSONArray("st").objects()
    if (groups.isEmpty()) return Unavailable("Open the character window once to load your stats.")
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { g ->
            item { Header(g.str("h") ?: "") }
            items(g.optJSONArray("r").objects()) { r ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Label(r.str("l") ?: "", 14f, Wow.Label, Modifier.weight(1f))
                    Label(r.str("v") ?: "", 14f, if (r.optInt("c") == 1) Wow.Good else Wow.Text)
                }
            }
        }
    }
}

@Composable
private fun RepPage(c: JSONObject?) {
    val list = c?.optJSONArray("rep").objects()
    if (list.isEmpty()) return Unavailable("No reputations yet.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(list) { r ->
            if (r.optBoolean("h")) {
                Header(r.str("n") ?: "")
            } else {
                val bar = r.optJSONObject("bar")
                Column(Modifier.padding(vertical = 3.dp)) {
                    Row {
                        Label(r.str("n") ?: "", 14f, Wow.Text, Modifier.weight(1f))
                        Label(bar?.str("st") ?: "", 13f, standingColor(bar?.optInt("col") ?: 4))
                    }
                    if (bar != null) {
                        val max = bar.optDouble("max", 1.0).coerceAtLeast(1.0)
                        FlatBar((bar.optDouble("cur") / max).toFloat(), standingColor(bar.optInt("col")), height = 12.dp) {
                            Label(bar.str("p") ?: "", 10f)
                        }
                    }
                }
            }
        }
    }
}

private fun standingColor(i: Int) = when (i) {
    1, 2 -> Wow.Bad
    3 -> androidx.compose.ui.graphics.Color(0xFFEE6622)
    4 -> Wow.Gold
    else -> Wow.Good
}

@Composable
private fun SkillsPage(c: JSONObject?) {
    val list = c?.optJSONArray("sk").objects()
    if (list.isEmpty()) return Unavailable("No skills yet.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(list) { s ->
            if (s.optBoolean("h")) {
                Header(s.str("n") ?: "")
            } else {
                val max = s.optDouble("max")
                Column(Modifier.padding(vertical = 3.dp)) {
                    Row {
                        Label(s.str("n") ?: "", 14f, Wow.Text, Modifier.weight(1f))
                        Label(s.str("bt") ?: "", 13f, Wow.Label)
                    }
                    if (max > 0) FlatBar((s.optDouble("rank") / max).toFloat(), androidx.compose.ui.graphics.Color(0xFF3C78C8), height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun CurrencyPage(c: JSONObject?) {
    val list = c?.optJSONArray("cur").objects()
    if (list.isEmpty()) return Unavailable("No currencies yet.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(list) { s ->
            if (s.optBoolean("h")) {
                Header(s.str("n") ?: "")
            } else {
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    WowIcon(s.opt("icon"), 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Label(s.str("n") ?: "", 14f, Wow.Text, Modifier.weight(1f))
                    Label(s.str("qt") ?: "${s.optInt("q")}", 14f, Wow.Gold)
                }
            }
        }
    }
}

@Composable
private fun StatisticsPage(c: JSONObject?) {
    val list = c?.optJSONArray("sx").objects()
    if (list.isEmpty()) return Unavailable("No statistics yet.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(list) { s ->
            if (s.optBoolean("h")) {
                Header(s.str("n") ?: "")
            } else {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Label(s.str("n") ?: "", 13f, Wow.Label, Modifier.weight(1f), lines = 2)
                    Label(s.str("v") ?: "", 13f, Wow.Text)
                }
            }
        }
    }
}

/** Unused-for-now strings helper kept close to its only users. */
internal fun JSONObject.lines(key: String): List<String> = optJSONArray(key).strings()

internal val dividerHeight = 1.dp

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(dividerHeight).background(Wow.Border))
}
