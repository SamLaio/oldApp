package tw.idv.samliao.apporganizer

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppOrganizerDb(context: Context) : SQLiteOpenHelper(context, "app_organizer.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE folders (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                custom INTEGER NOT NULL,
                category INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE keywords (
                folder_id TEXT NOT NULL,
                keyword TEXT NOT NULL,
                PRIMARY KEY(folder_id, keyword),
                FOREIGN KEY(folder_id) REFERENCES folders(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE assignments (
                package_name TEXT PRIMARY KEY,
                folder_id TEXT NOT NULL,
                FOREIGN KEY(folder_id) REFERENCES folders(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        createWidgetTable(db)
        DEFAULT_FOLDERS.forEach { folder ->
            saveFolder(db, folder.id, folder.name, folder.keywords, false, folder.category)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createWidgetTable(db)
    }

    fun folders(): List<OrganizerFolder> {
        val keywords = keywordsByFolder()
        val result = mutableListOf<OrganizerFolder>()
        readableDatabase.rawQuery(
            "SELECT id, name, custom, category FROM folders ORDER BY custom, rowid",
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += OrganizerFolder(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    custom = cursor.getInt(2) == 1,
                    category = if (cursor.isNull(3)) null else cursor.getInt(3),
                    keywords = keywords[cursor.getString(0)].orEmpty()
                )
            }
        }
        return result
    }

    fun addFolder(name: String, keywords: List<String>) {
        saveFolder("custom_${System.currentTimeMillis()}", name, keywords, true, null)
    }

    fun updateFolder(folder: OrganizerFolder, name: String, keywords: List<String>) {
        saveFolder(folder.id, name, keywords, folder.custom, folder.category)
    }

    fun deleteFolder(id: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("assignments", "folder_id = ?", arrayOf(id))
            writableDatabase.delete("widget_folders", "folder_id = ?", arrayOf(id))
            writableDatabase.delete("keywords", "folder_id = ?", arrayOf(id))
            writableDatabase.delete("folders", "id = ? AND custom = 1", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun assignments(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        readableDatabase.rawQuery("SELECT package_name, folder_id FROM assignments", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1)
        }
        return result
    }

    fun setAssignment(packageName: String, folderId: String?) {
        if (folderId == null) {
            writableDatabase.delete("assignments", "package_name = ?", arrayOf(packageName))
            return
        }
        writableDatabase.insertWithOnConflict(
            "assignments",
            null,
            ContentValues().apply {
                put("package_name", packageName)
                put("folder_id", folderId)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun setWidgetFolder(appWidgetId: Int, folderId: String) {
        writableDatabase.insertWithOnConflict(
            "widget_folders",
            null,
            ContentValues().apply {
                put("app_widget_id", appWidgetId)
                put("folder_id", folderId)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun widgetFolder(appWidgetId: Int): String? {
        readableDatabase.rawQuery(
            "SELECT folder_id FROM widget_folders WHERE app_widget_id = ?",
            arrayOf(appWidgetId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun deleteWidget(appWidgetId: Int) {
        writableDatabase.delete("widget_folders", "app_widget_id = ?", arrayOf(appWidgetId.toString()))
    }

    private fun keywordsByFolder(): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        readableDatabase.rawQuery(
            "SELECT folder_id, keyword FROM keywords ORDER BY folder_id, rowid",
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.getOrPut(cursor.getString(0)) { mutableListOf() } += cursor.getString(1)
            }
        }
        return result
    }

    private fun saveFolder(id: String, name: String, keywords: List<String>, custom: Boolean, category: Int?) {
        writableDatabase.beginTransaction()
        try {
            saveFolder(writableDatabase, id, name, keywords, custom, category)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun saveFolder(
        db: SQLiteDatabase,
        id: String,
        name: String,
        keywords: List<String>,
        custom: Boolean,
        category: Int?
    ) {
        db.insertWithOnConflict(
            "folders",
            null,
            ContentValues().apply {
                put("id", id)
                put("name", name.trim().ifBlank { "未命名" })
                put("custom", if (custom) 1 else 0)
                if (category == null) putNull("category") else put("category", category)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        db.delete("keywords", "folder_id = ?", arrayOf(id))
        keywords.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { keyword ->
                db.insertWithOnConflict(
                    "keywords",
                    null,
                    ContentValues().apply {
                        put("folder_id", id)
                        put("keyword", keyword)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
    }

    private fun createWidgetTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS widget_folders (
                app_widget_id INTEGER PRIMARY KEY,
                folder_id TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private companion object {
        val DEFAULT_FOLDERS = listOf(
            OrganizerFolder("communication", "通訊", listOf("chat", "message", "messenger", "sms", "mail", "gmail", "line", "telegram", "whatsapp", "signal", "discord", "zoom", "meet", "phone", "contacts"), ApplicationInfo.CATEGORY_SOCIAL, false),
            OrganizerFolder("social", "社群", listOf("facebook", "instagram", "threads", "twitter", "tiktok", "reddit", "plurk", "social"), ApplicationInfo.CATEGORY_SOCIAL, false),
            OrganizerFolder("video", "影音", listOf("youtube", "netflix", "video", "tv", "movie", "stream", "player", "vlc", "bilibili", "twitch"), ApplicationInfo.CATEGORY_VIDEO, false),
            OrganizerFolder("music", "音樂", listOf("music", "audio", "spotify", "podcast", "sound", "radio", "kkbox"), ApplicationInfo.CATEGORY_AUDIO, false),
            OrganizerFolder("photo", "相片", listOf("camera", "photo", "gallery", "image", "photos", "snapseed", "lightroom", "canva"), ApplicationInfo.CATEGORY_IMAGE, false),
            OrganizerFolder("game", "遊戲", listOf("game", "games", "puzzle", "rpg", "casino", "arcade"), ApplicationInfo.CATEGORY_GAME, false),
            OrganizerFolder("maps", "地圖", listOf("map", "maps", "nav", "navigation", "gps", "uber", "taxi", "bus", "train", "metro"), ApplicationInfo.CATEGORY_MAPS, false),
            OrganizerFolder("finance", "金融", listOf("bank", "pay", "wallet", "card", "finance", "stock", "crypto", "insurance", "tax", "paypal"), null, false),
            OrganizerFolder("shopping", "購物", listOf("shop", "shopping", "store", "mall", "market", "amazon", "shopee", "momo", "pchome", "rakuten"), null, false),
            OrganizerFolder("work", "工作", listOf("docs", "sheet", "office", "calendar", "drive", "note", "todo", "slack", "teams"), ApplicationInfo.CATEGORY_PRODUCTIVITY, false),
            OrganizerFolder("tools", "工具", listOf("tool", "tools", "scanner", "scan", "file", "manager", "cleaner", "vpn", "keyboard", "clock", "calculator", "weather"), null, false),
            OrganizerFolder("news", "新聞", listOf("news", "magazine", "rss"), ApplicationInfo.CATEGORY_NEWS, false),
            OrganizerFolder("system", "系統", listOf("settings", "installer", "permission", "launcher"), null, false)
        )
    }
}

data class OrganizerFolder(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val category: Int?,
    val custom: Boolean
)
