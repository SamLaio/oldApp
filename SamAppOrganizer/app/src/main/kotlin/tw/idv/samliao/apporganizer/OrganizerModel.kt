package tw.idv.samliao.apporganizer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import java.util.Locale

const val UNCATEGORIZED = "uncategorized"

data class AppItem(
    val label: String,
    val packageName: String,
    val category: Int,
    val isSystem: Boolean,
    val icon: Drawable
)

object OrganizerModel {
    private val systemKeywords = listOf("android", "settings", "packageinstaller", "permission")

    fun loadApps(context: Context): List<AppItem> =
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val appInfo = activityInfo.applicationInfo ?: return@mapNotNull null
            AppItem(
                label = resolveInfo.loadLabel(context.packageManager).toString(),
                packageName = activityInfo.packageName,
                category = appInfo.category,
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                icon = resolveInfo.loadIcon(context.packageManager)
            )
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.US) }

    fun classify(
        apps: List<AppItem>,
        folders: List<OrganizerFolder>,
        assignments: Map<String, String>
    ): Map<String, List<AppItem>> {
        val folderIds = (folders + uncategorizedFolder()).map { it.id }
        val grouped = folderIds.associateWith { mutableListOf<AppItem>() }.toMutableMap()
        apps.forEach { app ->
            val assigned = assignments[app.packageName]
            if (assigned in folderIds) {
                grouped.getValue(assigned!!).add(app)
                return@forEach
            }

            val ranked = folders
                .map { it to score(app, it) }
                .sortedByDescending { it.second }
            val best = ranked.firstOrNull()
            val secondScore = ranked.getOrNull(1)?.second ?: 0
            val target = if (best != null && best.second >= 40 && best.second - secondScore >= 15) {
                best.first.id
            } else {
                UNCATEGORIZED
            }
            grouped.getValue(target).add(app)
        }
        return grouped.mapValues { it.value.sortedBy { app -> app.label.lowercase(Locale.US) } }
    }

    fun uncategorizedFolder(): OrganizerFolder =
        OrganizerFolder(UNCATEGORIZED, "未分類", emptyList(), null, false)

    private fun score(app: AppItem, folder: OrganizerFolder): Int {
        var value = if (folder.category == app.category) 60 else 0
        val label = app.label.lowercase(Locale.US)
        val packageName = app.packageName.lowercase(Locale.US)
        folder.keywords.forEach { keyword ->
            val key = keyword.lowercase(Locale.US).trim()
            if (key.length < 2) return@forEach
            value += when {
                label == key -> 90
                label.contains(key) -> 50
                packageName.contains(key) -> 50
                packageSegments(packageName).any { isAdjacentSwap(it, key) } -> 45
                else -> 0
            }
        }
        if (folder.id == "system" && app.isSystem && systemKeywords.any { packageName.contains(it) }) {
            value += 80
        }
        return value
    }

    private fun packageSegments(packageName: String): List<String> =
        packageName.split('.', '_', '-').filter { it.length >= 4 }

    private fun isAdjacentSwap(value: String, keyword: String): Boolean {
        if (value.length != keyword.length) return false
        val diffs = value.indices.filter { value[it] != keyword[it] }
        return diffs.size == 2 &&
            diffs[1] == diffs[0] + 1 &&
            value[diffs[0]] == keyword[diffs[1]] &&
            value[diffs[1]] == keyword[diffs[0]]
    }
}
