package com.samliao.findpacket

import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var screenRoot: FrameLayout
    private lateinit var scrollView: ScrollView
    private lateinit var root: LinearLayout
    private lateinit var numberInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var carrierSpinner: Spinner
    private lateinit var recentList: LinearLayout
    private lateinit var recentStore: RecentStore
    private lateinit var deleteButton: Button
    private lateinit var cancelButton: Button
    private lateinit var modeButton: TextView
    private var nightMode = false
    private val selectedRecentIds = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recentStore = RecentStore(this)
        nightMode = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_NIGHT_MODE, false)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(144), dp(32), dp(32))
        }

        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL

            addView(TextView(this@MainActivity).apply {
                setText(R.string.app_name)
                textSize = 28f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            deleteButton = Button(this@MainActivity).apply {
                isAllCaps = false
                setText(R.string.delete)
                visibility = View.GONE
                setOnClickListener { deleteSelectedRecents() }
            }
            addView(deleteButton)

            cancelButton = Button(this@MainActivity).apply {
                isAllCaps = false
                setText(R.string.cancel)
                visibility = View.GONE
                setOnClickListener { clearRecentSelection() }
            }
            addView(cancelButton)
        })

        numberInput = EditText(this).apply {
            setHint(R.string.tracking_number_hint)
            setSingleLine(true)
        }
        root.addView(numberInput)

        noteInput = EditText(this).apply {
            setHint(R.string.package_note_hint)
            setSingleLine(true)
        }
        root.addView(noteInput)

        carrierSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Carriers.all)
        }
        root.addView(carrierSpinner)

        root.addView(Button(this).apply {
            isAllCaps = false
            setText(R.string.open_webview)
            setOnClickListener { openSelected(external = false) }
        })

        root.addView(Button(this).apply {
            isAllCaps = false
            setText(R.string.open_browser)
            setOnClickListener { openSelected(external = true) }
        })

        recentList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(recentList)

        migrateLegacyRecents()
        restoreRecent()
        modeButton = TextView(this).apply {
            gravity = Gravity.CENTER
            setOnClickListener { toggleNightMode() }
        }
        screenRoot = FrameLayout(this).apply {
            scrollView = ScrollView(this@MainActivity).apply { addView(root) }
            addView(scrollView)
            addView(modeButton, FrameLayout.LayoutParams(
                dp(48),
                dp(48),
                Gravity.BOTTOM or Gravity.START,
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) })
        }
        setContentView(screenRoot)
        applyNightMode()
        handleSharedText(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    private fun openSelected(external: Boolean) {
        val carrier = carrierSpinner.selectedItem as Carrier
        val number = numberInput.text.toString().trim()
        val note = noteInput.text.toString().trim()
        if (number.isNotEmpty()) {
            copyNumber(number)
        }
        saveRecent(number, carrierSpinner.selectedItemPosition, note)

        if (external) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(carrier.url)))
        } else {
            startActivity(Intent(this, WebActivity::class.java).apply {
                putExtra(WebActivity.EXTRA_TITLE, carrier.name)
                putExtra(WebActivity.EXTRA_URL, carrier.url)
                putExtra(WebActivity.EXTRA_NUMBER, number)
                putExtra(WebActivity.EXTRA_AUTOFILL_SELECTOR, carrier.autofillSelector)
            })
        }
    }

    private fun copyNumber(number: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText(getString(R.string.tracking_number), number))
        Toast.makeText(this, R.string.number_copied, Toast.LENGTH_SHORT).show()
    }

    private fun saveRecent(number: String, carrierIndex: Int, note: String = "") {
        if (number.isBlank()) return

        val now = System.currentTimeMillis()
        val safeCarrierIndex = carrierIndex.coerceIn(Carriers.all.indices)
        val query = RecentQuery(number, safeCarrierIndex, Carriers.all[safeCarrierIndex].name, note, now)
        recentStore.save(query)
        renderRecentQueries(recentStore.readAll())
    }

    private fun restoreRecent(showToast: Boolean = false) {
        val recents = recentStore.readAll()
        renderRecentQueries(recents)
        recents.firstOrNull()?.let { recent ->
            applyRecentQuery(recent)
            if (showToast) {
                Toast.makeText(this, R.string.recent_query_restored, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyRecentQuery(query: RecentQuery) {
        val number = query.number
        carrierSpinner.setSelection(query.carrierIndex)
        numberInput.setText(number)
        numberInput.setSelection(number.length)
        noteInput.setText(query.note)
        noteInput.setSelection(query.note.length)
    }

    private fun renderRecentQueries(recents: List<RecentQuery>) {
        recentList.removeAllViews()
        recents.forEach { recent ->
            recentList.addView(TextView(this).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                tag = recent.id
                setBackgroundColor(if (recent.id in selectedRecentIds) Color.LTGRAY else Color.TRANSPARENT)
                text = if (recent.note.isBlank()) {
                    getString(R.string.recent_query, Carriers.all[recent.carrierIndex].name, recent.number)
                } else {
                    getString(R.string.recent_query_with_note, recent.note, Carriers.all[recent.carrierIndex].name, recent.number)
                }
                setOnClickListener {
                    if (selectedRecentIds.isEmpty()) {
                        applyRecentQuery(recent)
                        Toast.makeText(this@MainActivity, R.string.recent_query_restored, Toast.LENGTH_SHORT).show()
                    } else {
                        toggleRecentSelection(recent)
                    }
                }
                setOnLongClickListener {
                    toggleRecentSelection(recent)
                    true
                }
            })
        }
        applyNightMode()
    }

    private fun toggleNightMode() {
        nightMode = !nightMode
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_NIGHT_MODE, nightMode).apply()
        applyNightMode()
    }

    private fun applyNightMode() {
        if (!::modeButton.isInitialized) return
        val background = if (nightMode) Color.BLACK else Color.WHITE
        val foreground = if (nightMode) Color.WHITE else Color.BLACK
        val selected = if (nightMode) Color.DKGRAY else Color.LTGRAY
        screenRoot.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        root.setBackgroundColor(background)
        modeButton.contentDescription = getString(if (nightMode) R.string.day_mode else R.string.night_mode)
        modeButton.text = getString(if (nightMode) R.string.day_mode_icon else R.string.night_mode_icon)
        modeButton.textSize = 24f
        modeButton.setTextColor(if (nightMode) Color.BLACK else Color.WHITE)
        modeButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (nightMode) Color.WHITE else Color.BLACK)
            setStroke(dp(2), if (nightMode) Color.LTGRAY else Color.DKGRAY)
        }
        window.statusBarColor = background
        window.navigationBarColor = background
        applyColors(root, foreground, selected)
    }

    private fun applyColors(view: View, foreground: Int, selected: Int) {
        if (view is TextView && view !is Button) {
            view.setTextColor(foreground)
            if (view is EditText) view.setHintTextColor(if (nightMode) Color.LTGRAY else Color.DKGRAY)
        }
        val recentId = view.tag as? Long
        if (view.parent == recentList && recentId != null && recentId in selectedRecentIds) {
            view.setBackgroundColor(selected)
        } else if (view.parent == recentList) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        if (view is LinearLayout) {
            for (index in 0 until view.childCount) {
                applyColors(view.getChildAt(index), foreground, selected)
            }
        }
    }

    private fun toggleRecentSelection(recent: RecentQuery) {
        if (!selectedRecentIds.add(recent.id)) {
            selectedRecentIds.remove(recent.id)
        }
        updateSelectionButtons()
        renderRecentQueries(recentStore.readAll())
    }

    private fun clearRecentSelection() {
        selectedRecentIds.clear()
        updateSelectionButtons()
        renderRecentQueries(recentStore.readAll())
    }

    private fun deleteSelectedRecents() {
        recentStore.delete(selectedRecentIds)
        selectedRecentIds.clear()
        updateSelectionButtons()
        renderRecentQueries(recentStore.readAll())
    }

    private fun updateSelectionButtons() {
        val visibility = if (selectedRecentIds.isEmpty()) View.GONE else View.VISIBLE
        deleteButton.visibility = visibility
        cancelButton.visibility = visibility
    }

    private fun migrateLegacyRecents() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val json = prefs.getString(KEY_RECENTS, null)
        val recents = if (json == null) {
            val number = prefs.getString(KEY_NUMBER, "").orEmpty()
            if (number.isBlank()) {
                emptyList()
            } else {
                val carrierIndex = prefs.getInt(KEY_CARRIER, 0).coerceIn(Carriers.all.indices)
                listOf(RecentQuery(number, carrierIndex, Carriers.all[carrierIndex].name, "", now))
            }
        } else {
            runCatching<List<RecentQuery>> {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val number = item.optString(KEY_NUMBER).trim()
                    val updatedAt = item.optLong(KEY_UPDATED_AT)
                    if (number.isBlank()) return@mapNotNull null
                    val carrierName = item.optString(KEY_CARRIER_NAME)
                    val carrierIndex = Carriers.all.indexOfFirst { it.name == carrierName }
                        .takeIf { it >= 0 }
                        ?: item.optInt(KEY_CARRIER).coerceIn(Carriers.all.indices)
                    RecentQuery(number, carrierIndex, Carriers.all[carrierIndex].name, item.optString(KEY_NOTE).trim(), updatedAt)
                }
            }.getOrDefault(emptyList())
        }
        recents.forEach(recentStore::save)
        prefs
            .edit()
            .remove(KEY_RECENTS)
            .remove(KEY_NUMBER)
            .remove(KEY_CARRIER)
            .apply()
    }

    private fun handleSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) {
            return
        }

        val sharedText = listOfNotNull(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
        ).joinToString(" ")
        val number = extractTrackingNumber(sharedText) ?: return

        numberInput.setText(number)
        numberInput.setSelection(number.length)
        noteInput.setText("")
        saveRecent(number, carrierSpinner.selectedItemPosition)
        Toast.makeText(this, R.string.shared_number_filled, Toast.LENGTH_SHORT).show()
    }

    private fun extractTrackingNumber(text: String): String? {
        return Regex("[A-Za-z0-9][A-Za-z0-9 -]{5,39}")
            .findAll(text)
            .map { match -> match.value to match.value.replace(Regex("[^A-Za-z0-9]"), "") }
            .filter { (raw, value) ->
                !raw.contains("http", ignoreCase = true) &&
                    !raw.contains("www", ignoreCase = true) &&
                    value.length in 8..30 &&
                    value.count(Char::isDigit) >= 5
            }
            .maxByOrNull { (_, value) -> value.length }
            ?.second
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class RecentQuery(
        val number: String,
        val carrierIndex: Int,
        val carrierName: String,
        val note: String,
        val updatedAt: Long,
        val id: Long = 0,
    )

    private class RecentStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE_RECENTS (" +
                    "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$KEY_NUMBER TEXT NOT NULL, " +
                    "$KEY_CARRIER_NAME TEXT NOT NULL, " +
                    "$KEY_NOTE TEXT NOT NULL, " +
                    "$KEY_UPDATED_AT INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX recents_unique ON $TABLE_RECENTS($KEY_CARRIER_NAME, $KEY_NUMBER, $KEY_NOTE)")
            db.execSQL("CREATE INDEX recents_updated_at ON $TABLE_RECENTS($KEY_UPDATED_AT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_RECENTS")
            onCreate(db)
        }

        fun save(query: RecentQuery) {
            val db = writableDatabase
            deleteOld(db)
            db.insertWithOnConflict(TABLE_RECENTS, null, ContentValues().apply {
                put(KEY_NUMBER, query.number)
                put(KEY_CARRIER_NAME, query.carrierName)
                put(KEY_NOTE, query.note)
                put(KEY_UPDATED_AT, query.updatedAt)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }

        fun readAll(): List<RecentQuery> {
            val db = writableDatabase
            deleteOld(db)
            db.query(
                TABLE_RECENTS,
                arrayOf(COL_ID, KEY_NUMBER, KEY_CARRIER_NAME, KEY_NOTE, KEY_UPDATED_AT),
                "$KEY_UPDATED_AT >= ?",
                arrayOf((System.currentTimeMillis() - RECENT_MAX_AGE_MS).toString()),
                null,
                null,
                "$KEY_UPDATED_AT DESC",
            ).use { cursor ->
                val recents = mutableListOf<RecentQuery>()
                while (cursor.moveToNext()) {
                    val carrierName = cursor.getString(2)
                    val carrierIndex = Carriers.all.indexOfFirst { it.name == carrierName }
                    if (carrierIndex >= 0) {
                        recents += RecentQuery(
                            number = cursor.getString(1),
                            carrierIndex = carrierIndex,
                            carrierName = carrierName,
                            note = cursor.getString(3),
                            updatedAt = cursor.getLong(4),
                            id = cursor.getLong(0),
                        )
                    }
                }
                return recents
            }
        }

        fun delete(ids: Set<Long>) {
            val db = writableDatabase
            ids.forEach { id ->
                db.delete(TABLE_RECENTS, "$COL_ID = ?", arrayOf(id.toString()))
            }
        }

        private fun deleteOld(db: SQLiteDatabase) {
            db.delete(TABLE_RECENTS, "$KEY_UPDATED_AT < ?", arrayOf((System.currentTimeMillis() - RECENT_MAX_AGE_MS).toString()))
        }
    }

    private companion object {
        const val PREFS = "find_packet"
        const val DB_NAME = "find_packet.db"
        const val DB_VERSION = 1
        const val TABLE_RECENTS = "recents"
        const val COL_ID = "_id"
        const val KEY_RECENTS = "recents"
        const val KEY_NUMBER = "number"
        const val KEY_CARRIER = "carrier"
        const val KEY_CARRIER_NAME = "carrier_name"
        const val KEY_NOTE = "note"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_NIGHT_MODE = "night_mode"
        const val RECENT_MAX_AGE_MS = 365L * 24 * 60 * 60 * 1000
    }
}
