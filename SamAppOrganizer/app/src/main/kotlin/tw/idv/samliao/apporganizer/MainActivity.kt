package tw.idv.samliao.apporganizer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val db: AppOrganizerDb by lazy { AppOrganizerDb(this) }
    private var atRoot = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showFolders()
    }

    private fun showFolders() {
        atRoot = true
        val folders = db.folders()
        val grouped = groupedApps(folders)
        val root = rootLayout()

        root.addView(label(getString(R.string.app_name), 32f, 0xFF202124.toInt(), Gravity.CENTER))
        root.addView(label(getString(R.string.app_summary), 16f, 0xFF5F6368.toInt(), Gravity.CENTER).apply {
            setPadding(0, dp(8), 0, dp(14))
        })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWrap()
            addView(actionButton("新增資料夾") { editFolder(null) }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(6)
                }
            })
            addView(actionButton("重新整理") { refreshClassification() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(6)
                }
            })
        })
        root.addView(label("長按資料夾可改名稱與關鍵字。", 14f, 0xFF5F6368.toInt(), Gravity.START).apply {
            setPadding(0, dp(14), 0, dp(4))
        })

        (folders + OrganizerModel.uncategorizedFolder()).forEach { folder ->
            root.addView(folderRow(folder, grouped[folder.id].orEmpty()))
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showFolder(folder: OrganizerFolder, apps: List<AppItem>) {
        atRoot = false
        val root = rootLayout()
        root.addView(actionButton("返回") { showFolders() })
        root.addView(label("${folder.name} (${apps.size})", 28f, 0xFF202124.toInt(), Gravity.CENTER).apply {
            setPadding(0, dp(12), 0, dp(8))
        })
        if (apps.isEmpty()) {
            root.addView(label("沒有 app。", 16f, 0xFF5F6368.toInt(), Gravity.CENTER))
        } else {
            apps.forEach { root.addView(appRow(it)) }
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (atRoot) finish() else showFolders()
    }

    private fun editFolder(folder: OrganizerFolder?) {
        val isNew = folder == null
        val nameInput = EditText(this).apply {
            hint = "資料夾名稱"
            setText(folder?.name.orEmpty())
            setSingleLine(true)
        }
        val keywordInput = EditText(this).apply {
            hint = "關鍵字，以逗號或換行分隔"
            setText(folder?.keywords.orEmpty().joinToString(", "))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(nameInput)
            addView(keywordInput)
        }

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "新增資料夾" else "編輯資料夾")
            .setView(content)
            .setPositiveButton("儲存") { _, _ ->
                val keywords = keywordInput.text.toString().split(',', '\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (isNew) db.addFolder(nameInput.text.toString(), keywords)
                else db.updateFolder(folder!!, nameInput.text.toString(), keywords)
                FolderWidgetProvider.updateAll(this)
                showFolders()
            }
            .setNegativeButton("取消", null)
            .apply {
                if (!isNew && folder?.custom == true) {
                    setNeutralButton("刪除") { _, _ ->
                        db.deleteFolder(folder.id)
                        FolderWidgetProvider.updateAll(this@MainActivity)
                        showFolders()
                    }
                }
            }
            .show()
    }

    private fun chooseFolderForApp(app: AppItem) {
        val folders = db.folders() + OrganizerModel.uncategorizedFolder()
        val labels = arrayOf("自動分類") + folders.map { it.name }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(labels) { _, index ->
                db.setAssignment(app.packageName, if (index == 0) null else folders[index - 1].id)
                FolderWidgetProvider.updateAll(this)
                Toast.makeText(this, "已更新分類", Toast.LENGTH_SHORT).show()
                showFolders()
            }
            .show()
    }

    private fun refreshClassification() {
        FolderWidgetProvider.updateAll(this)
        Toast.makeText(this, "已重新掃描並歸類 app", Toast.LENGTH_SHORT).show()
        showFolders()
    }

    private fun groupedApps(folders: List<OrganizerFolder>): Map<String, List<AppItem>> =
        OrganizerModel.classify(OrganizerModel.loadApps(this), folders, db.assignments())

    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) Toast.makeText(this, "無法開啟 app", Toast.LENGTH_SHORT).show()
        else startActivity(intent)
    }

    private fun folderRow(folder: OrganizerFolder, apps: List<AppItem>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = getDrawable(android.R.drawable.dialog_holo_light_frame)
            layoutParams = matchWrap().apply { topMargin = dp(10) }
            setOnClickListener { showFolder(folder, apps) }
            setOnLongClickListener {
                editFolder(folder)
                true
            }
            addView(ImageView(this@MainActivity).apply {
                setImageBitmap(folderPreviewBitmap(apps, dp(54)))
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) }
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(label("${folder.name} (${apps.size})", 20f, 0xFF202124.toInt(), Gravity.START))
                addView(label(folder.keywords.joinToString(", ").ifBlank { "無關鍵字" }, 13f, 0xFF5F6368.toInt(), Gravity.START))
            })
        }

    private fun appRow(app: AppItem): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = getDrawable(android.R.drawable.dialog_holo_light_frame)
            layoutParams = matchWrap().apply { topMargin = dp(8) }
            setOnClickListener { openApp(app.packageName) }
            setOnLongClickListener {
                chooseFolderForApp(app)
                true
            }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) }
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(label(app.label, 18f, 0xFF202124.toInt(), Gravity.START))
                addView(label(app.packageName, 13f, 0xFF5F6368.toInt(), Gravity.START))
            })
        }

    private fun rootLayout(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(38), dp(20), dp(24))
            setBackgroundColor(0xFFF7F8FA.toInt())
        }

    private fun actionButton(textValue: String, action: () -> Unit): Button =
        Button(this).apply {
            text = textValue
            textSize = 17f
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
}
