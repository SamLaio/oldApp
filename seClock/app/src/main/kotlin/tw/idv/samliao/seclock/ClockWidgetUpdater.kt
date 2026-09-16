package tw.idv.samliao.seclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.provider.AlarmClock
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date

object ClockWidgetUpdater {
    const val PREFS = "clock_widgets"
    const val KEY_THEME = "theme"
    const val KEY_CLICK_ACTION = "click_action"
    const val KEY_CLICK_PACKAGE = "click_package"
    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val CLICK_SETTINGS = "settings"
    const val CLICK_CLOCK = "clock"
    const val CLICK_APP = "app"

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        update(context, manager, manager.getAppWidgetIds(ComponentName(context, PlainClockWidgetProvider::class.java)), R.layout.widget_plain)
        update(context, manager, manager.getAppWidgetIds(ComponentName(context, ThinClockWidgetProvider::class.java)), R.layout.widget_thin)
        update(context, manager, manager.getAppWidgetIds(ComponentName(context, VerticalClockWidgetProvider::class.java)), R.layout.widget_vertical)
        update(context, manager, manager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider::class.java)), R.layout.widget_analog)
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray, layoutId: Int) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, themedLayoutId(context, layoutId))
            applyTheme(context, views)
            views.setTextViewText(R.id.clock_alarm, nextAlarmText(context))
            views.setOnClickPendingIntent(R.id.clock_root, clickPendingIntent(context))
            manager.updateAppWidget(id, views)
        }
    }

    private fun themedLayoutId(context: Context, layoutId: Int): Int {
        return if (layoutId == R.layout.widget_analog && shouldUseDark(context)) {
            R.layout.widget_analog_black
        } else {
            layoutId
        }
    }

    private fun applyTheme(context: Context, views: RemoteViews) {
        val dark = shouldUseDark(context)
        val foreground = if (dark) Color.argb(204, 17, 17, 17) else Color.WHITE
        val secondary = if (dark) Color.argb(204, 17, 17, 17) else Color.rgb(225, 225, 225)

        views.setInt(R.id.clock_time, "setTextColor", foreground)
        views.setInt(R.id.clock_minute, "setTextColor", foreground)
        views.setInt(R.id.clock_date, "setTextColor", secondary)
        views.setInt(R.id.clock_alarm, "setTextColor", secondary)
    }

    private fun shouldUseDark(context: Context): Boolean {
        return when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, THEME_AUTO)) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            else -> {
                val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun nextAlarmText(context: Context): String {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarm = manager?.nextAlarmClock ?: return ""
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alarm.triggerTime))
        return context.getString(R.string.next_alarm, time)
    }

    private fun clickPendingIntent(context: Context): PendingIntent {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val intent = when (prefs.getString(KEY_CLICK_ACTION, CLICK_SETTINGS)) {
            CLICK_CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            CLICK_APP -> prefs.getString(KEY_CLICK_PACKAGE, null)
                ?.let { context.packageManager.getLaunchIntentForPackage(it) }
                ?: settingsIntent(context)
            else -> settingsIntent(context)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun settingsIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
}
