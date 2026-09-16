package tw.idv.samliao.apporganizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.min

fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap.width == sizePx && bitmap.height == sizePx) return bitmap
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

fun folderPreviewBitmap(apps: List<AppItem>, sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = sizePx * 0.18f
    paint.color = Color.rgb(244, 197, 66)
    canvas.drawRoundRect(RectF(0f, sizePx * 0.12f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, paint)
    paint.color = Color.rgb(226, 174, 40)
    canvas.drawRoundRect(RectF(0f, 0f, sizePx * 0.48f, sizePx * 0.32f), radius, radius, paint)

    val iconSize = (sizePx * 0.34f).toInt()
    val padding = (sizePx * 0.15f).toInt()
    apps.take(4).forEachIndexed { index, app ->
        val left = padding + (index % 2) * (iconSize + padding / 2)
        val top = padding + (index / 2) * (iconSize + padding / 2)
        canvas.drawBitmap(app.icon.toBitmap(iconSize), left.toFloat(), top.toFloat(), null)
    }
    if (apps.isEmpty()) {
        paint.color = Color.rgb(47, 111, 237)
        val dot = min(sizePx / 5f, 18f)
        canvas.drawCircle(sizePx * 0.38f, sizePx * 0.58f, dot, paint)
        canvas.drawCircle(sizePx * 0.62f, sizePx * 0.58f, dot, paint)
    }
    return bitmap
}
