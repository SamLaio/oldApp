package tw.idv.samliao.quick.setting

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

class PanelHandleService : Service() {
    private lateinit var windowManager: WindowManager
    private var handleView: View? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) repositionHandle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        val filter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(configReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(configReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshHandle()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(configReceiver) }
        removeHandle()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        repositionHandle()
    }

    private fun refreshHandle() {
        removeHandle()
        showHandle()
    }

    private fun removeHandle() {
        handleView?.let { runCatching { windowManager.removeView(it) } }
        handleView = null
        handleParams = null
    }

    private fun showHandle() {
        if (handleView != null) return

        val visibleWidth = dp(widthDp(this))
        val touchWidth = dp(touchWidthDp(this))
        val height = dp(lengthDp(this))
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val screen = screenSize()
        val onRight = prefs.getBoolean(orientationKey(KEY_RIGHT), prefs.getBoolean(KEY_RIGHT, true))
        val yKey = orientationKey(KEY_Y_FRACTION)
        val savedY = when {
            prefs.contains(yKey) -> prefs.getFloat(yKey, 0.5f)
            isLandscape() -> 0.28f
            else -> prefs.getFloat(KEY_Y_FRACTION, 0.5f)
        }
        val params = WindowManager.LayoutParams(
            touchWidth,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            applyFullScreenLayoutFlags()
            x = if (onRight) screen.first - touchWidth else 0
            y = (savedY * screen.second).toInt().coerceIn(dp(72), yMax(screen.second, height))
        }

        val handle = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(TextView(this@PanelHandleService).apply {
                text = "≡"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                alpha = 0.86f
                background = handleBackground(onRight)
            }, visibleHandleParams(onRight, visibleWidth))
            setOnTouchListener(handleTouchListener(params, touchWidth, height))
        }

        windowManager.addView(handle, params)
        handleView = handle
        handleParams = params
    }

    private fun repositionHandle() {
        val params = handleParams ?: return refreshHandle()
        val view = handleView ?: return refreshHandle()
        val visibleWidth = dp(widthDp(this))
        val touchWidth = dp(touchWidthDp(this))
        val height = dp(lengthDp(this))
        val screen = screenSize()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val onRight = prefs.getBoolean(orientationKey(KEY_RIGHT), prefs.getBoolean(KEY_RIGHT, true))
        val yKey = orientationKey(KEY_Y_FRACTION)
        val savedY = when {
            prefs.contains(yKey) -> prefs.getFloat(yKey, 0.5f)
            isLandscape() -> 0.28f
            else -> prefs.getFloat(KEY_Y_FRACTION, 0.5f)
        }

        params.width = touchWidth
        params.height = height
        params.x = if (onRight) screen.first - touchWidth else 0
        params.y = (savedY * screen.second).toInt().coerceIn(dp(72), yMax(screen.second, height))
        params.applyFullScreenLayoutFlags()
        updateVisibleHandle(view, onRight, visibleWidth)
        runCatching { windowManager.updateViewLayout(view, params) }
        DebugLog.write(this, "edge handle reposition landscape=${isLandscape()} right=$onRight x=${params.x} y=${params.y} screen=${screen.first}x${screen.second}")
    }

    private fun handleTouchListener(params: WindowManager.LayoutParams, width: Int, height: Int): View.OnTouchListener {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        return View.OnTouchListener { view, event ->
            val screen = screenSize()
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
                    params.x = (startX + dx).coerceIn(0, screen.first - width)
                    params.y = (startY + dy).coerceIn(dp(72), yMax(screen.second, height))
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val tap = abs(event.rawX - downRawX) <= slop * 4 && abs(event.rawY - downRawY) <= slop * 4
                    DebugLog.write(this, "edge handle up moved=$moved tap=$tap dx=${event.rawX - downRawX} dy=${event.rawY - downRawY}")
                    if (moved && !tap) {
                        snapToEdge(view, params, width, screen.first, screen.second)
                    } else {
                        DebugLog.write(this, "edge handle open panel")
                        startService(Intent(this, FloatingPanelService::class.java))
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) snapToEdge(view, params, width, screen.first, screen.second)
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
            .putBoolean(orientationKey(KEY_RIGHT), onRight)
            .putFloat(orientationKey(KEY_Y_FRACTION), params.y.toFloat() / screenHeight.coerceAtLeast(1))
            .apply()
        DebugLog.write(this, "edge handle move right=$onRight y=${params.y}")
    }

    private fun visibleHandleParams(onRight: Boolean, width: Int) =
        FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.MATCH_PARENT).apply {
            gravity = if (onRight) Gravity.RIGHT else Gravity.LEFT
        }

    private fun updateVisibleHandle(view: View, onRight: Boolean, width: Int) {
        val child = (view as? FrameLayout)?.getChildAt(0) ?: return
        child.background = handleBackground(onRight)
        child.layoutParams = visibleHandleParams(onRight, width)
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

    private fun WindowManager.LayoutParams.applyFullScreenLayoutFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        } else {
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun yMax(screenHeight: Int, handleHeight: Int): Int =
        max(dp(72), screenHeight - handleHeight - dp(96))

    private fun orientationKey(key: String): String =
        if (isLandscape()) {
            "landscape_$key"
        } else {
            "portrait_$key"
        }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            @Suppress("DEPRECATION")
            Point().also { windowManager.defaultDisplay.getRealSize(it) }.let { it.x to it.y }
        }

    companion object {
        const val PREFS = "panel_handle"
        private const val DEFAULT_WIDTH_DP = 10
        private const val MIN_TOUCH_WIDTH_DP = 48
        private const val DEFAULT_LENGTH_DP = 96
        private const val KEY_RIGHT = "right"
        private const val KEY_WIDTH_DP = "width_dp"
        private const val KEY_LENGTH_DP = "length_dp"
        private const val KEY_Y_FRACTION = "y_fraction"

        fun widthDp(context: android.content.Context): Int =
            context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP)
                .coerceIn(6, 40)

        fun setWidthDp(context: android.content.Context, widthDp: Int) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_WIDTH_DP, widthDp.coerceIn(6, 40))
                .apply()
        }

        fun touchWidthDp(context: android.content.Context): Int =
            max(widthDp(context), if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 96 else MIN_TOUCH_WIDTH_DP)

        fun lengthDp(context: android.content.Context): Int =
            context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_LENGTH_DP, DEFAULT_LENGTH_DP)
                .coerceIn(48, 240)

        fun setLengthDp(context: android.content.Context, lengthDp: Int) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_LENGTH_DP, lengthDp.coerceIn(48, 240))
                .apply()
        }
    }
}
