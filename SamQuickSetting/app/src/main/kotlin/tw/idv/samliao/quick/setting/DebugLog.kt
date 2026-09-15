package tw.idv.samliao.quick.setting

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val PREFS = "debug_log"
    private const val KEY_ENABLED = "enabled"
    private const val FILE_NAME = "quick_log.txt"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun write(context: Context, message: String) {
        if (!isEnabled(context)) return
        runCatching {
            val line = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())} $message\n"
            val resolver = context.contentResolver
            val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existing = resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(FILE_NAME),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    android.content.ContentUris.withAppendedId(uri, cursor.getLong(0))
                } else {
                    null
                }
            }
            val target = existing ?: resolver.insert(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }) ?: return
            resolver.openOutputStream(target, "wa")?.use { it.write(line.toByteArray()) }
        }
    }
}
