package com.samliao.seclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class PlainClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ClockWidgetUpdater.update(context, manager, ids, R.layout.widget_plain)
    }
}
