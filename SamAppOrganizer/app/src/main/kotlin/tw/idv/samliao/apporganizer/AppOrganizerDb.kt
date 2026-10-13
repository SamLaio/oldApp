package tw.idv.samliao.apporganizer

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppOrganizerDb(context: Context) : SQLiteOpenHelper(context, "app_organizer.db", null, 6) {
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
        createSettingsTable(db)
        createManualUncategorizedTable(db)
        DEFAULT_FOLDERS.forEach { folder -> saveFolder(db, folder.id, folder.name, folder.keywords, false, folder.category) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createWidgetTable(db)
        if (oldVersion < 4) createSettingsTable(db)
        if (oldVersion < 5) createManualUncategorizedTable(db)
        if (oldVersion < 6) mergeDefaultFolders(db)
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

    fun importFolders(folders: List<OrganizerFolder>): Int {
        writableDatabase.beginTransaction()
        try {
            folders.forEachIndexed { index, folder ->
                val id = folder.id.takeIf { it.isNotBlank() && it != UNCATEGORIZED }
                    ?: "import_${System.currentTimeMillis()}_$index"
                val existing = writableDatabase.rawQuery(
                    "SELECT custom, category FROM folders WHERE id = ?",
                    arrayOf(id)
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null
                    else (cursor.getInt(0) == 1) to (if (cursor.isNull(1)) null else cursor.getInt(1))
                }
                saveFolder(
                    writableDatabase,
                    id,
                    folder.name,
                    folder.keywords,
                    existing?.first ?: true,
                    existing?.second ?: folder.category
                )
            }
            writableDatabase.setTransactionSuccessful()
            return folders.size
        } finally {
            writableDatabase.endTransaction()
        }
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
        readableDatabase.rawQuery("SELECT package_name FROM manual_uncategorized", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = UNCATEGORIZED
        }
        return result
    }

    fun setAssignment(packageName: String, folderId: String?) {
        val db = writableDatabase
        when (folderId) {
            null -> {
                db.delete("assignments", "package_name = ?", arrayOf(packageName))
                db.delete("manual_uncategorized", "package_name = ?", arrayOf(packageName))
            }
            UNCATEGORIZED -> {
                db.insertWithOnConflict(
                    "manual_uncategorized",
                    null,
                    ContentValues().apply { put("package_name", packageName) },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                db.delete("assignments", "package_name = ?", arrayOf(packageName))
            }
            else -> {
                db.insertWithOnConflict(
                    "assignments",
                    null,
                    ContentValues().apply {
                        put("package_name", packageName)
                        put("folder_id", folderId)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                db.delete("manual_uncategorized", "package_name = ?", arrayOf(packageName))
            }
        }
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

    fun iconStyle(): String {
        readableDatabase.rawQuery(
            "SELECT value FROM settings WHERE key = ?",
            arrayOf(FOLDER_ICON_STYLE)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else FolderIconStyle.PREVIEW
        }
    }

    fun setIconStyle(style: String) {
        writableDatabase.insertWithOnConflict(
            "settings",
            null,
            ContentValues().apply {
                put("key", FOLDER_ICON_STYLE)
                put("value", style)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
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

    private fun saveFolder(
        id: String,
        name: String,
        keywords: List<String>,
        custom: Boolean,
        category: Int?
    ) {
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
        val values = ContentValues().apply {
            put("name", name.trim().ifBlank { "未命名" })
            put("custom", if (custom) 1 else 0)
            if (category == null) putNull("category") else put("category", category)
        }
        if (db.update("folders", values, "id = ?", arrayOf(id)) == 0) {
            values.put("id", id)
            db.insertOrThrow("folders", null, values)
        }
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

    private fun createSettingsTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
        )
    }

    private fun createManualUncategorizedTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS manual_uncategorized (package_name TEXT PRIMARY KEY)")
    }

    private fun mergeDefaultFolders(db: SQLiteDatabase) {
        DEFAULT_FOLDERS.forEach { folder ->
            val storedKeywords = mutableListOf<String>()
            db.rawQuery("SELECT keyword FROM keywords WHERE folder_id = ?", arrayOf(folder.id)).use { cursor ->
                while (cursor.moveToNext()) storedKeywords += cursor.getString(0)
            }
            if (storedKeywords.isEmpty()) {
                val exists = db.rawQuery("SELECT 1 FROM folders WHERE id = ?", arrayOf(folder.id)).use { it.moveToFirst() }
                if (!exists) saveFolder(db, folder.id, folder.name, folder.keywords, false, folder.category)
                return@forEach
            }
            val legacy = LEGACY_DEFAULT_FOLDERS.firstOrNull { it.id == folder.id } ?: return@forEach
            if (storedKeywords.toSet() == legacy.keywords.toSet()) {
                folder.keywords.filterNot { it in storedKeywords }.forEach { keyword ->
                    db.insertOrThrow("keywords", null, ContentValues().apply {
                        put("folder_id", folder.id)
                        put("keyword", keyword)
                    })
                }
            }
        }
    }

    private companion object {
        const val FOLDER_ICON_STYLE = "folder_icon_style"
        val LEGACY_DEFAULT_FOLDERS = listOf(
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

        val DEFAULT_APP_NAMES = mapOf(
            "communication" to listOf("Carrier Services", "Discord - Talk, Play, Hang Out", "Firefox Fast & Private Browser", "FolderSync Pro", "Google Chat", "Google Pixel Watch", "LINE: Calls & Messages", "Messenger", "Samsung Accessory Service", "Telegram", "Thunderbird: Free Your Inbox", "Google Meet"),
            "social" to listOf("巴哈姆特 - 華人最大電玩動漫社群網站", "Facebook", "Gleezy Pro", "Instagram", "Message Peeping Tom", "Mobile01", "Threads", "X"),
            "video" to listOf("GoPro Quik: Video Editor", "VLC for Android", "MX Player Pro"),
            "music" to listOf("AntennaPod - Podcast Player", "foobar2000", "Google Pixel Buds", "Marshall", "Neutron Music Player", "XROUND MyTune"),
            "photo" to listOf("Gallery", "PhotoScan by Google Photos", "Snapseed", "Synology Photos", "Microsoft Lens"),
            "game" to listOf("Sudokuplus.net -classic sudoku"),
            "maps" to listOf("55688 Taiwan Taxi", "WeMo - 24H Scooter Sharing"),
            "finance" to listOf("【富貴角10號】sk88新光證券", "台新銀行 「行動銀行」", "國泰世華 行動銀行", "發票存摺：電子發票兌獎、載具歸戶神器", "街口支付", "集保e手掌握(集保e存摺/手機存摺)", "新光銀行", "AndroMoney ( Expense Track )", "Binance: Buy Bitcoin & Crypto", "Easy Wallet 悠遊付", "Fubon+ Mobile Banking", "Google Wallet", "Kraken Pro: Advanced Trading", "MAX Exchange - Buy Bitcoin"),
            "shopping" to listOf("健身工廠商城", "蝦皮購物 | 花得更少買得更好", "露天市集", "Amazon Shopping", "AppSales", "momo購物 | 值得信賴的購物首選", "PChome24h購物 | 每天一起變更好", "Rakuten樂天市場購物網", "Yahoo拍賣"),
            "work" to listOf("網紅小遙", "AutoApps", "Calendar Widget (key)", "Calendar Widget Month + Agenda", "ChatGPT", "EinkBro", "Gemini Notebook", "Google Docs", "Google One", "Google Sheets", "Joplin", "Link to Windows", "MEGA", "Miniflutt for Miniflux", "Qmanager", "QR & Barcode Scanner Pro", "Solid Explorer File Manager", "spacedesk - USB Display for PC", "Synology Drive", "EZ WAY 易利委", "Microsoft Authenticator", "MyASUS - Service Center", "Termius - Modern SSH Client", "Windows App"),
            "tools" to listOf("1.1.1.1 + WARP: Safer Internet", "1DM+: Browser & Video Download", "3C All-in-One Toolbox", "3C All-in-One Toolbox Pro key", "8BitDo Ultimate Software", "中華電信", "中華網安助手", "寶島眼鏡", "Aegis Authenticator - 2FA App", "Anker Power", "ASUS Router", "Bluetooth Keyboard & Mouse Pro", "Chameleon Ultra GUI", "Cx File Explorer", "DNSChanger for IPv4/IPv6", "DS finder", "DS get", "DS router", "FloleVac", "Galaxy Fit3 Plugin", "Galaxy Wearable", "Google Authenticator", "Google Family Link", "Google Translate", "IINE", "Keepass2Android Password Safe", "MTools BLE - BLE RFID Reader", "OpenVPN Connect", "Quick Ball", "SD Maid 2/SE - System Cleaner", "Smart Tools 2", "Sony | Sound Connect", "Speedtest by Ookla", "Synology Secure SignIn", "TWDIW", "V380 Pro", "ZArchiver Donate", "AriaNgGUI", "BlueTask", "SamQuickSetting", "SamAppOrganizer", "AI Input Widget", "LocalSend", "Obtainium", "Torrent Search", "LIME", "Quick Settings Tiles", "Blizzard Authenticator", "Beacon Scanner", "Website Shortcut", "findPacket", "seClock", "RustDesk", "Tasker"),
            "news" to listOf("Google News - Daily Headlines", "Medium: Read & Write Stories"),
            "reading" to listOf("輕鬆讀小說", "Amazon Kindle: Reading App", "BooxReader: EPUB Reader & PDF", "CalliPlus Chinese Calligraphy", "Google Play Books & Audiobooks", "HyRead 電子書 - 立即借圖書館小說雜誌影音書", "Kobo | eBooks & Audiobooks", "Librera PRO - Book reader", "Moon+ Reader Pro", "ReadEra Premium - ebook reader", "Readest: EPUB & PDF Reader", "Readmoo 看書", "Reasily - EPUB Reader", "Books OPDS"),
            "comic" to listOf("CDisplayEx Comic Reader", "Perfect Viewer", "Perfect Viewer X", "Renta！亂搭－正版漫畫小說天天更新", "WEBTOON: Manga, Comics, Manhwa", "EHViewer", "SamComic"),
            "health" to listOf("我的健身工廠 My Fitness Factory", "Google Fit: Activity Tracking", "Google Health (Fitbit)", "Health Connect", "Nearby Nirvana", "Samsung Health", "全民健保行動快易通 | 健康存摺", "FreeStyle LibreLink - TW"),
            "home" to listOf("Home Assistant", "Google Home", "Roborock", "SwitchBot", "TP-Link Tapo", "Xiaomi Home", "Yeelight Classic"),
            "life" to listOf("有OPENPOINT真好", "誠品 TW", "Hi-Life VIP", "Invoice Prize Redemption", "Journal", "Nintendo Switch Parental Controls", "surco", "Uber Eats: Food Delivery"),
            "entertainment" to listOf("巴哈姆特動畫瘋", "Battle.net", "Nintendo Switch App", "pixiv", "Steam", "TagMo", "Twitch: Live Streaming"),
            "weather" to listOf("中央氣象署E - 地震測報", "Taiwan Weather", "Weather & Widget - Weawow")
        )

        val DEFAULT_FOLDERS = LEGACY_DEFAULT_FOLDERS.map { folder ->
            folder.copy(keywords = folder.keywords + DEFAULT_APP_NAMES[folder.id].orEmpty())
        } + listOf(
            OrganizerFolder("reading", "閱讀", DEFAULT_APP_NAMES.getValue("reading"), null, false),
            OrganizerFolder("comic", "漫畫", DEFAULT_APP_NAMES.getValue("comic"), null, false),
            OrganizerFolder("health", "健康", DEFAULT_APP_NAMES.getValue("health"), null, false),
            OrganizerFolder("home", "智慧家庭", DEFAULT_APP_NAMES.getValue("home"), null, false),
            OrganizerFolder("life", "生活", DEFAULT_APP_NAMES.getValue("life"), null, false),
            OrganizerFolder("entertainment", "娛樂", DEFAULT_APP_NAMES.getValue("entertainment"), null, false),
            OrganizerFolder("weather", "天氣", DEFAULT_APP_NAMES.getValue("weather"), null, false)
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
