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

data class FolderTarget(val folderId: String, val weight: Int)

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

    fun loadIndexedApps(context: Context, packageNames: Collection<String>): List<AppItem> {
        val packageManager = context.packageManager
        return packageNames.distinct().mapNotNull { packageName ->
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
            val resolveInfo = packageManager.resolveActivity(intent, 0) ?: return@mapNotNull null
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val appInfo = activityInfo.applicationInfo ?: return@mapNotNull null
            AppItem(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = packageName,
                category = appInfo.category,
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }.sortedBy { it.label.lowercase(Locale.US) }
    }

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

            grouped.getValue(automaticTarget(app, folders).folderId).add(app)
        }
        return grouped.mapValues { it.value.sortedBy { app -> app.label.lowercase(Locale.US) } }
    }

    fun uncategorizedFolder(): OrganizerFolder =
        OrganizerFolder(UNCATEGORIZED, "未分類", emptyList(), null, false)

    fun automaticTarget(app: AppItem, folders: List<OrganizerFolder>): FolderTarget {
        val target = folders.maxWithOrNull(
            compareBy<OrganizerFolder> { score(app, it) }.thenBy { if (it.custom) 1 else 0 }
        )
        val weight = target?.let { score(app, it) } ?: 0
        return if (target == null || weight == 0) {
            FolderTarget(UNCATEGORIZED, 10)
        } else {
            FolderTarget(target.id, weight)
        }
    }

    private fun score(app: AppItem, folder: OrganizerFolder): Int {
        val label = app.label.lowercase(Locale.US)
        val packageName = app.packageName.lowercase(Locale.US)
        val keywordMatch = folder.keywords.any { keyword ->
            val key = keyword.lowercase(Locale.US).trim()
            key.length >= 2 && (
                label == key ||
                    label.contains(key) ||
                    packageName.contains(key) ||
                    packageSegments(packageName).any { isAdjacentSwap(it, key) }
                )
        }
        if (keywordMatch) return 50
        if (folder.category != null && folder.category == app.category) return 20
        if (folder.id == "system" && app.isSystem && systemKeywords.any { packageName.contains(it) }) {
            return 20
        }
        return 0
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
