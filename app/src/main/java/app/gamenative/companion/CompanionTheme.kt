package app.gamenative.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import app.gamenative.R

/**
 * The look of Eric's PC UI (Tukui + DaylightUI): dark see-through panels with a rounded black outline, a warm
 * band lit along its top and a soft shadow (DaylightUI's frame); flat Tukui bars with 1px black borders; the
 * Daylight gradient (blue -> purple -> pink -> orange) for accents and XP; Expressway text with an outline.
 */
object Wow {
    val Family = FontFamily(Font(R.font.expressway_free))

    val Screen = Color(0xFF0B0B0D)
    val Background = Color(0xFF141417)
    val Border = Color(0xFF060607)
    val Band = Color(0xFF1A170F)
    val BandLit = Color(0xFF332F24)
    val ShadowColor = Color(0x99000000)
    val Text = Color(0xFFF0F0F0)
    val Label = Color(0xFFB8B2A6)
    val Dim = Color(0xFF8A857C)
    val Gold = Color(0xFFFFD200)
    val Good = Color(0xFF4CCB59)
    val Bad = Color(0xFFE5484D)
    val Warn = Color(0xFFFFB02E)

    val SweepColors = listOf(Color(0xFF1E90FF), Color(0xFFA25EC7), Color(0xFFE65A72), Color(0xFFFF9A1F))
    val Sweep = Brush.horizontalGradient(SweepColors)
    val Mid = Color(0xFFA25EC7)

    val Health = Color(0xFF4DCC59)
    val Rested = Color(0xFF598CFF)
    val QuestReady = Color(0xFFFFC740)

    private val quality = listOf(
        Color(0xFF9D9D9D), Color(0xFFFFFFFF), Color(0xFF1EFF00), Color(0xFF0070DD),
        Color(0xFFA335EE), Color(0xFFFF8000), Color(0xFFE6CC80), Color(0xFF00CCFF),
    )

    fun quality(q: Int): Color = quality.getOrElse(q) { Text }

    private val classes = mapOf(
        "WARRIOR" to Color(0xFFC79C6E), "PALADIN" to Color(0xFFF58CBA), "HUNTER" to Color(0xFFABD473),
        "ROGUE" to Color(0xFFFFF569), "PRIEST" to Color(0xFFFFFFFF), "SHAMAN" to Color(0xFF0070DE),
        "MAGE" to Color(0xFF69CCF0), "WARLOCK" to Color(0xFF9482C9), "DRUID" to Color(0xFFFF7D0A),
        "DEATHKNIGHT" to Color(0xFFC41F3B), "MONK" to Color(0xFF00FF96), "DEMONHUNTER" to Color(0xFFA330C9),
        "EVOKER" to Color(0xFF33937F),
    )

    fun classColor(file: String?): Color {
        if (file == null) return Text
        return classes[file.uppercase().replace(" ", "")] ?: Text
    }

    fun hex(rgb: String?, fallback: Color = Text): Color {
        val s = rgb?.removePrefix("#") ?: return fallback
        return try {
            when (s.length) {
                6 -> Color(0xFF000000 or s.toLong(16))
                8 -> Color(s.substring(2).toLong(16) or 0xFF000000)
                else -> fallback
            }
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    fun style(size: Float, color: Color = Text) = TextStyle(
        fontFamily = Family,
        fontSize = size.sp,
        color = color,
        // the game's outlined text: a tight dark shadow reads the same on a small screen
        shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 2f),
    )

    /** Copper as the game shows it: 12g 34s 56c. */
    fun money(copper: Long): String {
        val g = copper / 10000
        val s = (copper / 100) % 100
        val c = copper % 100
        return buildString {
            if (g > 0) append("${g}g ")
            if (g > 0 || s > 0) append("${s}s ")
            append("${c}c")
        }
    }

    fun short(n: Double): String = when {
        n >= 1_000_000 -> String.format("%.1fm", n / 1_000_000)
        n >= 10_000 -> String.format("%.1fk", n / 1000)
        else -> n.toLong().toString()
    }

    fun duration(seconds: Double): String {
        val s = seconds.toLong().coerceAtLeast(0)
        return when {
            s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
            s >= 60 -> "${s / 60}m"
            else -> "${s}s"
        }
    }
}

/** DaylightUI's frame: drawn inside the box, [inset] of it taken by the border. */
fun Modifier.daylight(radius: Dp = 6.dp, band: Boolean = true, alpha: Float = 0.92f): Modifier = this.drawBehind {
    drawDaylight(radius.toPx(), band, alpha)
}

fun DrawScope.drawDaylight(radius: Float, band: Boolean, alpha: Float) {
    val px = density
    // soft shadow just outside
    for (i in 1..3) {
        val g = i * 1.5f * px
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f / i),
            topLeft = Offset(-g, -g + px),
            size = Size(size.width + g * 2, size.height + g * 2),
            cornerRadius = CornerRadius(radius + g),
        )
    }
    drawRoundRect(Wow.Border, cornerRadius = CornerRadius(radius))
    var inset = 1f * px
    if (band) {
        drawRoundRect(
            Wow.Band,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
        )
        drawLine(
            Wow.BandLit,
            Offset(radius, inset + px * 0.5f),
            Offset(size.width - radius, inset + px * 0.5f),
            strokeWidth = px,
        )
        inset = 3.5f * px
        drawRoundRect(
            Wow.Border,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius((radius - inset).coerceAtLeast(1f)),
        )
        inset += px
    }
    drawRoundRect(
        Wow.Background.copy(alpha = alpha),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius((radius - inset).coerceAtLeast(1f)),
    )
    // a faint lit top edge inside
    drawLine(
        Color.White.copy(alpha = 0.05f),
        Offset(inset + px * 2, inset + px),
        Offset(size.width - inset - px * 2, inset + px),
        strokeWidth = px,
    )
}

/** A DaylightUI panel with its content inside the border. */
@Composable
fun Panel(modifier: Modifier = Modifier, band: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.daylight(band = band).padding(if (band) 8.dp else 5.dp), content = content)
}

/** Tukui's flat bar: a 1px black edge, a dark track and the fill. */
@Composable
fun FlatBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    brush: Brush? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                drawRect(Color.Black)
                val inset = density
                val w = size.width - inset * 2
                val h = size.height - inset * 2
                drawRect(color.copy(alpha = 0.18f), Offset(inset, inset), Size(w, h))
                val f = fraction.coerceIn(0f, 1f)
                if (f > 0f) {
                    if (brush != null) drawRect(brush, Offset(inset, inset), Size(w * f, h))
                    else drawRect(color, Offset(inset, inset), Size(w * f, h))
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A section header as the PC's bag categories draw it: label, then a faint accent line. */
@Composable
fun Header(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Wow.style(13f, Wow.Label))
        Box(
            Modifier
                .padding(start = 6.dp)
                .weight(1f)
                .height(1.dp)
                .background(Wow.Mid.copy(alpha = 0.35f)),
        )
    }
}

@Composable
fun Label(text: String, size: Float = 13f, color: Color = Wow.Text, modifier: Modifier = Modifier, lines: Int = 1) {
    Text(text, style = Wow.style(size, color), modifier = modifier, maxLines = lines, overflow = TextOverflow.Ellipsis)
}
