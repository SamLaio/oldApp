package tw.idv.samliao.apporganizer

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class FolderWidgetConfigActivity : Activity() {
    private val db: AppOrganizerDb by lazy { AppOrganizerDb(this) }
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        showFolders()
    }

    private fun showFolders() {
        val folders = db.folders() + OrganizerModel.uncategorizedFolder()
        val iconStyle = db.iconStyle()
        val grouped = OrganizerModel.classify(
            OrganizerModel.loadApps(this),
            db.folders(),
            db.assignments()
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(24))
            setBackgroundColor(0xFFF7F8FA.toInt())
        }
        root.addView(label("選擇小工具資料夾", 26f, 0xFF202124.toInt(), Gravity.CENTER))
        folders.forEach { folder ->
            root.addView(folderRow(folder, grouped[folder.id].orEmpty(), iconStyle))
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun folderRow(folder: OrganizerFolder, apps: List<AppItem>, iconStyle: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = getDrawable(android.R.drawable.dialog_holo_light_frame)
            layoutParams = matchWrap().apply { topMargin = dp(10) }
            setOnClickListener {
                db.setWidgetFolder(appWidgetId, folder.id)
                FolderWidgetProvider.updateWidget(this@FolderWidgetConfigActivity, appWidgetId)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                )
                finish()
            }
            addView(ImageView(this@FolderWidgetConfigActivity).apply {
                setImageBitmap(folderPreviewBitmap(apps, dp(54), iconStyle))
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) }
            })
            addView(label("${folder.name} (${apps.size})", 20f, 0xFF202124.toInt(), Gravity.START))
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
}
