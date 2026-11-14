package app.gamenative.companion.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.gamenative.companion.Header
import app.gamenative.companion.Label
import app.gamenative.companion.Panel
import app.gamenative.companion.Snapshot
import app.gamenative.companion.Unavailable
import app.gamenative.companion.Wow
import app.gamenative.companion.objects
import app.gamenative.companion.str
import kotlin.math.PI

/**
 * The zone you're in, drawn as a plain map in the UI's look: your arrow (live, from the position stream), quest
 * objectives and turn-ins, flight masters; and beside it the quests here, nearest first.
 */
@Composable
fun MapRoom(snap: Snapshot) {
    val map = snap.map()
    val pos = snap.pos
    if (map == null && pos == null) {
        Panel(Modifier.fillMaxSize()) { Unavailable("The map shows once the game sends where you are.") }
        return
    }
    val x = pos?.optDouble("x") ?: map?.optDouble("x") ?: 0.5
    val y = pos?.optDouble("y") ?: map?.optDouble("y") ?: 0.5
    val facing = pos?.optDouble("facing") ?: map?.optDouble("facing") ?: 0.0
    val quests = map?.optJSONArray("quests").objects()
    val flights = (map?.optJSONArray("taxi").objects() + map?.optJSONArray("fly").objects())
        .filter { it.optDouble("x") in 0.0..1.0 && it.optDouble("y") in 0.0..1.0 }
    val pois = map?.optJSONArray("pois").objects()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Panel(Modifier.fillMaxHeight().aspectRatio(1f)) {
            Column {
                val zone = pos?.str("zone") ?: map?.str("zone") ?: ""
                val sub = pos?.str("subzone") ?: map?.str("subzone")
                Label(if (sub != null && sub != zone) "$zone - $sub" else zone, 15f, Wow.Gold)
                Label(String.format("%.1f, %.1f", x * 100, y * 100), 12f, Wow.Label)
                Box(Modifier.fillMaxSize().padding(top = 4.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        drawRect(Color(0xFF101418))
                        // a faint grid, a tenth of the zone apart
                        for (i in 1..9) {
                            val gx = w * i / 10f
                            val gy = h * i / 10f
                            drawLine(Color.White.copy(alpha = 0.05f), Offset(gx, 0f), Offset(gx, h), 1f)
                            drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, gy), Offset(w, gy), 1f)
                        }
                        drawRect(Wow.Border, style = Stroke(2f))
                        pois.forEach { p ->
                            drawCircle(Color(0xFF8FA3B8), 4f, Offset((p.optDouble("x") * w).toFloat(), (p.optDouble("y") * h).toFloat()))
                        }
                        flights.forEach { f ->
                            val c = Offset((f.optDouble("x") * w).toFloat(), (f.optDouble("y") * h).toFloat())
                            drawCircle(Color.Black, 7f, c)
                            drawCircle(Color(0xFF6FD36F), 5f, c)
                        }
                        quests.forEach { q ->
                            val c = Offset((q.optDouble("x") * w).toFloat(), (q.optDouble("y") * h).toFloat())
                            val done = q.optBoolean("done")
                            drawCircle(Color.Black, 9f, c)
                            drawCircle(if (done) Wow.QuestReady else Wow.Gold.copy(alpha = 0.8f), 7f, c)
                            if (!done) drawCircle(Color.Black, 2.5f, c)
                        }
                        // you: an arrow turned to where you face (the game's facing: 0 = north, counter-clockwise)
                        val me = Offset((x * w).toFloat(), (y * h).toFloat())
                        rotate(degrees = (-facing * 180 / PI).toFloat(), pivot = me) {
                            val s = 12f
                            val arrow = Path().apply {
                                moveTo(me.x, me.y - s)
                                lineTo(me.x + s * 0.7f, me.y + s * 0.8f)
                                lineTo(me.x, me.y + s * 0.35f)
                                lineTo(me.x - s * 0.7f, me.y + s * 0.8f)
                                close()
                            }
                            drawPath(arrow, Color.Black, style = Stroke(5f))
                            drawPath(arrow, Wow.Gold)
                        }
                    }
                }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Header("Quests here") }
                val sorted = quests.sortedBy { it.optDouble("dist", 1e9) }
                if (sorted.isEmpty()) item { Label("None in this zone.", 13f, Wow.Dim) }
                items(sorted) { q ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(q.str("title") ?: "", 14f, if (q.optBoolean("done")) Wow.Good else Wow.Text, Modifier.weight(1f))
                        q.optDouble("dist").takeIf { !it.isNaN() }?.let { Label("${it.toInt()} yd", 12f, Wow.Label) }
                    }
                }
                if (flights.isNotEmpty()) {
                    item { Header("Flight masters") }
                    items(flights) { f -> Label(f.str("name") ?: "", 13f, Color(0xFF6FD36F)) }
                }
            }
        }
    }
}

