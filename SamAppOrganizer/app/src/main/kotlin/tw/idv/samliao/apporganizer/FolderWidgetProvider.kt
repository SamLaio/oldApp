package tw.idv.samliao.apporganizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class FolderWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val db = AppOrganizerDb(context)
        appWidgetIds.forEach { db.deleteWidget(it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FolderWidgetProvider::class.java))
            ids.forEach { updateWidget(context, it) }
        }

        fun updateWidget(context: Context, appWidgetId: Int) {
            val db = AppOrganizerDb(context)
            val folderId = db.widgetFolder(appWidgetId)
            val iconStyle = db.iconStyle()
            val folders = db.folders()
            val folder = (folders + OrganizerModel.uncategorizedFolder()).firstOrNull { it.id == folderId }
            val apps = if (folder == null) {
                emptyList()
            } else {
                OrganizerModel.classify(
                    OrganizerModel.loadApps(context),
                    folders,
                    db.assignments()
                )[folder.id].orEmpty()
            }

            val views = RemoteViews(context.packageName, R.layout.widget_folder_icon)
            views.setTextViewText(R.id.widget_folder_label, folder?.name ?: "資料夾")
            views.setImageViewBitmap(
                R.id.widget_folder_icon,
                folderPreviewBitmap(apps, dp(context, 54), iconStyle)
            )
            views.setOnClickPendingIntent(R.id.widget_folder_root, folderPendingIntent(context, appWidgetId))
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }

        private fun folderPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, FolderPopupActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("samapporganizer://widget/$appWidgetId"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}
