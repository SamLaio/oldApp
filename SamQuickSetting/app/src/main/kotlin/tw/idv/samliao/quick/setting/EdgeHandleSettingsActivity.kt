package tw.idv.samliao.quick.setting

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

class EdgeHandleSettingsActivity : Activity() {
    private lateinit var visibilityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@EdgeHandleSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(20), dp(40), dp(20), dp(28))
                setBackgroundColor(0xFFF8F8F5.toInt())
                visibilityButton = actionButton { toggleHandle() }
                addView(visibilityButton)
                addView(actionButton { chooseWidth() }.apply { text = getString(R.string.edge_handle_width) })
                addView(actionButton { chooseLength() }.apply { text = getString(R.string.edge_handle_length) })
                addView(actionButton { finish() }.apply { text = getString(R.string.settings_done) })
            })
        })
        updateVisibilityButton()
    }

    override fun onResume() {
        super.onResume()
        updateVisibilityButton()
    }

    private fun toggleHandle() {
        if (PanelHandleService.isEnabled(this)) {
            PanelHandleService.setEnabled(this, false)
            stopService(Intent(this, PanelHandleService::class.java))
            DebugLog.write(this, "settings edge handle hidden")
            updateVisibilityButton()
            return
        }
        if (!QuickActions.canDrawOverlays(this)) {
            QuickActions.requestOverlayPermission(this)
            return
        }
        PanelHandleService.setEnabled(this, true)
        startService(Intent(this, PanelHandleService::class.java))
        DebugLog.write(this, "settings edge handle shown")
        updateVisibilityButton()
    }

    private fun chooseWidth() = chooseValue(
        R.string.edge_handle_width,
        intArrayOf(6, 8, 10, 12, 16, 20),
        PanelHandleService.widthDp(this)
    ) { PanelHandleService.setWidthDp(this, it) }

    private fun chooseLength() = chooseValue(
        R.string.edge_handle_length,
        intArrayOf(64, 80, 96, 120, 144, 180),
        PanelHandleService.lengthDp(this)
    ) { PanelHandleService.setLengthDp(this, it) }

    private fun chooseValue(title: Int, values: IntArray, current: Int, save: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(values.map { "$it dp" }.toTypedArray(), values.indexOf(current)) { dialog, index ->
                save(values[index])
                if (PanelHandleService.isEnabled(this)) startService(Intent(this, PanelHandleService::class.java))
                dialog.dismiss()
            }
            .show()
    }

    private fun updateVisibilityButton() {
        if (::visibilityButton.isInitialized) {
            visibilityButton.text = getString(if (PanelHandleService.isEnabled(this)) R.string.hide_edge_handle else R.string.show_edge_handle)
        }
    }

    private fun actionButton(action: () -> Unit) = Button(this).apply {
        textSize = 18f
        setAllCaps(false)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
