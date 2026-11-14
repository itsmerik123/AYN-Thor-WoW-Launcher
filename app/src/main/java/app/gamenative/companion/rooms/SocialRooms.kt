package app.gamenative.companion.rooms

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import app.gamenative.companion.FlatBar
import app.gamenative.companion.Header
import app.gamenative.companion.Label
import app.gamenative.companion.Panel
import app.gamenative.companion.Snapshot
import app.gamenative.companion.SubTabs
import app.gamenative.companion.Unavailable
import app.gamenative.companion.Wow
import app.gamenative.companion.WowIcon
import app.gamenative.companion.objects
import app.gamenative.companion.str
import java.util.Calendar
import org.json.JSONObject

/** Friends (Battle.net and character friends) and guild, online first, in class colors. */
@Composable
fun SocialRoom(snap: Snapshot) {
    var tab by remember { mutableStateOf(0) }
    val friends = snap.doc("social:friends")?.optJSONObject("s")
    val guild = snap.doc("social:guild")?.optJSONObject("s")
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Friends", "Guild"), tab) { tab = it }
        Panel(Modifier.fillMaxSize()) {
            if (tab == 0) {
                val people = friends?.optJSONArray("bn").objects() + friends?.optJSONArray("list").objects()
                if (people.isEmpty()) {
                    Unavailable("Your friends list shows once the game sends it.")
                } else {
                    PeopleList(people.sortedWith(compareByDescending<JSONObject> { it.optBoolean("on") || it.optBoolean("connected") }.thenBy { name(it) }))
                }
            } else {
                val members = guild?.optJSONArray("list").objects() + guild?.optJSONArray("m").objects()
                if (members.isEmpty()) {
                    Unavailable(if (snap.doc("social")?.optJSONObject("s")?.optBoolean("inGuild") == false) "You're not in a guild." else "Your guild shows once the game sends it.")
                } else {
                    PeopleList(members.sortedWith(compareByDescending<JSONObject> { it.optBoolean("on") || it.optBoolean("online") }.thenBy { name(it) }))
                }
            }
        }
    }
}

private fun name(p: JSONObject) = p.str("char") ?: p.str("name") ?: p.str("n") ?: p.str("tag") ?: "?"

@Composable
private fun PeopleList(people: List<JSONObject>) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(people) { p ->
            val online = p.optBoolean("on") || p.optBoolean("online") || p.optBoolean("connected")
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Label(name(p), 15f, if (online) Wow.classColor(p.str("cls") ?: p.str("class")) else Wow.Dim)
                        p.str("tag")?.let { Label("  $it", 12f, Wow.Dim) }
                        if (p.optBoolean("afk")) Label("  AFK", 12f, Wow.Warn)
                        if (p.optBoolean("dnd")) Label("  DND", 12f, Wow.Bad)
                    }
                    val where = listOfNotNull(p.str("area") ?: p.str("zone"), p.str("realm")).joinToString("  -  ")
                    if (where.isNotEmpty()) Label(where, 12f, Wow.Label)
                }
                val lvl = p.optInt("lvl", p.optInt("level"))
                if (lvl > 0) Label("$lvl", 14f, Wow.Text)
            }
        }
    }
}

/** The group finder: what you're listed for and the groups the last search found. */
@Composable
fun GroupRoom(snap: Snapshot) {
    val g = snap.doc("gf")?.optJSONObject("g")
    val cats = snap.doc("gfcat")?.optJSONObject("c")?.optJSONArray("cats").objects()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Header("Your listing")
                val me = g?.optJSONObject("me")
                Label(if (me?.optBoolean("on") == true) (me.str("n") ?: "Listed") else "Not listed", 14f, if (me?.optBoolean("on") == true) Wow.Good else Wow.Dim)
            }
        }
        Panel(Modifier.fillMaxSize()) {
            val res = g?.optJSONObject("search")?.optJSONArray("res").objects()
            LazyColumn(Modifier.fillMaxSize()) {
                item { Header("Groups") }
                if (res.isEmpty()) item { Label("No search results. Search from the game's group finder to fill this.", 13f, Wow.Dim, lines = 2) }
                items(res) { r ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row {
                            Label(r.str("leader") ?: r.str("n") ?: "?", 15f, Wow.classColor(r.str("cls")), Modifier.weight(1f))
                            r.optInt("lvl").takeIf { it > 0 }?.let { Label("$it", 13f, Wow.Label) }
                        }
                        Label(r.str("act") ?: r.str("name") ?: r.str("title") ?: "", 13f, Wow.Gold)
                        r.str("comment")?.let { Label(it, 12f, Wow.Label, lines = 2) }
                    }
                }
                if (cats.isNotEmpty()) {
                    item { Header("Dungeons for your level") }
                    val acts = cats.flatMap { it.optJSONArray("acts").objects() }
                    items(acts) { a ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Label(a.str("full") ?: a.str("n") ?: "", 13f, Wow.Text, Modifier.weight(1f))
                            Label("${a.optInt("smin")}-${a.optInt("smax")}", 12f, Wow.Label)
                        }
                    }
                }
            }
        }
    }
}

