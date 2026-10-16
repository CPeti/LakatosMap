package com.pczumbel.navigator.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.location.Location
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Position dot with a heading beam, like Google Maps / OsmAnd.
 *
 * osmdroid's own overlay only shows a direction when the GPS fix carries a bearing, i.e. while
 * walking, and nothing standing still — which is exactly when you want to know which way you're
 * facing at a junction. Here the beam follows the compass ([heading], true north), falling back
 * to GPS course-over-ground when there's no compass reading.
 *
 * The base class still draws the accuracy circle; its icons are replaced with a transparent
 * pixel so only our dot and beam appear.
 */
class HeadingLocationOverlay(
    provider: IMyLocationProvider,
    mapView: MapView,
) : MyLocationNewOverlay(provider, mapView) {

    /** Device heading, degrees clockwise from true north; null when unknown. */
    @Volatile
    var heading: Float? = null

    private val density = mapView.context.resources.displayMetrics.density
    private val dotRadius = 8f * density
    private val ringWidth = 3f * density
    private val beamRadius = 58f * density
    private val beamHalfAngle = 32f

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A73E8") }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#40000000") }
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamPath = Path()
    private val beamRect = RectF()

    init {
        val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        setPersonIcon(blank)
        @Suppress("DEPRECATION")
        setDirectionArrow(blank, blank)
    }

    override fun drawMyLocation(canvas: Canvas, pj: Projection, lastFix: Location) {
        // Accuracy circle (and the invisible base icons).
        super.drawMyLocation(canvas, pj, lastFix)

        val p = pj.toPixels(GeoPoint(lastFix.latitude, lastFix.longitude), null)
        val cx = p.x.toFloat()
        val cy = p.y.toFloat()

        val direction = heading ?: lastFix.bearing.takeIf { lastFix.hasBearing() }
        if (direction != null) {
            // The canvas is already in the map's rotated frame, so rotating by the true-north
            // bearing points the beam the right way whatever the map orientation.
            canvas.save()
            canvas.rotate(direction, cx, cy)
            beamPaint.shader = RadialGradient(
                cx, cy, beamRadius,
                intArrayOf(Color.parseColor("#E61A73E8"), Color.parseColor("#001A73E8")),
                floatArrayOf(0.15f, 1f),
                Shader.TileMode.CLAMP,
            )
            beamRect.set(cx - beamRadius, cy - beamRadius, cx + beamRadius, cy + beamRadius)
            beamPath.reset()
            beamPath.moveTo(cx, cy)
            beamPath.arcTo(beamRect, -90f - beamHalfAngle, beamHalfAngle * 2)
            beamPath.close()
            canvas.drawPath(beamPath, beamPaint)
            canvas.restore()
        }

        canvas.drawCircle(cx, cy + density, dotRadius + ringWidth, shadowPaint)
        canvas.drawCircle(cx, cy, dotRadius + ringWidth, ringPaint)
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)
    }
}
