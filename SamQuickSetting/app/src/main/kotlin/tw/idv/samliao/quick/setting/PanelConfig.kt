package tw.idv.samliao.quick.setting

import android.content.Context
import android.content.SharedPreferences

object PanelConfig {
    const val PREFIX_SETTING = "setting:"
    const val PREFIX_APP = "app:"
    private const val PREFS = "panel_config"
    private const val KEY_ACTIVE_PANEL = "active_panel"
    private const val KEY_PANEL_COUNT = "panel_count"
    private const val KEY_ITEMS = "items"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_GRID = "grid"
    private const val KEY_LOCKED = "locked"
    private const val KEY_X = "_x"
    private const val KEY_Y = "_y"

    val systemSettings = listOf(
        Setting("brightness", R.string.action_brightness),
        Setting("volume", R.string.action_volume),
        Setting("ringer", R.string.action_ringer),
        Setting("timeout", R.string.action_timeout),
        Setting("rotation", R.string.action_rotation),
        Setting("sync", R.string.action_sync),
        Setting("wifi", R.string.open_wifi_settings),
        Setting("bluetooth", R.string.open_bluetooth_settings),
        Setting("location", R.string.open_location_settings),
        Setting("hotspot", R.string.open_hotspot_settings),
        Setting("nfc", R.string.open_nfc_settings)
    )

    val indicators = listOf(
        Setting("storage", R.string.action_storage),
        Setting("battery", R.string.action_battery)
    )

    val tools = listOf(
        Setting("torch", R.string.action_torch)
    )

    val settings = systemSettings + indicators + tools

    val backgrounds = listOf(
        Background("carbon_black", R.string.background_carbon_black, 0xFF263238.toInt(), R.drawable.bg_quick_carbon_black),
        Background("carbon_gray", R.string.background_carbon_gray, 0xFF37474F.toInt(), R.drawable.bg_quick_carbon_gray),
        Background("carbon_blue", R.string.background_carbon_blue, 0xFF006064.toInt(), R.drawable.bg_quick_carbon_blue),
        Background("carbon_violet", R.string.background_carbon_violet, 0xFF4527A0.toInt(), R.drawable.bg_quick_carbon_violet),
        Background("circles_blue", R.string.background_circles_blue, 0xFF006064.toInt(), R.drawable.bg_quick_circles_blue),
        Background("circles_green", R.string.background_circles_green, 0xFF1B5E20.toInt(), R.drawable.bg_quick_circles_green)
    )

    val grids = listOf(
        Grid("3x4", R.string.grid_3x4, 3, 4),
        Grid("4x5", R.string.grid_4x5, 4, 5),
        Grid("5x6", R.string.grid_5x6, 5, 6)
    )

    fun activePanel(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(KEY_ACTIVE_PANEL, 1).coerceIn(1, panelCount(context))
    }

    fun panelCount(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(KEY_PANEL_COUNT, 1).coerceAtLeast(1)
    }

    fun items(context: Context): List<String> {
        val prefs = prefs(context)
        return prefs.getString(panelKey(activePanel(context), KEY_ITEMS), "")
            .orEmpty()
            .split("\n")
            .filter { it.startsWith(PREFIX_SETTING) || it.startsWith(PREFIX_APP) }
    }

    fun addSetting(context: Context, id: String) = save(context, items(context) + "$PREFIX_SETTING$id")

    fun addApp(context: Context, packageName: String) = save(context, items(context) + "$PREFIX_APP$packageName")

    fun canAddSetting(context: Context, id: String): Boolean {
        val grid = grid(context)
        return usedCellCount(context, grid) + itemCellCount("$PREFIX_SETTING$id", grid) <= capacity(grid)
    }

    fun canAddApp(context: Context): Boolean {
        val grid = grid(context)
        return usedCellCount(context, grid) + 1 <= capacity(grid)
    }

    fun canUseGrid(context: Context, grid: Grid): Boolean = usedCellCount(context, grid) <= capacity(grid)

    fun clear(context: Context) = save(context, emptyList())

