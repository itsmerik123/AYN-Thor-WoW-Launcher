package app.gamenative.externaldisplay

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import java.io.File

/**
 * Thor split screen: the game renders onto one tall X screen. The top part (the game area) goes to the
 * top display and a band underneath goes to the bottom display, where the ThorSplit addon puts the UI
 * panels. Touches on the bottom display click straight into that band.
 */
object ThorSplit {
    private const val PREFS = "wow_forever"
    private fun key(flavor: String) = "split_ui_$flavor"
    private const val LAST_RES = "split_last_resolution"

    const val TOP_W = 1920
    const val TOP_H = 1080

    /** Layout for the current session, set at launch when split mode is on. */
    @Volatile
    var active: Layout? = null
        private set

    data class Layout(val screenW: Int, val screenH: Int, val top: Rect, val band: Rect) {
        val screenSize get() = "${screenW}x$screenH"

        /**
         * Before the world loads, addons can't run, so WoW's login screens fill the whole tall screen and
         * their dialogs (login, captcha, character select) sit on the seam. The top display shows the
         * middle of the tall screen until the player switches to the game view.
         */
        val login get() = Rect(0, (screenH - top.height()) / 2, top.width(), (screenH - top.height()) / 2 + top.height())
    }

    /** True while the top display shows the login view (the middle of the tall screen). */
    @Volatile
    var loginView = true

    fun isEnabled(context: Context, flavor: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(flavor), false)

    fun setEnabled(context: Context, flavor: String, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(flavor), enabled).apply()

    /** The resolution split mode last wrote into Config.wtf, so it can be put back when split is turned off. */
    fun lastResolution(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_RES, null)

    private fun rememberResolution(context: Context, value: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAST_RES, value).apply()

    fun clear() { active = null }

    /**
     * Works out the tall screen from the bottom display's real size. The band is drawn 1:1 when the bottom
     * display fits inside the top width (the Thor's does), otherwise scaled down to fit.
     */
    fun prepare(context: Context): Layout {
        val (bw, bh) = bottomDisplaySize(context) ?: (1240 to 1080)
        // The band keeps the bottom display's own shape, as the Presentation shows it.
        val w = bw
        val h = bh
        val scale = minOf(1f, TOP_W.toFloat() / w)
        val bandW = (w * scale).toInt() and 0x7FFFFFFE
        val bandH = (h * scale).toInt() and 0x7FFFFFFE
        val bandX = (TOP_W - bandW) / 2
        val layout = Layout(
            screenW = TOP_W,
            screenH = TOP_H + bandH,
            top = Rect(0, 0, TOP_W, TOP_H),
            band = Rect(bandX, TOP_H, bandX + bandW, TOP_H + bandH),
        )
        active = layout
        loginView = true
        rememberResolution(context, layout.screenSize)
        return layout
    }

    fun bottomDisplaySize(context: Context): Pair<Int, Int>? {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return null
        val current = context.display?.displayId ?: Display.DEFAULT_DISPLAY
        val display = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != current && it.name != "HiddenDisplay" } ?: return null
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * Copies the bundled ThorSplit addon into the game's AddOns folder and writes the layout it reads.
     * WoW addons can't read files at runtime, so the numbers go in as a Lua file loaded first.
     */
    fun installAddon(context: Context, flavorDir: File, layout: Layout) {
        val addonDir = File(flavorDir, "Interface/AddOns/ThorSplit").apply { mkdirs() }
        context.assets.list("ThorSplit")?.forEach { name ->
            context.assets.open("ThorSplit/$name").use { input ->
                File(addonDir, name).outputStream().use { input.copyTo(it) }
            }
        }
        File(addonDir, "Layout.lua").writeText(
            """
            -- Written by the launcher at every launch. Pixel coordinates of the X screen.
            ThorSplitLayout = {
                screenW = ${layout.screenW}, screenH = ${layout.screenH},
                topW = ${layout.top.width()}, topH = ${layout.top.height()},
                bandX = ${layout.band.left}, bandY = ${layout.band.top},
                bandW = ${layout.band.width()}, bandH = ${layout.band.height()},
                enabled = true,
            }
            """.trimIndent() + "\n",
        )
    }

    /** Leaves the addon in place but tells it split is off, so the world view fills the screen again. */
    fun disableAddon(flavorDir: File) {
        val layout = File(flavorDir, "Interface/AddOns/ThorSplit/Layout.lua")
        if (layout.exists()) layout.writeText("ThorSplitLayout = { enabled = false }\n")
    }
}
