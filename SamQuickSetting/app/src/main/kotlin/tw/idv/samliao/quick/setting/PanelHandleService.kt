package tw.idv.samliao.quick.setting

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

class PanelHandleService : Service() {
    private lateinit var windowManager: WindowManager
    private var handleView: View? = null
    private var handleParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        showHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showHandle()
        return START_STICKY
    }

    override fun onDestroy() {
        handleView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    private fun showHandle() {
        if (handleView != null) return

        val width = dp(10)
        val height = dp(96)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val screen = resources.displayMetrics
        val onRight = prefs.getBoolean(KEY_RIGHT, true)
        val savedY = prefs.getFloat(KEY_Y_FRACTION, 0.5f)
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (onRight) screen.widthPixels - width else 0
            y = (savedY * screen.heightPixels).toInt().coerceIn(dp(72), screen.heightPixels - height - dp(96))
        }

        val handle = FrameLayout(this).apply {
            background = handleBackground(onRight)
            addView(TextView(this@PanelHandleService).apply {
                text = "≡"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                alpha = 0.86f
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            setOnTouchListener(handleTouchListener(params, width, height))
        }

        windowManager.addView(handle, params)
        handleView = handle
        handleParams = params
    }

    private fun handleTouchListener(params: WindowManager.LayoutParams, width: Int, height: Int): View.OnTouchListener {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        return View.OnTouchListener { view, event ->
            val screen = resources.displayMetrics
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    params.x = (startX + dx).coerceIn(0, screen.widthPixels - width)
                    params.y = (startY + dy).coerceIn(dp(72), screen.heightPixels - height - dp(96))
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        snapToEdge(view, params, width, screen.widthPixels, screen.heightPixels)
                    } else {
                        DebugLog.write(this, "edge handle open panel")
                        startService(Intent(this, FloatingPanelService::class.java))
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) snapToEdge(view, params, width, screen.widthPixels, screen.heightPixels)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams, width: Int, screenWidth: Int, screenHeight: Int) {
        val onRight = params.x + width / 2 >= screenWidth / 2
        params.x = if (onRight) screenWidth - width else 0
        view.background = handleBackground(onRight)
        windowManager.updateViewLayout(view, params)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RIGHT, onRight)
            .putFloat(KEY_Y_FRACTION, params.y.toFloat() / screenHeight.coerceAtLeast(1))
            .apply()
        DebugLog.write(this, "edge handle move right=$onRight y=${params.y}")
    }

    private fun handleBackground(onRight: Boolean) = GradientDrawable().apply {
        setColor(0xCC102027.toInt())
        setStroke(dp(1), 0xAA4DD0E1.toInt())
        val r = dp(14).toFloat()
        cornerRadii = if (onRight) {
            floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        } else {
            floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "panel_handle"
        private const val KEY_RIGHT = "right"
        private const val KEY_Y_FRACTION = "y_fraction"
    }
}
