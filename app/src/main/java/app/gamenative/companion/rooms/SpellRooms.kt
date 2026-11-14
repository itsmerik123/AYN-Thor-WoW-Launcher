package app.gamenative.companion.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
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
import app.gamenative.companion.objects
import app.gamenative.companion.str
import org.json.JSONObject

/** The spellbook, by its tabs (General, your specs, Professions): icon, name, rank, cast time and text. */
@Composable
fun SpellsRoom(snap: Snapshot) {
    val s = snap.doc("spells")?.optJSONObject("s")
    val lines = s?.optJSONArray("lines").objects()
    val items = s?.optJSONArray("items").objects()
    if (lines.isEmpty() || items.isEmpty()) {
        Panel(Modifier.fillMaxSize()) { Unavailable("Your spellbook shows once the game sends it.") }
        return
    }
    var tab by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(lines.map { it.str("n") ?: "?" }, tab.coerceAtMost(lines.size - 1)) { tab = it }
        val line = lines[tab.coerceAtMost(lines.size - 1)]
        val mine = items.filter { it.optInt("ln") == line.optInt("k") }
        Panel(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(mine) { sp ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open = if (open == sp.optInt("id")) null else sp.optInt("id") }
                            .padding(vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WowIcon(sp.opt("i"), ROW_ICON)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Label(sp.str("n") ?: "", 15f, if (sp.optInt("pv") > 0 && sp.has("tl")) Wow.Dim else Wow.Text)
                                val sub = listOfNotNull(sp.str("sub"), sp.optInt("ct").takeIf { it > 0 }?.let { "${it / 1000.0}s cast" })
                                if (sub.isNotEmpty()) Label(sub.joinToString("  -  "), 12f, Wow.Dim)
                            }
                        }
                        if (open == sp.optInt("id")) {
                            sp.str("d")?.let { Label(it, 13f, Wow.Gold, Modifier.padding(start = 38.dp, top = 2.dp), lines = 8) }
                        }
                    }
                }
            }
        }
    }
}

/** The talent trees: each tree's points and its talents with their ranks (gold = maxed, green = some). */
@Composable
fun TalentsRoom(snap: Snapshot) {
    val t = snap.doc("talents")?.optJSONObject("t")
    val groups = t?.optJSONArray("groups").objects()
    val nodes = t?.optJSONArray("nodes").objects()
    if (groups.isEmpty()) {
        Panel(Modifier.fillMaxSize()) { Unavailable("Your talents show once the game sends them.") }
        return
    }
    var tab by remember { mutableStateOf(0) }
    val pool = t?.optJSONObject("pool")
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SubTabs(groups.map { "${it.str("n")} (${it.optInt("spent")})" }, tab.coerceAtMost(groups.size - 1)) { tab = it }
            }
            if (pool != null) Label("${pool.optInt("have")} to spend", 14f, if (pool.optInt("have") > 0) Wow.Good else Wow.Label)
        }
        val group = groups[tab.coerceAtMost(groups.size - 1)]
        // nodes carry their tree's index (g, 1-based) and their row (r) / column (c)
        val mine = nodes.filter { it.optInt("g") == tab + 1 }.sortedWith(compareBy({ it.optInt("r") }, { it.optInt("c") }))
        Panel(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Header(group.str("n") ?: "") }
                mine.groupBy { it.optInt("r") }.forEach { (row, list) ->
                    item {
                        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("${row + 1}", 11f, Wow.Dim, Modifier.width(18.dp))
                            list.forEach { n -> TalentNode(n) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TalentNode(n: JSONObject) {
    val rank = n.optInt("rk")
    val max = n.optInt("max", 1)
    val color = when {
        rank >= max -> Wow.Gold
        rank > 0 -> Wow.Good
        n.str("st") == "locked" -> Color(0xFF555555)
        else -> Wow.Label
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        WowIcon(n.opt("i"), 36.dp, border = color) {
            Label("$rank/$max", 10f, color, Modifier.align(Alignment.BottomEnd))
        }
        Label(n.str("n") ?: "", 10f, color, lines = 2)
    }
}

/** Professions: each with its rank bar and its abilities. */
@Composable
fun ProfessionsRoom(snap: Snapshot) {
    val list = snap.doc("prof")?.optJSONObject("p")?.optJSONArray("list").objects()
    if (list.isEmpty()) {
        Panel(Modifier.fillMaxSize()) { Unavailable("Your professions show once the game sends them.") }
        return
    }
    Panel(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { p ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WowIcon(p.opt("icon"), 36.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Label(p.str("name") ?: "", 16f, Wow.Gold, Modifier.weight(1f))
                                Label("${p.optInt("rank")} / ${p.optInt("max")}", 14f, Wow.Text)
                            }
                            val max = p.optDouble("max", 1.0).coerceAtLeast(1.0)
                            FlatBar((p.optDouble("rank") / max).toFloat(), Color(0xFF3C78C8), height = 8.dp)
                        }
                    }
                    Row(Modifier.padding(start = 44.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.optJSONArray("btn").objects().forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WowIcon(b.opt("icon"), 22.dp)
                                Label(" " + (b.str("n") ?: ""), 13f, Wow.Label)
                            }
                        }
                    }
                }
            }
        }
    }
}
