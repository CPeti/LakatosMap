package com.pczumbel.navigator.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.pczumbel.navigator.R
import kotlin.math.max

/** An icon plus the horizontal anchor that keeps the *symbol* over the cave, not the label. */
data class CaveIcon(val drawable: Drawable, val anchorU: Float, val anchorV: Float)

/**
 * Builds cave markers, optionally with the name drawn beside the symbol.
 *
 * osmdroid has no concept of a marker label, so the label is rendered into the icon bitmap
 * itself. The consequence is the anchor: a labelled bitmap is much wider than the symbol, so
 * anchoring at 0.5 would place the *text's* centre on the coordinate and leave the symbol
 * sitting to the west of the actual entrance.
 */
class CaveIconFactory(private val context: Context) {

    private val plain = ContextCompat.getDrawable(context, R.drawable.ic_cave_entrance)!!
    private val highlighted = ContextCompat.getDrawable(context, R.drawable.ic_cave_entrance_selected)!!

    private val density = context.resources.displayMetrics.density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.parseColor("#1B2A20")
    }

    /** White outline so names stay readable over both dark forest and pale limestone. */
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private val selectedTextPaint = Paint(textPaint).apply { color = Color.parseColor("#B3261E") }

    // Labelled bitmaps are expensive to build and there can be dozens on screen; rebuilding
    // them on every pan would stutter.
    private val cache = LruCache<String, CaveIcon>(256)

    private val clusterFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B4332") }
    private val clusterRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val clusterText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val clusterCache = LruCache<String, Drawable>(64)

    /** Numbered disc, growing a little with the count so big clusters read as big at a glance. */
    fun cluster(count: Int): Drawable {
        val text = if (count > 999) "999+" else count.toString()
        clusterCache[text]?.let { return it }

        val radius = density * when {
            count < 10 -> 15f
            count < 100 -> 18f
            else -> 22f
        }
        val size = (radius * 2 + clusterRing.strokeWidth * 2).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        canvas.drawCircle(c, c, radius, clusterFill)
        canvas.drawCircle(c, c, radius, clusterRing)
        val metrics = clusterText.fontMetrics
        canvas.drawText(text, c, c - (metrics.ascent + metrics.descent) / 2f, clusterText)

        val drawable = BitmapDrawable(context.resources, bitmap)
        clusterCache.put(text, drawable)
        return drawable
    }

    private val waypointFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2D7DD2") }
    private val waypointCache = LruCache<String, Drawable>(32)

    /** Route waypoint pin: blue disc with "S", "1", "2"… "E", matching the route line colour. */
    fun waypoint(label: String): Drawable {
        waypointCache[label]?.let { return it }
        val radius = 13f * density
        val size = (radius * 2 + clusterRing.strokeWidth * 2).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        canvas.drawCircle(c, c, radius, waypointFill)
        canvas.drawCircle(c, c, radius, clusterRing)
        val metrics = clusterText.fontMetrics
        canvas.drawText(label, c, c - (metrics.ascent + metrics.descent) / 2f, clusterText)
        val drawable = BitmapDrawable(context.resources, bitmap)
        waypointCache.put(label, drawable)
        return drawable
    }

    private val eventTodo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8590C") }
    private val eventDone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A8A8A") }
    private val eventFocus = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B3261E") }
    private val eventCache = LruCache<String, Drawable>(16)

    /**
     * Event-mode marker. Orange = still to visit, grey with a tick = done, red = the one whose
     * card is open. Caves show the arch, custom locations a plain dot, so you can tell a cave
     * control from, say, a road-junction control at a glance.
     */
    fun eventPoint(isCave: Boolean, visited: Boolean, focused: Boolean): Drawable {
        val key = "$isCave|$visited|$focused"
        eventCache[key]?.let { return it }
        val radius = 15f * density
        val size = (radius * 2 + clusterRing.strokeWidth * 2).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        canvas.drawCircle(c, c, radius, if (focused) eventFocus else if (visited) eventDone else eventTodo)
        canvas.drawCircle(c, c, radius, clusterRing)
        val metrics = clusterText.fontMetrics
        val glyph = when {
            visited -> "✓"
            isCave -> "∩"
            else -> "•"
        }
        canvas.drawText(glyph, c, c - (metrics.ascent + metrics.descent) / 2f, clusterText)
        val drawable = BitmapDrawable(context.resources, bitmap)
        eventCache.put(key, drawable)
        return drawable
    }

    /**
     * Just a name, offset to sit to the right of a marker drawn separately at the same point.
     * Event markers carry visited state in their colour, so their labels can't be baked into
     * the cave icon the way [icon] does it.
     */
    fun labelOnly(label: String): CaveIcon {
        val key = "L|$label"
        cache[key]?.let { return it }
        val markerWidth = 30f * density + clusterRing.strokeWidth * 2
        val gap = 3f * density
        val trimmed = if (label.length > 28) label.take(27) + "…" else label
        val textWidth = textPaint.measureText(trimmed)
        val metrics = textPaint.fontMetrics
        val height = (metrics.descent - metrics.ascent).toInt() + 2
        val width = (markerWidth + gap + textWidth).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(trimmed, markerWidth + gap, baseline, haloPaint)
        canvas.drawText(trimmed, markerWidth + gap, baseline, textPaint)
        val result = CaveIcon(BitmapDrawable(context.resources, bitmap), (markerWidth / 2f) / width, 0.5f)
        cache.put(key, result)
        return result
    }

    fun icon(label: String?, selected: Boolean): CaveIcon {
        if (label.isNullOrBlank()) {
            val drawable = if (selected) highlighted else plain
            return CaveIcon(drawable, 0.5f, 0.5f)
        }

        val key = "${if (selected) "S" else "N"}|$label"
        cache[key]?.let { return it }

        val symbol = (if (selected) highlighted else plain).toBitmap()
        val gap = (3f * density)
        val paint = if (selected) selectedTextPaint else textPaint

        val trimmed = if (label.length > 28) label.take(27) + "…" else label
        val textWidth = paint.measureText(trimmed)
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent

        val width = (symbol.width + gap + textWidth).toInt()
        val height = max(symbol.height.toFloat(), textHeight).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(symbol, 0f, (height - symbol.height) / 2f, null)

        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        val textX = symbol.width + gap
        canvas.drawText(trimmed, textX, baseline, haloPaint)
        canvas.drawText(trimmed, textX, baseline, paint)

        val icon = CaveIcon(
            drawable = BitmapDrawable(context.resources, bitmap),
            anchorU = (symbol.width / 2f) / width,
            anchorV = 0.5f,
        )
        cache.put(key, icon)
        return icon
    }
}
