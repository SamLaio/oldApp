package tw.idv.samliao.seclock

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var pickAppButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 40)
        }

        root.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 28f
        })

        root.addView(TextView(this).apply {
            gravity = Gravity.CENTER
            setText(R.string.theme_hint)
            textSize = 16f
        })

        root.addView(themeButton(getString(R.string.theme_auto), ClockWidgetUpdater.THEME_AUTO))
        root.addView(themeButton(getString(R.string.theme_light), ClockWidgetUpdater.THEME_LIGHT))
        root.addView(themeButton(getString(R.string.theme_dark), ClockWidgetUpdater.THEME_DARK))

        root.addView(TextView(this).apply {
            gravity = Gravity.CENTER
            setText(R.string.click_hint)
            textSize = 16f
        })

        root.addView(clickButton(getString(R.string.click_settings), ClockWidgetUpdater.CLICK_SETTINGS))
        root.addView(clickButton(getString(R.string.click_clock), ClockWidgetUpdater.CLICK_CLOCK))
        pickAppButton = clickButton(pickAppLabel(), ClockWidgetUpdater.CLICK_APP).apply {
            setOnClickListener { showAppPicker() }
        }
        root.addView(pickAppButton)

        setContentView(root)
    }

    private fun themeButton(label: String, theme: String): Button =
        Button(this).apply {
            isAllCaps = false
            text = label
            setOnClickListener {
                getSharedPreferences(ClockWidgetUpdater.PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(ClockWidgetUpdater.KEY_THEME, theme)
                    .apply()
                ClockWidgetUpdater.updateAll(this@MainActivity)
            }
        }

    private fun clickButton(label: String, action: String): Button =
        Button(this).apply {
            isAllCaps = false
            text = label
            setOnClickListener {
                getSharedPreferences(ClockWidgetUpdater.PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(ClockWidgetUpdater.KEY_CLICK_ACTION, action)
                    .apply()
                ClockWidgetUpdater.updateAll(this@MainActivity)
            }
        }

    private fun showAppPicker() {
        val apps = launcherApps()
        if (apps.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.click_no_apps)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.click_pick_app)
            .setItems(apps.map { it.label }.toTypedArray()) { _, index ->
                getSharedPreferences(ClockWidgetUpdater.PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(ClockWidgetUpdater.KEY_CLICK_ACTION, ClockWidgetUpdater.CLICK_APP)
                    .putString(ClockWidgetUpdater.KEY_CLICK_PACKAGE, apps[index].packageName)
                    .apply()
                pickAppButton.text = pickAppLabel()
                ClockWidgetUpdater.updateAll(this)
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun launcherApps(): List<AppChoice> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { AppChoice(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun pickAppLabel(): String {
        val packageName = getSharedPreferences(ClockWidgetUpdater.PREFS, MODE_PRIVATE)
            .getString(ClockWidgetUpdater.KEY_CLICK_PACKAGE, null)
        val appLabel = packageName?.let {
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
            }.getOrNull()
        }
        return if (appLabel == null) {
            getString(R.string.click_pick_app)
        } else {
            getString(R.string.click_pick_app_with_name, appLabel)
        }
    }

    private data class AppChoice(val label: String, val packageName: String)
}
