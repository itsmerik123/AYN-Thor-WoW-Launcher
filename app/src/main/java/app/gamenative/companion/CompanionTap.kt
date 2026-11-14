package app.gamenative.companion

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.winlator.winhandler.MouseEventFlags
import com.winlator.xserver.Pointer
import com.winlator.xserver.XServer
import org.json.JSONObject
import timber.log.Timber

/**
 * The companion -> game half of the link.
 *
 * An add-on can't receive anything from outside the game except input, so the DS Forever add-ons keep a grid
 * of invisible buttons along the top of the game's screen ("the pad", TapPad.lua): one row per kind of action,
 * 40 columns each. Every add-on document carries the grid's place (`pad`: x / y / cell size in game pixels,
 * row numbers by name, the game's pixel size). A tap here clicks one cell, straight into the game's X server.
 *
 * Taps run one at a time, spaced out: a cell ignores a second press within 0.25 s (one finger tap arrives as two
 * presses on the Thor), and the pointer must be where the press says.
 */
object CompanionTap {
    @Volatile
    var xServer: XServer? = null

    private val thread = HandlerThread("wow-companion-tap").apply { start() }
    private val handler = Handler(thread.looper)

    /** Next moment a tap may go, and when each cell was last pressed. */
    private var nextAt = 0L
    private val lastPress = HashMap<String, Long>()

    private const val GAP_MS = 110L
    private const val SAME_CELL_MS = 320L
    private const val HOLD_MS = 45L

    /** Whether [row] is on the grid right now (some rows only appear out of combat or with a bag equipped). */
    fun has(row: String): Boolean {
        val pad = CompanionLink.docs.value.pad ?: return false
        return pad.optJSONObject("rows")?.has(row) == true
    }

    /**
     * Clicks cell ([row], [col]). Returns false when the grid isn't known yet or has no such row; the click
     * itself goes out on the tap thread, after any taps already queued.
     */
    fun tap(row: String, col: Int): Boolean {
        val pad = CompanionLink.docs.value.pad ?: return false
        val rowIndex = pad.optJSONObject("rows")?.optInt(row, -1) ?: -1
        if (rowIndex < 0 || col < 0 || col >= pad.optInt("cols", 40)) return false
        val server = xServer ?: return false
        handler.post { click(server, pad, row, rowIndex, col) }
        return true
    }

    /** Several taps in order (letters of a chat line, a pick then its action). */
    fun taps(vararg cells: Pair<String, Int>): Boolean {
        var ok = true
        for ((row, col) in cells) ok = tap(row, col) && ok
        return ok
    }

    private fun click(server: XServer, pad: JSONObject, row: String, rowIndex: Int, col: Int) {
        try {
            val key = "$row:$col"
            val now = SystemClock.uptimeMillis()
            var at = maxOf(now, nextAt)
            lastPress[key]?.let { at = maxOf(at, it + SAME_CELL_MS) }
            if (at > now) SystemClock.sleep(at - now)

            val gameW = pad.optDouble("pw", 1920.0)
            val gameH = pad.optDouble("ph", 1080.0)
            val cw = pad.optDouble("cw", 6.0)
            val ch = pad.optDouble("ch", 2.0)
            val gx = pad.optDouble("x") + col * cw + cw / 2
            val gy = pad.optDouble("y") + rowIndex * ch + ch / 2
            val sw = server.screenInfo.width.toDouble()
            val sh = server.screenInfo.height.toDouble()
            val x = (gx * sw / gameW).toInt()
            val y = (gy * sh / gameH).toInt()

            if (server.isRelativeMouseMovement) {
                val win = server.winHandler
                win.mouseEvent(MouseEventFlags.MOVE, x, y, 0)
                win.mouseEvent(MouseEventFlags.LEFTDOWN, 0, 0, 0)
                SystemClock.sleep(HOLD_MS)
                win.mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0)
            } else {
                server.injectPointerMove(x, y)
                server.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
                SystemClock.sleep(HOLD_MS)
                server.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
            }
            val done = SystemClock.uptimeMillis()
            lastPress[key] = done
            nextAt = done + GAP_MS
        } catch (e: Exception) {
            Timber.w(e, "companion: tap $row $col")
        }
    }
}
