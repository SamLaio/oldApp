package tw.idv.samliao.quick.setting

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private var panelView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        showPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showPanel()
        return START_STICKY
    }

    override fun onDestroy() {
        panelView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    private fun showPanel() {
        panelView?.let { windowManager.removeView(it) }

        val background = PanelConfig.background(this)
        val grid = PanelConfig.grid(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackground(null)
        }

        val workspace = FrameLayout(this).apply {
            this.background = getDrawable(background.drawableRes)
        }
        val deleteTarget = deleteBall()
        val actionLayer = FrameLayout(this).apply {
            visibility = View.GONE
            addView(deleteTarget, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.RIGHT or Gravity.BOTTOM).apply {
                rightMargin = dp(8)
                bottomMargin = dp(8)
            })
        }
        root.addView(
            workspace,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val usedCells = mutableSetOf<Pair<Int, Int>>()
        PanelConfig.items(this).forEachIndexed { index, item ->
            val key = "$index:$item"
            val view = createPanelItem(item) ?: return@forEachIndexed
            val span = spanFor(item, grid)
            val itemSize = itemSize(span, grid)
            val savedPosition = PanelConfig.position(this, key) ?: defaultPosition(index, itemSize.first, itemSize.second, span)
            val position = snapFreePosition(savedPosition.x, savedPosition.y, itemSize.first, itemSize.second, span = span, grid = grid, usedCells = usedCells)
            usedCells += cellsFor(cellFor(position.x, position.y, grid = grid, span = span), span)
            PanelConfig.savePosition(this, key, position.x, position.y)

            workspace.addView(view, FrameLayout.LayoutParams(itemSize.first, itemSize.second).apply {
                leftMargin = position.x
                topMargin = position.y
            })
            makeMovable(workspace, view, key, index, actionLayer, deleteTarget)
        }

        workspace.addView(
            actionLayer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(toolButton(getString(R.string.settings_panel), "", action = ::openSettings), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    rightMargin = dp(4)
                })
                addView(toolButton(getString(R.string.close_panel), "", action = ::stopSelf), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    leftMargin = dp(4)
                })
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(8)
            }
        )

        val params = WindowManager.LayoutParams(
            panelWidth(),
            panelHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.35f
        }
        windowManager.addView(root, params)
        panelView = root
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        stopSelf()
    }

    private fun createPanelItem(item: String): View? =
        when {
            item.startsWith(PanelConfig.PREFIX_SETTING) -> {
                val id = item.removePrefix(PanelConfig.PREFIX_SETTING)
                if (id == "brightness" || id == "volume") {
                    sliderToolButton(
                        PanelConfig.settingLabel(this, id),
                        QuickActions.panelStatus(this, id),
                        QuickActions.panelIcon(this, id),
                        QuickActions.panelColor(this, id),
                        if (id == "brightness") QuickActions.brightnessPercent(this) else QuickActions.volumePercent(this),
                        showAccentLine = id == "brightness",
                        { value ->
                            if (id == "brightness") {
                                QuickActions.setBrightnessPercent(this, value)
                            } else {
                                QuickActions.setVolumePercent(this, value)
                            }
                        }
                    ) {
                        if (!QuickActions.run(this, id)) {
                            Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
                        }
                        showPanel()
                    }
                } else {
                    val lineOnly = isLineOnlySwitch(id)
                    val showLine = lineOnly || id == "wifi"
                    toolButton(
                        PanelConfig.settingLabel(this, id),
                        if (lineOnly) "" else QuickActions.panelStatus(this, id),
                        QuickActions.panelIcon(this, id),
                        QuickActions.panelColor(this, id),
                        showAccentLine = showLine
                    ) {
                        if (!QuickActions.run(this, id)) {
                            Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
                        }
                        showPanel()
                    }
                }
            }
            item.startsWith(PanelConfig.PREFIX_APP) -> {
                val packageName = item.removePrefix(PanelConfig.PREFIX_APP)
                toolButton("", PanelConfig.appLabel(this, packageName), R.drawable.ic_apps, iconDrawable = appIcon(packageName)) {
                    if (!QuickActions.launchApp(this, packageName)) {
                        Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> null
        }

    private fun toolButton(
        title: String,
        status: String,
        iconRes: Int = 0,
        accentColor: Int = 0xFF8EA2AD.toInt(),
        iconDrawable: Drawable? = null,
        showAccentLine: Boolean = false,
        action: () -> Unit
    ): LinearLayout =
        baseToolButton(title, status, iconRes, accentColor, iconDrawable, showAccentLine = showAccentLine, action = action)

    private fun sliderToolButton(
        title: String,
        status: String,
        iconRes: Int,
        accentColor: Int,
        progress: Int,
        showAccentLine: Boolean,
        onProgress: (Int) -> Unit,
        action: () -> Unit
    ): LinearLayout =
        baseToolButton(title, status, iconRes, accentColor, null, textBesideIcon = true, showAccentLine = showAccentLine, action = action).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(SeekBar(this@FloatingPanelService).apply {
                max = 100
                minHeight = dp(48)
                setPadding(0, dp(4), 0, 0)
                this.progress = progress.coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                        if (fromUser) onProgress(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

    private fun baseToolButton(
        title: String,
        status: String,
        iconRes: Int,
        accentColor: Int,
        iconDrawable: Drawable?,
        textBesideIcon: Boolean = false,
        showAccentLine: Boolean = false,
        action: () -> Unit
    ): LinearLayout =
        LinearLayout(this).apply {
            val hasIcon = iconDrawable != null || iconRes != 0
            val displayText = if (status.isNotBlank() || hasIcon) status else title
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = toolBackground()
            contentDescription = listOf(title, status).filter { it.isNotBlank() }.joinToString(" ")
            isClickable = true
            if (hasIcon && textBesideIcon) {
                addView(LinearLayout(this@FloatingPanelService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addIcon(iconRes, iconDrawable, dp(38), dp(38))
                    addStatusText(displayText, 20f, width = 0, weight = 1f)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            } else if (hasIcon) {
                addView(LinearLayout(this@FloatingPanelService).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addIcon(iconRes, iconDrawable, dp(36), dp(36), rightMargin = 0, bottomMargin = dp(5))
                    if (displayText.isNotBlank()) {
                        addStatusText(displayText, 16f)
                    }
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ))
            } else {
                addStatusText(displayText, 18f, weight = 1f)
            }
            if (showAccentLine) {
                addView(View(this@FloatingPanelService).apply {
                    setBackgroundColor(accentColor)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(5)
                ).apply {
                    topMargin = dp(5)
                })
            }
            setOnClickListener { action() }
        }

    private fun LinearLayout.addIcon(
        iconRes: Int,
        iconDrawable: Drawable?,
        width: Int,
        height: Int,
        rightMargin: Int = dp(8),
        bottomMargin: Int = 0
    ) {
        addView(ImageView(this@FloatingPanelService).apply {
            if (iconDrawable != null) {
                setImageDrawable(iconDrawable)
            } else {
                setImageResource(iconRes)
            }
            alpha = 0.9f
        }, LinearLayout.LayoutParams(width, height).apply {
            this.rightMargin = rightMargin
            this.bottomMargin = bottomMargin
        })
    }

    private fun LinearLayout.addStatusText(
        textValue: String,
        size: Float,
        width: Int = LinearLayout.LayoutParams.MATCH_PARENT,
        weight: Float = 0f
    ) {
        addView(TextView(this@FloatingPanelService).apply {
            text = textValue
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
        }, LinearLayout.LayoutParams(
            width,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            weight
        ))
    }

    private fun isLineOnlySwitch(id: String): Boolean =
        id == "rotation" || id == "sync" || id == "torch" || id == "bluetooth" || id == "location"

    private fun deleteBall(): ImageView =
        ImageView(this).apply {
            setImageResource(R.drawable.ic_trash)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = deleteBallBackground()
            alpha = 0.92f
        }

    private fun appIcon(packageName: String): Drawable? =
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()

    private fun makeMovable(
        workspace: FrameLayout,
        view: View,
        key: String,
        index: Int,
        actionLayer: View,
        deleteTarget: View
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var editMode = false
        var longPress = Runnable {}
        view.setOnTouchListener { target, event ->
            val params = target.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.leftMargin
                    startY = params.topMargin
                    moved = false
                    editMode = false
                    longPress = Runnable {
                        editMode = true
                        actionLayer.visibility = View.VISIBLE
                        actionLayer.bringToFront()
                    }
                    target.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    target.bringToFront()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        moved = true
                        target.removeCallbacks(longPress)
                    }
                    params.leftMargin = (startX + dx).coerceIn(0, max(0, workspace.width - target.width))
                    params.topMargin = (startY + dy).coerceIn(0, max(0, workspace.height - target.height))
                    target.layoutParams = params
                    true
                }
                MotionEvent.ACTION_UP -> {
                    target.removeCallbacks(longPress)
                    actionLayer.visibility = View.GONE
                    if (editMode && isOver(deleteTarget, event.rawX, event.rawY)) {
                        PanelConfig.removeAt(this, index)
                        showPanel()
                    } else if (moved) {
                        val grid = PanelConfig.grid(this)
                        val movingSpan = spanFor(PanelConfig.items(this)[index], grid)
                        val targetCell = cellFor(params.leftMargin, params.topMargin, workspace.width, workspace.height, grid, movingSpan)
                        val targetCells = cellsFor(targetCell, movingSpan)
                        val occupied = occupiedItemCells(index, target.width, target.height, workspace.width, workspace.height)
                        val hitItems = occupied.filter { it.cells.any { cell -> cell in targetCells } }
                        val snapped = positionForCell(targetCell, target.width, target.height, workspace.width, workspace.height, grid)
                        if (hitItems.isNotEmpty()) {
                            val oldCells = cellsFor(cellFor(startX, startY, workspace.width, workspace.height, grid, movingSpan), movingSpan)
                            val used = occupied.flatMap { it.cells }.toMutableSet()
                            used.removeAll(oldCells)
                            used += targetCells
                            hitItems.forEach { hitItem ->
                                used.removeAll(hitItem.cells)
                                val freeCell = nearestFreeCell(targetCell, grid, hitItem.span, used)
                                val displaced = positionForCell(freeCell ?: hitItem.origin, target.width, target.height, workspace.width, workspace.height, grid)
                                PanelConfig.savePosition(this, hitItem.key, displaced.x, displaced.y)
                                used += cellsFor(freeCell ?: hitItem.origin, hitItem.span)
                            }
                        }
                        params.leftMargin = snapped.x
                        params.topMargin = snapped.y
                        target.layoutParams = params
                        PanelConfig.savePosition(this, key, snapped.x, snapped.y)
                        if (hitItems.isNotEmpty()) showPanel()
                    } else if (!editMode) {
                        target.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    target.removeCallbacks(longPress)
                    actionLayer.visibility = View.GONE
                    true
                }
                else -> false
            }
        }
    }

    private fun isOver(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] &&
            rawX <= location[0] + view.width &&
            rawY >= location[1] &&
            rawY <= location[1] + view.height
    }

    private fun defaultPosition(index: Int, width: Int, height: Int, span: Span): PanelConfig.Position {
        val grid = PanelConfig.grid(this)
        val x = dp(8) + index % grid.columns * (width + dp(8))
        val y = dp(8) + index / grid.columns * (height + dp(8))
        return snapPosition(min(x, panelWidth() - width - dp(16)), y, width, height, span = span, grid = grid)
    }

    private fun snapPosition(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        workspaceWidth: Int = panelWidth() - dp(16),
        workspaceHeight: Int = panelHeight() - dp(96),
        span: Span = Span(1, 1),
        grid: PanelConfig.Grid = PanelConfig.grid(this)
    ): PanelConfig.Position {
        return positionForCell(cellFor(x, y, workspaceWidth, workspaceHeight, grid, span), width, height, workspaceWidth, workspaceHeight, grid)
    }

    private fun snapFreePosition(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        workspaceWidth: Int = panelWidth() - dp(16),
        workspaceHeight: Int = panelHeight() - dp(96),
        span: Span = Span(1, 1),
        grid: PanelConfig.Grid = PanelConfig.grid(this),
        usedCells: Set<Pair<Int, Int>>
    ): PanelConfig.Position {
        val target = cellFor(x, y, workspaceWidth, workspaceHeight, grid, span)
        val targetCells = cellsFor(target, span)
        val cell = if (targetCells.none { it in usedCells }) {
            target
        } else {
            gridOrigins(grid, span)
                .filter { origin -> cellsFor(origin, span).none { it in usedCells } }
                .minByOrNull { abs(it.first - target.first) + abs(it.second - target.second) }
                ?: target
        }
        return positionForCell(cell, width, height, workspaceWidth, workspaceHeight, grid)
    }

    private fun occupiedItemCells(
        skipIndex: Int,
        width: Int,
        height: Int,
        workspaceWidth: Int,
        workspaceHeight: Int
    ): List<ItemCell> {
        val grid = PanelConfig.grid(this)
        return PanelConfig.items(this).mapIndexedNotNull { index, item ->
            if (index == skipIndex) return@mapIndexedNotNull null
            val key = "$index:$item"
            val span = spanFor(item, grid)
            val position = PanelConfig.position(this, key) ?: defaultPosition(index, width, height, span)
            val origin = cellFor(position.x, position.y, workspaceWidth, workspaceHeight, grid, span)
            ItemCell(key, origin, span, cellsFor(origin, span))
        }
    }

    private fun nearestFreeCell(
        target: Pair<Int, Int>,
        grid: PanelConfig.Grid,
        span: Span,
        usedCells: Set<Pair<Int, Int>>
    ): Pair<Int, Int>? =
        gridOrigins(grid, span)
            .filter { origin -> cellsFor(origin, span).none { it in usedCells } }
            .minByOrNull { abs(it.first - target.first) + abs(it.second - target.second) }

    private fun cellFor(
        x: Int,
        y: Int,
        workspaceWidth: Int = panelWidth() - dp(16),
        workspaceHeight: Int = panelHeight() - dp(96),
        grid: PanelConfig.Grid = PanelConfig.grid(this),
        span: Span = Span(1, 1)
    ): Pair<Int, Int> {
        val edge = dp(8)
        val stepX = max(1, (workspaceWidth - edge * 2) / grid.columns)
        val stepY = max(1, (workspaceHeight - edge * 2) / grid.rows)
        val column = max(0, (x - edge + stepX / 2) / stepX)
        val row = max(0, (y - edge + stepY / 2) / stepY)
        return column.coerceAtMost(grid.columns - span.columns) to row.coerceAtMost(grid.rows - span.rows)
    }

    private fun positionForCell(
        cell: Pair<Int, Int>,
        width: Int,
        height: Int,
        workspaceWidth: Int,
        workspaceHeight: Int,
        grid: PanelConfig.Grid
    ): PanelConfig.Position {
        val edge = dp(8)
        val stepX = max(1, (workspaceWidth - edge * 2) / grid.columns)
        val stepY = max(1, (workspaceHeight - edge * 2) / grid.rows)
        return PanelConfig.Position(
            (edge + cell.first * stepX).coerceIn(0, max(0, workspaceWidth - width)),
            (edge + cell.second * stepY).coerceIn(0, max(0, workspaceHeight - height))
        )
    }

    private fun spanFor(item: String, grid: PanelConfig.Grid): Span {
        val id = item.removePrefix(PanelConfig.PREFIX_SETTING)
        return if (item.startsWith(PanelConfig.PREFIX_SETTING) && (id == "brightness" || id == "volume")) {
            Span(grid.columns, 1)
        } else {
            Span(1, 1)
        }
    }

    private fun cellsFor(origin: Pair<Int, Int>, span: Span): Set<Pair<Int, Int>> =
        (origin.first until origin.first + span.columns).flatMap { column ->
            (origin.second until origin.second + span.rows).map { row -> column to row }
        }.toSet()

    private fun gridOrigins(grid: PanelConfig.Grid, span: Span): List<Pair<Int, Int>> =
        (0..(grid.columns - span.columns)).flatMap { column ->
            (0..(grid.rows - span.rows)).map { row -> column to row }
        }

    private fun itemSize(span: Span, grid: PanelConfig.Grid): Pair<Int, Int> {
        val workspaceWidth = panelWidth() - dp(16)
        val workspaceHeight = panelHeight() - dp(96)
        val edge = dp(8)
        val gap = dp(8)
        val cellWidth = (workspaceWidth - edge * 2 - gap * (grid.columns - 1)) / grid.columns
        val cellHeight = (workspaceHeight - edge * 2 - gap * (grid.rows - 1)) / grid.rows
        val rowHeight = min(cellHeight, dp(132)).coerceAtLeast(dp(96))
        if (span.columns > 1) {
            return (cellWidth * span.columns + gap * (span.columns - 1)) to rowHeight
        }
        return cellWidth to rowHeight
    }

    private fun toolBackground() = GradientDrawable().apply {
        setColor(0xCC111820.toInt())
        setStroke(dp(1), 0x668EA2AD)
    }

    private fun deleteBallBackground() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFFFF8200.toInt())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun panelWidth(): Int = resources.displayMetrics.widthPixels - dp(28)

    private fun panelHeight(): Int = resources.displayMetrics.heightPixels - dp(104)

    private data class Span(val columns: Int, val rows: Int)

    private data class ItemCell(
        val key: String,
        val origin: Pair<Int, Int>,
        val span: Span,
        val cells: Set<Pair<Int, Int>>
    )
}