/** PvP: honor, rank progress, and the battlegrounds. */
@Composable
fun PvpRoom(snap: Snapshot) {
    val p = snap.doc("pvp")?.optJSONObject("pvp")
    if (p == null) {
        Panel(Modifier.fillMaxSize()) { Unavailable("PvP shows once the game sends it.") }
        return
    }
    val honor = p.optJSONObject("honor")
    val rank = p.optJSONObject("rank")
    Panel(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Column {
                Header("Honor")
                val cur = honor?.optJSONObject("currency")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WowIcon(cur?.opt("icon"), 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Label("${cur?.optInt("have") ?: 0} / ${cur?.optInt("max") ?: 0}", 16f, Wow.Gold, Modifier.weight(1f))
                    Label("HK today ${honor?.optInt("hkToday") ?: 0}  -  lifetime ${honor?.optInt("hkLife") ?: 0}", 13f, Wow.Label)
                }
            } }
            if (rank != null && rank.optBoolean("ok")) {
                item { Column {
                    Header("Rank")
                    val lines = rank.optJSONObject("lines")
                    Label(rank.str("title") ?: lines?.str("rank") ?: "", 15f, Wow.Gold)
                    lines?.str("progress")?.let { Label(it, 13f, Wow.Label, lines = 2) }
                    val max = rank.optDouble("threshold", 0.0)
                    if (max > 0) FlatBar((rank.optDouble("earned") / max).toFloat(), Wow.Mid, height = 10.dp, brush = Wow.Sweep)
                    lines?.str("weeklyCap")?.let { Label(it, 12f, Wow.Dim, lines = 2) }
                } }
            }
            item { Header("Battlegrounds") }
            items(p.optJSONArray("bg").objects()) { bg ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row {
                        Label(bg.str("name") ?: "", 15f, if (bg.optBoolean("canEnter")) Wow.Gold else Wow.Dim, Modifier.weight(1f))
                        if (bg.optBoolean("holiday")) Label("Holiday", 12f, Wow.Good)
                    }
                    bg.str("short")?.let { Label(it, 12f, Wow.Label, lines = 3) }
                }
            }
            val queue = p.optJSONArray("queue").objects()
            if (queue.isNotEmpty()) {
                item { Header("Queued") }
                items(queue) { q -> Label(q.str("name") ?: q.str("n") ?: q.toString(), 14f, Wow.Good) }
            }
        }
    }
}

/** The calendar: this month and the next, holidays and events by day. */
@Composable
fun CalendarRoom(snap: Snapshot) {
    val c = snap.doc("calendar")?.optJSONObject("c")
    val months = c?.optJSONArray("months").objects()
    if (months.isEmpty()) {
        Panel(Modifier.fillMaxSize()) { Unavailable("The calendar shows once the game sends it.") }
        return
    }
    val now = c?.optJSONObject("now")
    Panel(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            months.forEach { m ->
                item { Header(monthName(m.optInt("m")) + " " + m.optInt("y")) }
                val events = m.optJSONArray("ev").objects()
                if (events.isEmpty()) item { Label("Nothing this month.", 13f, Wow.Dim) }
                items(events) { e ->
                    val st = e.optJSONObject("st")
                    val en = e.optJSONObject("en")
                    val today = now != null && st != null && now.optInt("m") == m.optInt("m") &&
                        (e.optJSONArray("on")?.toString()?.contains(now.optInt("d").toString()) == true)
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        WowIcon(e.opt("icon"), 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Label(e.str("t") ?: "", 15f, if (today) Wow.Good else Wow.Gold)
                            Label(span(st, en), 12f, Wow.Label)
                        }
                    }
                }
            }
        }
    }
}

private fun monthName(m: Int): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.MONTH, (m - 1).coerceIn(0, 11))
    return cal.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault()) ?: "Month $m"
}

private fun span(st: JSONObject?, en: JSONObject?): String {
    fun d(o: JSONObject?) = o?.let { "${monthName(it.optInt("m")).take(3)} ${it.optInt("d")}" } ?: ""
    val a = d(st)
    val b = d(en)
    return if (b.isEmpty() || a == b) a else "$a - $b"
}