    fun removeAt(context: Context, index: Int) {
        save(context, items(context).filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun background(context: Context): Background {
        val prefs = prefs(context)
        val id = prefs.getString(panelKey(activePanel(context), KEY_BACKGROUND), backgrounds.first().id)
        return backgrounds.firstOrNull { it.id == id } ?: backgrounds.first()
    }

    fun setBackground(context: Context, id: String) {
        prefs(context)
            .edit()
            .putString(panelKey(activePanel(context), KEY_BACKGROUND), id)
            .apply()
    }

    fun grid(context: Context): Grid {
        val id = prefs(context)
            .getString(KEY_GRID, grids.first().id)
        return grids.firstOrNull { it.id == id } ?: grids.first()
    }

    fun setGrid(context: Context, id: String) {
        prefs(context)
            .edit()
            .putString(KEY_GRID, id)
            .apply()
    }

    fun isLocked(context: Context): Boolean =
        prefs(context)
            .getBoolean(KEY_LOCKED, false)

    fun setLocked(context: Context, locked: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_LOCKED, locked)
            .apply()
    }

    fun position(context: Context, key: String): Position? {
        val prefs = prefs(context)
        val panelKey = panelKey(activePanel(context), key)
        if (!prefs.contains(panelKey + KEY_X) || !prefs.contains(panelKey + KEY_Y)) return null
        return Position(prefs.getInt(panelKey + KEY_X, 0), prefs.getInt(panelKey + KEY_Y, 0))
    }

    fun savePosition(context: Context, key: String, x: Int, y: Int) {
        val panelKey = panelKey(activePanel(context), key)
        prefs(context)
            .edit()
            .putInt(panelKey + KEY_X, x)
            .putInt(panelKey + KEY_Y, y)
            .apply()
    }

    fun settingLabel(context: Context, id: String): String =
        settings.firstOrNull { it.id == id }?.let { context.getString(it.labelRes) } ?: id

    fun appLabel(context: Context, packageName: String): String =
        runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(context.packageManager).toString()
        }.getOrElse { packageName }

    private fun save(context: Context, items: List<String>) {
        prefs(context)
            .edit()
            .putString(panelKey(activePanel(context), KEY_ITEMS), items.joinToString("\n"))
            .apply()
    }

    data class Setting(val id: String, val labelRes: Int)

    data class Background(
        val id: String,
        val labelRes: Int,
        val panelColor: Int,
        val drawableRes: Int
    )

    data class Grid(val id: String, val labelRes: Int, val columns: Int, val rows: Int)

    data class Position(val x: Int, val y: Int)

    private fun prefs(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrateToPanels(prefs)
        return prefs
    }

    private fun migrateToPanels(prefs: SharedPreferences) {
        if (prefs.contains(KEY_PANEL_COUNT)) return

        val editor = prefs.edit()
            .putInt(KEY_PANEL_COUNT, 1)
            .putInt(KEY_ACTIVE_PANEL, 1)

        prefs.getString(KEY_ITEMS, null)?.let {
            editor.putString(panelKey(1, KEY_ITEMS), it)
            editor.remove(KEY_ITEMS)
        }
        prefs.getString(KEY_BACKGROUND, null)?.let {
            editor.putString(panelKey(1, KEY_BACKGROUND), it)
            editor.remove(KEY_BACKGROUND)
        }

        prefs.all.forEach { (key, value) ->
            if ((key.endsWith(KEY_X) || key.endsWith(KEY_Y)) && value is Int) {
                editor.putInt(panelKey(1, key), value)
                editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun panelKey(panel: Int, key: String): String = "panel_${panel}_$key"

    private fun capacity(grid: Grid): Int = grid.columns * grid.rows

    private fun usedCellCount(context: Context, grid: Grid): Int =
        items(context).sumOf { itemCellCount(it, grid) }

    private fun itemCellCount(item: String, grid: Grid): Int {
        val id = item.removePrefix(PREFIX_SETTING)
        return if (item.startsWith(PREFIX_SETTING) && (id == "brightness" || id == "volume")) {
            grid.columns
        } else {
            1
        }
    }
}
