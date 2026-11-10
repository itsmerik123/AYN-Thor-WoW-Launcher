package app.gamenative.externaldisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.winlator.renderer.VulkanRenderer
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerView
import com.winlator.xserver.Pointer
import com.winlator.xserver.XServer
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Bottom screen in split mode: a second renderer that shows the UI band of the tall X screen, with touch
 * going straight into the game as mouse clicks. A small corner button flips to the regular button pad.
 *
 * Touch: tap = left click, long press = right click, drag = left-button drag (moving bag items),
 * two-finger tap = right click, two-finger vertical swipe = mouse wheel.
 */
@SuppressLint("ViewConstructor")
class SplitBandView(
    context: Context,
    private val xServer: XServer,
    private val layout: ThorSplit.Layout,
    private val touchpadViewProvider: () -> TouchpadView?,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val mirrorView = XServerView(context, xServer, "vulkan")
    private val mirror = mirrorView.renderer as VulkanRenderer
    private val touchLayer = TouchLayer(context)
    private var padView: View? = null
    private val toggle: TextView

    init {
        setBackgroundColor(Color.BLACK)
        mirror.registerAsMirror()
        mirror.setSplitCrop(layout.band)
        addView(mirrorView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(touchLayer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        toggle = TextView(context).apply {
            text = "PAD"
            textSize = 11f
            setTextColor(Color.argb(200, 255, 255, 255))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(Color.argb(110, 0, 0, 0))
                setStroke((1 * density).toInt(), Color.argb(90, 255, 255, 255))
            }
            val pad = (8 * density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            setOnClickListener { togglePad() }
        }
        addView(toggle, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            val m = (6 * density).toInt()
            setMargins(m, m, m, m)
        })
    }

    private fun togglePad() {
        val current = padView
        if (current != null) {
            removeView(current)
            padView = null
            toggle.text = "PAD"
            return
        }
        val pad = ExternalActionPad(context, xServer, PadTheme.DEFAULT, touchpadViewProvider)
        addView(pad, indexOfChild(toggle), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        padView = pad
        toggle.text = "UI"
    }

    override fun onDetachedFromWindow() {
        mirror.releaseMirror()
        super.onDetachedFromWindow()
    }

    /** View point -> X screen point inside the band, or null when it falls in the letterbox. */
    private fun toScreen(x: Float, y: Float): Pair<Int, Int>? {
        val fit = mirror.splitFit() ?: return null
        val band = layout.band
        val sx = band.left + (x - fit[0]) / fit[2]
        val sy = band.top + (y - fit[1]) / fit[2]
        if (sx < band.left || sx >= band.right || sy < band.top || sy >= band.bottom) return null
        return sx.toInt() to sy.toInt()
    }

    private inner class TouchLayer(context: Context) : View(context) {
        private val handler = Handler(Looper.getMainLooper())
        private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private val longPressMs = 450L
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var leftDown = false
        private var consumed = false
        private var multi = false
        private var multiMoved = false
        private var lastScrollY = 0f
        private var scrollAccum = 0f

        private val longPress = Runnable {
            if (!dragging && !multi) {
                click(Pointer.Button.BUTTON_RIGHT)
                consumed = true
            }
        }

        private fun moveTo(x: Float, y: Float): Boolean {
            val p = toScreen(x, y) ?: return false
            xServer.injectPointerMove(p.first, p.second)
            return true
        }

        private fun click(button: Pointer.Button) {
            xServer.injectPointerButtonPress(button)
            xServer.injectPointerButtonRelease(button)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    dragging = false; leftDown = false; consumed = false; multi = false; multiMoved = false
                    if (moveTo(e.x, e.y)) handler.postDelayed(longPress, longPressMs)
                    else consumed = true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    handler.removeCallbacks(longPress)
                    if (leftDown) { xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT); leftDown = false }
                    multi = true
                    lastScrollY = (e.getY(0) + e.getY(1)) / 2f
                    scrollAccum = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    if (multi && e.pointerCount >= 2) {
                        val y = (e.getY(0) + e.getY(1)) / 2f
                        scrollAccum += y - lastScrollY
                        lastScrollY = y
                        val step = 40 * density
                        while (abs(scrollAccum) >= step) {
                            multiMoved = true
                            // Finger moving up scrolls the content up (wheel down), like a phone.
                            click(if (scrollAccum < 0) Pointer.Button.BUTTON_SCROLL_DOWN else Pointer.Button.BUTTON_SCROLL_UP)
                            scrollAccum -= step * Math.signum(scrollAccum)
                        }
                    } else if (!consumed && !multi) {
                        if (!dragging && hypot(e.x - downX, e.y - downY) > slop) {
                            dragging = true
                            handler.removeCallbacks(longPress)
                            // Press at the start point, then follow the finger: a drag in the game.
                            moveTo(downX, downY)
                            xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
                            leftDown = true
                        }
                        if (dragging) moveTo(e.x, e.y)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    when {
                        leftDown -> { moveTo(e.x, e.y); xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT); leftDown = false }
                        multi -> if (!multiMoved) click(Pointer.Button.BUTTON_RIGHT)
                        !consumed && !dragging -> click(Pointer.Button.BUTTON_LEFT)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (leftDown) { xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT); leftDown = false }
                }
            }
            return true
        }
    }
}
