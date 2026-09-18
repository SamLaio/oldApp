package tw.idv.samliao.quick.setting

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var itemsView: LinearLayout
    private lateinit var storageView: TextView
    private lateinit var lockButton: Button
    private lateinit var debugLogButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handlePanelIntent(intent)) return
        setContentView(createContentView())
        refreshPanelItems()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePanelIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::storageView.isInitialized) storageView.text = QuickActions.storageInfo(this)
        if (::lockButton.isInitialized) updateLockButton()
        if (::debugLogButton.isInitialized) updateDebugLogButton()
        ensureEdgeHandle()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(40), dp(20), dp(28))
            setBackgroundColor(0xFFF8F8F5.toInt())
        }

        root.addView(label(getString(R.string.app_name), 32f, 0xFF202124.toInt(), Gravity.CENTER))
        root.addView(label(getString(R.string.app_summary), 16f, 0xFF5F6368.toInt(), Gravity.CENTER).apply {
            setPadding(0, dp(10), 0, dp(18))
        })

        root.addView(actionButton(R.string.open_floating_panel) { openFloatingPanel() })
        root.addView(actionButton(R.string.edge_handle_settings) {
            startActivity(Intent(this, EdgeHandleSettingsActivity::class.java))
        })
        root.addView(actionButton(R.string.choose_tool) { chooseToolGroup() })
        root.addView(actionButton(R.string.choose_background) { chooseBackground() })
        root.addView(actionButton(R.string.choose_grid) { chooseGrid() })
        lockButton = actionButton(R.string.lock_panel) { togglePanelLock() }
        root.addView(lockButton)
        debugLogButton = actionButton(R.string.enable_debug_log) { toggleDebugLog() }
        root.addView(debugLogButton)
        root.addView(actionButton(R.string.clear_panel) {
            PanelConfig.clear(this)
            refreshPanelItems()
        })
        root.addView(actionButton(R.string.settings_done) { finish() })

        storageView = label("", 16f, 0xFF202124.toInt(), Gravity.START).apply {
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(storageView)

        root.addView(label(getString(R.string.panel_items_title), 18f, 0xFF202124.toInt(), Gravity.START).apply {
            setPadding(0, dp(16), 0, 0)
        })
        itemsView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap()
        }
        root.addView(itemsView)

        return ScrollView(this).apply { addView(root) }
    }

    private fun handlePanelIntent(intent: Intent?): Boolean {
        if (intent?.action != QuickActions.ACTION_SHOW_PANEL) return false
        startActivity(Intent(this, PanelLauncherActivity::class.java).setAction(QuickActions.ACTION_SHOW_PANEL))
        finish()
        return true
    }

    private fun chooseToolGroup() {
        val labels = arrayOf(
            getString(R.string.group_system_settings),
            getString(R.string.group_indicators),
            getString(R.string.action_app),
            getString(R.string.group_tools)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_tool)
            .setItems(labels) { _, index ->
                when (index) {
                    0 -> chooseSettingButton(R.string.group_system_settings, PanelConfig.systemSettings)
                    1 -> chooseSettingButton(R.string.group_indicators, PanelConfig.indicators)
                    2 -> chooseApplication()
                    3 -> chooseTool()
                }
            }
            .show()
    }

    private fun chooseTool() {
        val labels = arrayOf(getString(R.string.action_torch))
        AlertDialog.Builder(this)
            .setTitle(R.string.group_tools)
            .setItems(labels) { _, _ ->
                addSetting("torch")
            }
            .show()
    }

    private fun chooseSettingButton(titleRes: Int, settings: List<PanelConfig.Setting>) {
        val labels = settings.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setItems(labels) { _, index ->
                addSetting(settings[index].id)
            }
            .show()
    }

    private fun addSetting(id: String) {
        if (!PanelConfig.canAddSetting(this, id)) {
            Toast.makeText(this, R.string.panel_no_space, Toast.LENGTH_SHORT).show()
            return
        }
        DebugLog.write(this, "settings add setting=$id")
        PanelConfig.addSetting(this, id)
        refreshPanelItems()
    }

    private fun chooseApplication() {
        val apps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString() }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.action_app)
            .setItems(labels) { _, index ->
                if (!PanelConfig.canAddApp(this)) {
                    Toast.makeText(this, R.string.panel_no_space, Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                val packageName = apps[index].activityInfo.packageName
                DebugLog.write(this, "settings add app package=$packageName")
                PanelConfig.addApp(this, packageName)
                refreshPanelItems()
            }
            .show()
    }

    private fun chooseBackground() {
        val current = PanelConfig.background(this).id
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xDD263238.toInt())
            addView(label(getString(R.string.choose_background), 22f, 0xFFFFFFFF.toInt(), Gravity.START).apply {
                setPadding(dp(8), dp(4), dp(8), dp(12))
            })
        }
        val dialog = AlertDialog.Builder(this).create()
        PanelConfig.backgrounds.forEach { background ->
            content.addView(TextView(this).apply {
                text = "${if (background.id == current) "✓ " else "○ "}${getString(background.labelRes)}"
                textSize = 20f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(0xFFFFFFFF.toInt())
                setShadowLayer(3f, 1f, 1f, 0xCC000000.toInt())
                this.background = getDrawable(background.drawableRes)
                setPadding(dp(20), 0, dp(20), 0)
                setOnClickListener {
                    PanelConfig.setBackground(this@MainActivity, background.id)
                    dialog.dismiss()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(76)
                ).apply {
                    topMargin = dp(6)
                }
            })
        }
        dialog.setView(ScrollView(this).apply { addView(content) })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(0x00000000))
        }
        dialog.show()
    }

    private fun chooseGrid() {
        val labels = PanelConfig.grids.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_grid)
            .setItems(labels) { _, index ->
                val grid = PanelConfig.grids[index]
                if (!PanelConfig.canUseGrid(this, grid)) {
                    Toast.makeText(this, R.string.grid_too_small, Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                PanelConfig.setGrid(this, grid.id)
            }
            .show()
    }

    private fun togglePanelLock() {
        val locked = !PanelConfig.isLocked(this)
        PanelConfig.setLocked(this, locked)
        DebugLog.write(this, "settings panel locked=$locked")
        updateLockButton()
    }

    private fun updateLockButton() {
        lockButton.text = getString(if (PanelConfig.isLocked(this)) R.string.unlock_panel else R.string.lock_panel)
    }

    private fun toggleDebugLog() {
        DebugLog.setEnabled(this, !DebugLog.isEnabled(this))
        updateDebugLogButton()
    }

    private fun updateDebugLogButton() {
        debugLogButton.text = getString(if (DebugLog.isEnabled(this)) R.string.disable_debug_log else R.string.enable_debug_log)
    }

    private fun openFloatingPanel() {
        if (!QuickActions.canDrawOverlays(this)) {
            QuickActions.requestOverlayPermission(this)
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
        startService(Intent(this, FloatingPanelService::class.java))
    }

    private fun ensureEdgeHandle() {
        if (PanelHandleService.isEnabled(this) && QuickActions.canDrawOverlays(this)) {
            startService(Intent(this, PanelHandleService::class.java))
        }
    }

    private fun refreshPanelItems() {
        itemsView.removeAllViews()
        val items = PanelConfig.items(this)
        if (items.isEmpty()) {
            itemsView.addView(label(getString(R.string.panel_empty), 15f, 0xFF5F6368.toInt(), Gravity.START))
            return
        }
        items.forEachIndexed { index, item ->
            val text = when {
                item.startsWith(PanelConfig.PREFIX_SETTING) -> {
                    PanelConfig.settingLabel(this, item.removePrefix(PanelConfig.PREFIX_SETTING))
                }
                item.startsWith(PanelConfig.PREFIX_APP) -> {
                    PanelConfig.appLabel(this, item.removePrefix(PanelConfig.PREFIX_APP))
                }
                else -> item
            }
            itemsView.addView(label("${index + 1}. $text", 15f, 0xFF3C4043.toInt(), Gravity.START).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun actionButton(labelRes: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = getString(labelRes)
            textSize = 18f
            setAllCaps(false)
            setOnClickListener { action() }
            layoutParams = matchWrap().apply { topMargin = dp(10) }
        }

    private fun label(textValue: String, size: Float, color: Int, gravityValue: Int): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            gravity = gravityValue
            setTextColor(color)
            layoutParams = matchWrap()
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_CAMERA = 2003
    }
}
