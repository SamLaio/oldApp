package tw.idv.samliao.apporganizer

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class FolderPopupActivity : Activity() {
    private val db: AppOrganizerDb by lazy { AppOrganizerDb(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        showPopup(appWidgetId)
    }

    private fun showPopup(appWidgetId: Int) {
        val folders = db.folders()
        val folderId = db.widgetFolder(appWidgetId)
        val folder = (folders + OrganizerModel.uncategorizedFolder()).firstOrNull { it.id == folderId }
        if (folder == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val apps = OrganizerModel.classify(
            OrganizerModel.loadApps(this),
            folders,
            db.assignments()
        )[folder.id].orEmpty()

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { finish() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.folder_popup_background)
            setPadding(dp(18), dp(16), dp(18), dp(14))
            setOnClickListener { }
            layoutParams = FrameLayout.LayoutParams(dp(300), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        panel.addView(TextView(this).apply {
            text = folder.name
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
            setOnLongClickListener {
                startActivity(Intent(this@FolderPopupActivity, MainActivity::class.java))
                finish()
                true
            }
        })
        panel.addView(GridLayout(this).apply {
            columnCount = 3
            apps.take(9).forEach { app -> addView(appCell(app)) }
        })
        root.addView(panel)
        setContentView(root)
    }

    private fun appCell(app: AppItem): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(88)
                height = dp(92)
            }
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                finish()
            }
            addView(ImageView(this@FolderPopupActivity).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            })
            addView(TextView(this@FolderPopupActivity).apply {
                text = app.label
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(5) }
            })
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
