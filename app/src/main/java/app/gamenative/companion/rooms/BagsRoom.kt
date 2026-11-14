package app.gamenative.companion.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import app.gamenative.companion.WowIcon
import app.gamenative.companion.objects
import app.gamenative.companion.str
import org.json.JSONObject

/**
 * The bags, grouped and ordered exactly as the PC's Tukui bags do it (BagsRetail.lua's categories: equipment,
 * consumables, quest, one group per profession's materials and recipes, … junk, free slots), each group's items
 * by quality, then item. Tap an item for its tooltip and to use it.
 */
private val ORDER = listOf(
    "Equipment", "Consumables", "Quest", "Enchanting", "Tailoring", "Leatherworking", "Mining & Smithing",
    "Herbs & Alchemy", "Cooking", "Engineering", "First Aid & Fishing", "Trade Goods", "Recipes", "Gems",
    "Reagents", "Containers", "Ammo", "Keys", "Miscellaneous", "Junk",
)
private val TRADE_GOODS = mapOf(
    1 to "Engineering", 2 to "Engineering", 3 to "Engineering", 5 to "Tailoring", 6 to "Leatherworking",
    7 to "Mining & Smithing", 8 to "Cooking", 9 to "Herbs & Alchemy", 12 to "Enchanting",
)
private val RECIPES = mapOf(
    1 to "Leatherworking", 2 to "Tailoring", 3 to "Engineering", 4 to "Mining & Smithing", 5 to "Cooking",
    6 to "Herbs & Alchemy", 7 to "First Aid & Fishing", 8 to "Enchanting", 9 to "First Aid & Fishing",
)
private val CLASSES = mapOf(
    0 to "Consumables", 1 to "Containers", 2 to "Equipment", 3 to "Gems", 4 to "Equipment", 5 to "Reagents",
    6 to "Ammo", 8 to "Equipment", 11 to "Containers", 12 to "Quest", 13 to "Keys", 15 to "Miscellaneous",
    16 to "Recipes", 17 to "Miscellaneous",
)

private fun category(item: JSONObject): String {
    if (item.optBoolean("quest") || item.optInt("cls", -1) == 12 || item.str("itype") == "Quest") return "Quest"
    if (item.optInt("quality", 1) == 0) return "Junk"
    val cls = item.optInt("cls", -1)
    val sub = item.optInt("sub", -1)
    return when (cls) {
        7 -> TRADE_GOODS[sub] ?: "Trade Goods"
        9 -> RECIPES[sub] ?: "Recipes"
        else -> CLASSES[cls] ?: "Miscellaneous"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BagsRoom(snap: Snapshot) {
    val bags = snap.bags()
    if (bags == null) {
        Panel(Modifier.fillMaxSize()) { Unavailable("Your bags show once the game sends them.") }
        return
    }
    val items = bags.optJSONArray("items").objects()
    val groups = items.groupBy { category(it) }
    var picked by remember { mutableStateOf<JSONObject?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(Wow.money(bags.optLong("money")), 16f, Wow.Gold)
                    Label("${bags.optInt("free")} / ${bags.optInt("total")} free", 13f, Wow.Label)
                }
                bags.optJSONArray("bags").objects().forEach { b ->
                    WowIcon(b.opt("icon") ?: "inv_misc_bag_08", 26.dp, Modifier.padding(start = 3.dp)) {
                        Label("${b.optInt("free")}", 10f, Wow.Text, Modifier.align(Alignment.BottomEnd))
                    }
                }
                Spacer(Modifier.width(8.dp))
                WowButton("Sort") {
                    note = if (CompanionTap.tap("bagsort", 0)) null else "Sorting isn't available right now (in combat?)."
                }
            }
        }
        note?.let { Unavailable(it) }
        picked?.let { item ->
            Panel(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column {
                    Tooltip(itemTooltip(item))
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.optBoolean("use") || item.str("equip") != null) {
                            WowButton(if (item.str("equip") != null) "Equip" else "Use") {
                                val ok = CompanionTap.tap("bag" + item.optInt("bag"), item.optInt("slot") - 1)
                                note = if (ok) null else "Can't use that from here right now."
                                picked = null
                            }
                        }
                        WowButton("Close", color = Wow.Label) { picked = null }
                    }
                }
            }
        }
        Panel(Modifier.fillMaxSize().padding(top = 4.dp)) {
            LazyColumn(Modifier.fillMaxSize()) {
                ORDER.forEach { name ->
                    val list = groups[name] ?: return@forEach
                    val sorted = list.sortedWith(
                        compareByDescending<JSONObject> { it.optInt("quality", 1) }
                            .thenBy { it.optInt("id") }
                            .thenBy { it.optInt("bag") }
                            .thenBy { it.optInt("slot") },
                    )
                    item { Header(name) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            sorted.forEach { it ->
                                WowIcon(
                                    it.opt("icon"),
                                    40.dp,
                                    Modifier.clickable { picked = if (picked === it) null else it },
                                    border = if (it.optInt("quality", 1) >= 2) Wow.quality(it.optInt("quality")) else Wow.Border,
                                ) {
                                    val count = it.optInt("count", 1)
                                    if (count > 1) Label("$count", 12f, Wow.Text, Modifier.align(Alignment.BottomEnd).padding(1.dp))
                                    if (it.optBoolean("locked")) Label("•", 12f, Wow.Bad, Modifier.align(Alignment.TopStart))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An item's tooltip: the game's own lines when the add-on sent them, else what we know. */
private fun itemTooltip(item: JSONObject): List<JSONObject> {
    item.optJSONArray("tip").objects().takeIf { it.isNotEmpty() }?.let { return it }
    val out = ArrayList<JSONObject>()
    val q = item.optInt("quality", 1)
    out.add(JSONObject().put("t", item.str("name") ?: "Item").put("c", qualityHex(q)))
    if (item.optBoolean("bound")) out.add(JSONObject().put("t", "Soulbound").put("c", "ffffff"))
    listOfNotNull(item.str("itype"), item.str("isub")).joinToString(" - ").takeIf { it.isNotEmpty() }?.let {
        out.add(JSONObject().put("t", it).put("c", "ffffff"))
    }
    item.optJSONArray("stats").objects().forEach { s ->
        val v = s.optDouble("v")
        val text = if (v == Math.floor(v)) "+${v.toLong()} ${s.str("n") ?: ""}" else String.format("%.1f %s", v, s.str("n") ?: "")
        out.add(JSONObject().put("t", text).put("c", "ffffff"))
    }
    if (item.has("ilvl")) out.add(JSONObject().put("t", "Item Level ${item.optInt("ilvl")}").put("c", "ffd200"))
    if (item.optInt("count", 1) > 1) out.add(JSONObject().put("t", "Count: ${item.optInt("count")}").put("c", "aaaaaa"))
    return out
}

private fun qualityHex(q: Int) = when (q) {
    0 -> "9d9d9d"
    2 -> "1eff00"
    3 -> "0070dd"
    4 -> "a335ee"
    5 -> "ff8000"
    else -> "ffffff"
}
