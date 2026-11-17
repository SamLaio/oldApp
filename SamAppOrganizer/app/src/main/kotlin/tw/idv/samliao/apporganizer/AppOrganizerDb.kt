package tw.idv.samliao.apporganizer

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppOrganizerDb(context: Context) : SQLiteOpenHelper(context, "app_organizer.db", null, 13) {
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
        createFolderIndexTable(db)
        DEFAULT_FOLDERS.forEach { folder -> saveFolder(db, folder.id, folder.name, folder.keywords, false, folder.category) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createWidgetTable(db)
        if (oldVersion < 4) createSettingsTable(db)
        if (oldVersion < 5) createManualUncategorizedTable(db)
        if (oldVersion < 9) mergeDefaultFolders(db)
        if (oldVersion < 10) {
            createFolderIndexTable(db)
            db.execSQL("INSERT OR REPLACE INTO folder_index(package_name, folder_id) SELECT package_name, folder_id FROM assignments")
            db.execSQL("INSERT OR REPLACE INTO folder_index(package_name, folder_id) SELECT package_name, '$UNCATEGORIZED' FROM manual_uncategorized")
        }
        if (oldVersion < 11) mergeDefaultFolders(db)
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE folder_index ADD COLUMN weight INTEGER NOT NULL DEFAULT 20")
            db.execSQL("UPDATE folder_index SET weight = 90 WHERE package_name IN (SELECT package_name FROM assignments)")
            db.execSQL("UPDATE folder_index SET weight = 90 WHERE package_name IN (SELECT package_name FROM manual_uncategorized)")
        }
        if (oldVersion < 13) mergeDefaultFolders(db)
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

    fun importAssignments(assignments: Map<String, String>): Int {
        val values = assignments.map { it.key.trim() to it.value.trim() }
        validateFolderIndex(values)
        writableDatabase.beginTransaction()
        try {
            replaceFolderIndex(writableDatabase, values.associate { (packageName, folderId) ->
                packageName to FolderIndexEntry(folderId, 90)
            })
            writableDatabase.setTransactionSuccessful()
            return values.size
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun deleteFolder(id: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("assignments", "folder_id = ?", arrayOf(id))
            writableDatabase.delete("folder_index", "folder_id = ?", arrayOf(id))
            writableDatabase.delete("widget_folders", "folder_id = ?", arrayOf(id))
            writableDatabase.delete("keywords", "folder_id = ?", arrayOf(id))
            writableDatabase.delete("folders", "id = ? AND custom = 1", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun folderIndex(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        readableDatabase.rawQuery("SELECT package_name, folder_id FROM folder_index", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1)
        }
        return result
    }

    fun folderIndexEntries(): Map<String, FolderIndexEntry> {
        val result = mutableMapOf<String, FolderIndexEntry>()
        readableDatabase.rawQuery("SELECT package_name, folder_id, weight FROM folder_index", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(0)] = FolderIndexEntry(cursor.getString(1), cursor.getInt(2))
            }
        }
        return result
    }

    fun indexedPackages(folderId: String): List<String> =
        readableDatabase.rawQuery(
            "SELECT package_name FROM folder_index WHERE folder_id = ? ORDER BY package_name",
            arrayOf(folderId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    fun isFolderIndexReady(): Boolean = setting(FOLDER_INDEX_READY) == "1"

    fun replaceFolderIndex(index: Map<String, FolderIndexEntry>) {
        val values = index.map { it.key.trim() to it.value.folderId.trim() }
        validateFolderIndex(values)
        writableDatabase.beginTransaction()
        try {
            replaceFolderIndex(writableDatabase, index)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun updateFolderIndex(packageName: String, folderId: String, weight: Int = 90) {
        require(packageName.isNotBlank()) { "app package 不可空白" }
        require(folderId == UNCATEGORIZED || folders().any { it.id == folderId }) {
            "$packageName 指定了不存在的資料夾：$folderId"
        }
        writableDatabase.insertWithOnConflict(
            "folder_index",
            null,
            ContentValues().apply {
                put("package_name", packageName)
                put("folder_id", folderId)
                put("weight", weight)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        setSetting(FOLDER_INDEX_READY, "1")
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
        return setting(FOLDER_ICON_STYLE) ?: FolderIconStyle.PREVIEW
    }

    fun setIconStyle(style: String) {
        setSetting(FOLDER_ICON_STYLE, style)
    }

    private fun setting(key: String): String? =
        readableDatabase.rawQuery(
            "SELECT value FROM settings WHERE key = ?",
            arrayOf(key)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun setSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "settings",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
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

    private fun createFolderIndexTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS folder_index (package_name TEXT PRIMARY KEY, folder_id TEXT NOT NULL, weight INTEGER NOT NULL)"
        )
    }

    private fun validateFolderIndex(values: List<Pair<String, String>>) {
        val folderIds = folders().map { it.id }.toSet() + UNCATEGORIZED
        values.forEach { (packageName, folderId) ->
            require(packageName.isNotEmpty()) { "app package 不可空白" }
            require(folderId in folderIds) { "$packageName 指定了不存在的資料夾：$folderId" }
        }
    }

    private fun replaceFolderIndex(db: SQLiteDatabase, values: Map<String, FolderIndexEntry>) {
        db.delete("folder_index", null, null)
        values.forEach { (packageName, entry) ->
            db.insertOrThrow(
                "folder_index",
                null,
                ContentValues().apply {
                    put("package_name", packageName)
                    put("folder_id", entry.folderId)
                    put("weight", entry.weight)
                }
            )
        }
        db.insertWithOnConflict(
            "settings",
            null,
            ContentValues().apply {
                put("key", FOLDER_INDEX_READY)
                put("value", "1")
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
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
            defaultAppNames(folder.id).filterNot { it in storedKeywords }.forEach { keyword ->
                db.insertOrThrow("keywords", null, ContentValues().apply {
                    put("folder_id", folder.id)
                    put("keyword", keyword)
                })
            }
        }
    }

    private companion object {
        const val FOLDER_ICON_STYLE = "folder_icon_style"
        const val FOLDER_INDEX_READY = "folder_index_ready"
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
            "communication" to listOf("Carrier Services", "Discord - Talk, Play, Hang Out", "Firefox Fast & Private Browser", "FolderSync Pro", "Google Chat", "Google Pixel Watch", "LINE: Calls & Messages", "Messenger", "Samsung Accessory Service", "Telegram", "Thunderbird: Free Your Inbox", "Google Meet", "電話"),
            "social" to listOf("巴哈姆特 - 華人最大電玩動漫社群網站", "Facebook", "Gleezy Pro", "Instagram", "Message Peeping Tom", "Mobile01", "Threads", "X"),
            "video" to listOf("GoPro Quik: Video Editor", "VLC for Android", "MX Player Pro"),
            "music" to listOf("AntennaPod - Podcast Player", "foobar2000", "Google Pixel Buds", "Marshall", "Neutron Music Player", "XROUND MyTune"),
            "photo" to listOf("Gallery", "PhotoScan by Google Photos", "Snapseed", "Synology Photos", "Microsoft Lens", "畫廊", "Pixon"),
            "game" to listOf("Sudokuplus.net -classic sudoku", "Water? 2"),
            "maps" to listOf("55688 Taiwan Taxi", "WeMo - 24H Scooter Sharing"),
            "finance" to listOf("【富貴角10號】sk88新光證券", "台新銀行 「行動銀行」", "國泰世華 行動銀行", "發票存摺：電子發票兌獎、載具歸戶神器", "街口支付", "集保e手掌握(集保e存摺/手機存摺)", "新光銀行", "AndroMoney ( Expense Track )", "Binance: Buy Bitcoin & Crypto", "Easy Wallet 悠遊付", "Fubon+ Mobile Banking", "Google Wallet", "Kraken Pro: Advanced Trading", "MAX Exchange - Buy Bitcoin", "新光人壽", "新光行動保鏢"),
            "shopping" to listOf("健身工廠商城", "蝦皮購物 | 花得更少買得更好", "露天市集", "Amazon Shopping", "AppSales", "momo購物 | 值得信賴的購物首選", "PChome24h購物 | 每天一起變更好", "Rakuten樂天市場購物網", "Yahoo拍賣", "Pinkoi", "拍賣", "生活市集", "寵物公園"),
            "work" to listOf("網紅小遙", "AutoApps", "Calendar Widget (key)", "Calendar Widget Month + Agenda", "ChatGPT", "EinkBro", "Gemini Notebook", "Google Docs", "Google One", "Google Sheets", "Joplin", "Link to Windows", "MEGA", "Miniflutt for Miniflux", "Qmanager", "QR & Barcode Scanner Pro", "Solid Explorer File Manager", "spacedesk - USB Display for PC", "Synology Drive", "EZ WAY 易利委", "Microsoft Authenticator", "MyASUS - Service Center", "Termius - Modern SSH Client", "Windows App"),
            "tools" to listOf("1.1.1.1 + WARP: Safer Internet", "1DM+: Browser & Video Download", "1DM+", "3C All-in-One Toolbox", "3C All-in-One Toolbox Pro key", "8BitDo Ultimate Software", "中華電信", "中華網安助手", "寶島眼鏡", "Aegis Authenticator - 2FA App", "Anker Power", "ASUS Router", "Bluetooth Keyboard & Mouse Pro", "Chameleon Ultra GUI", "Chameleon", "Cx File Explorer", "CX檔案總管", "DNSChanger for IPv4/IPv6", "DS finder", "DS get", "DS router", "DS file", "Qfile Pro", "FloleVac", "Galaxy Fit3 Plugin", "Galaxy Wearable", "Google Authenticator", "Google Family Link", "Google Translate", "IINE", "Keepass2Android Password Safe", "MTools BLE - BLE RFID Reader", "OpenVPN Connect", "Quick Ball", "QuickShortcutMaker", "Sauce Finder", "Signal Strength", "愛字體", "萊姆輸入法", "藍光過濾", "設備訊息", "驗證器", "PrinterShare", "Net Signal Pro", "SD Maid 2/SE - System Cleaner", "Smart Tools 2", "Sony | Sound Connect", "Speedtest by Ookla", "Synology Secure SignIn", "TWDIW", "V380 Pro", "ZArchiver Donate", "AriaNgGUI", "BlueTask", "SamQuickSetting", "SamAppOrganizer", "AI Input Widget", "LocalSend", "Obtainium", "Torrent Search", "LIME", "Quick Settings Tiles", "Blizzard Authenticator", "Beacon Scanner", "Website Shortcut", "findPacket", "seClock", "RustDesk", "Tasker", "App Cloner", "App Cloner Premium & Add-ons", "APP分享器"),
            "news" to listOf("Google News - Daily Headlines", "Medium: Read & Write Stories"),
            "reading" to listOf("輕鬆讀小說", "Amazon Kindle: Reading App", "BooxReader: EPUB Reader & PDF", "CalliPlus Chinese Calligraphy", "Google Play Books & Audiobooks", "HyRead 電子書 - 立即借圖書館小說雜誌影音書", "Kobo | eBooks & Audiobooks", "Librera PRO - Book reader", "Moon+ Reader Pro", "ReadEra Premium - ebook reader", "Readest: EPUB & PDF Reader", "Readmoo 看書", "Reasily - EPUB Reader", "Books OPDS", "Calibre Web Companion", "QDict", "古典詩詞精選", "王羲之書法字典", "閱星曈", "閱讀", "Z-Lib", "WikiReader", "momoBOOK", "Memorize", "誠品TW"),
            "comic" to listOf("CDisplayEx Comic Reader", "CDisplayEx", "Perfect Viewer", "Perfect Viewer X", "Renta！亂搭－正版漫畫小說天天更新", "WEBTOON: Manga, Comics, Manhwa", "EHViewer", "SamComic", "CCC追漫台", "拷貝漫畫", "漫画人", "サンデーうぇぶり"),
            "health" to listOf("我的健身工廠 My Fitness Factory", "Google Fit: Activity Tracking", "Fit", "Google Health (Fitbit)", "Health", "Health Connect", "Nearby Nirvana", "舒壓地圖", "Samsung Health", "全民健保行動快易通 | 健康存摺", "健保快易通", "FreeStyle LibreLink - TW", "Zepp Life", "MedTimer"),
            "home" to listOf("Home Assistant", "Google Home", "Roborock", "SwitchBot", "TP-Link Tapo", "Xiaomi Home", "Yeelight Classic", "Yeelight", "eWeLink"),
            "life" to listOf("有OPENPOINT真好", "誠品 TW", "Hi-Life VIP", "Invoice Prize Redemption", "Journal", "Nintendo Switch Parental Controls", "surco", "Uber Eats: Food Delivery", "Pinterest", "麥當勞歡樂送"),
            "entertainment" to listOf("巴哈姆特動畫瘋", "Battle.net", "Nintendo Switch App", "pixiv", "Steam", "TagMo", "Twitch: Live Streaming"),
            "weather" to listOf("中央氣象署E - 地震測報", "Taiwan Weather", "Weather & Widget - Weawow")
        )

        val PIXEL_APP_ALIASES = mapOf(
            "communication" to listOf("Thunderbird", "Watch", "dialer"),
            "social" to listOf("巴哈姆特", "已讀不回"),
            "finance" to listOf("fubon", "taishinbank", "cathaybk", "invoice", "wallet"),
            "work" to listOf("連結至 Windows"),
            "tools" to listOf("CU GUI", "songpal", "良值"),
            "reading" to listOf("Kindle", "Readest"),
            "comic" to listOf("Komikku", "Renta！亂搭", "WEBTOON"),
            "health" to listOf("freestyle", "健身工廠"),
            "home" to listOf("Tapo", "米家"),
            "life" to listOf("OPENPOINT", "日記"),
            "weather" to listOf("Weawow")
        )

        fun defaultAppNames(folderId: String): List<String> =
            DEFAULT_APP_NAMES[folderId].orEmpty() + PIXEL_APP_ALIASES[folderId].orEmpty()

        val DEFAULT_FOLDERS = LEGACY_DEFAULT_FOLDERS.map { folder ->
            folder.copy(keywords = folder.keywords + defaultAppNames(folder.id))
        } + listOf(
            OrganizerFolder("reading", "閱讀", defaultAppNames("reading"), null, false),
            OrganizerFolder("comic", "漫畫", defaultAppNames("comic"), null, false),
            OrganizerFolder("health", "健康", defaultAppNames("health"), null, false),
            OrganizerFolder("home", "智慧家庭", defaultAppNames("home"), null, false),
            OrganizerFolder("life", "生活", defaultAppNames("life"), null, false),
            OrganizerFolder("entertainment", "娛樂", defaultAppNames("entertainment"), null, false),
            OrganizerFolder("weather", "天氣", defaultAppNames("weather"), null, false)
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

data class FolderIndexEntry(val folderId: String, val weight: Int)
