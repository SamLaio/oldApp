package tw.idv.samliao.apporganizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.min

object FolderIconStyle {
    const val PREVIEW = "preview"
    const val FOLDER = "folder"
    const val CIRCLE = "circle"

    val values = listOf(PREVIEW, FOLDER, CIRCLE)

    fun label(style: String): String = when (style) {
        FOLDER -> "白色資料夾預覽"
        CIRCLE -> "白色圓形預覽"
        else -> "系統預設（圖示）"
    }
}

fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap.width == sizePx && bitmap.height == sizePx) return bitmap
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

fun folderPreviewBitmap(
    apps: List<AppItem>,
    sizePx: Int,
    style: String = FolderIconStyle.PREVIEW
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (style == FolderIconStyle.CIRCLE) {
        paint.color = Color.WHITE
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.48f, paint)
        paint.color = Color.rgb(218, 220, 224)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, sizePx * 0.03f)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.48f, paint)
        paint.style = Paint.Style.FILL
    } else if (style == FolderIconStyle.FOLDER) {
        val radius = sizePx * 0.18f
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(0f, sizePx * 0.12f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, paint)
        paint.color = Color.rgb(241, 243, 244)
        canvas.drawRoundRect(RectF(0f, 0f, sizePx * 0.48f, sizePx * 0.32f), radius, radius, paint)
        paint.color = Color.rgb(218, 220, 224)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, sizePx * 0.03f)
        canvas.drawRoundRect(RectF(0f, sizePx * 0.12f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    val iconSize = (sizePx * 0.38f).toInt()
    val gap = (sizePx * 0.06f).toInt()
    val start = (sizePx - iconSize * 2 - gap) / 2
    apps.take(4).forEachIndexed { index, app ->
        val left = start + (index % 2) * (iconSize + gap)
        val top = start + (index / 2) * (iconSize + gap)
        canvas.drawBitmap(app.icon.toBitmap(iconSize), left.toFloat(), top.toFloat(), null)
    }
    if (apps.isEmpty()) {
        paint.color = Color.rgb(128, 128, 128)
        val dot = min(sizePx / 5f, 18f)
        canvas.drawCircle(sizePx * 0.38f, sizePx * 0.58f, dot, paint)
        canvas.drawCircle(sizePx * 0.62f, sizePx * 0.58f, dot, paint)
    }
    return bitmap
}
