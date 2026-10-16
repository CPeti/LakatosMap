package com.pczumbel.navigator.map

import android.graphics.Color
import com.pczumbel.navigator.data.RoutePlan
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Draws the planned walking route and its numbered waypoint pins.
 *
 * Drawn above the cave markers: when a route is on screen it is the thing you are following,
 * and a line that disappears behind marker clusters is useless. The casing (a wider dark line
 * under a bright one) keeps it legible over both pale rock and dark forest. Pins are shown as
 * soon as a waypoint is set, before any route exists, so you can see what you are planning.
 */
class RouteLayer(
    private val mapView: MapView,
    private val icons: CaveIconFactory,
) {

    private var casing: Polyline? = null
    private var line: Polyline? = null
    private val pins = mutableListOf<Marker>()

    fun showRoute(plan: RoutePlan?) {
        casing?.let { mapView.overlays.remove(it) }
        line?.let { mapView.overlays.remove(it) }
        casing = null
        line = null

        if (plan != null) {
            val density = mapView.context.resources.displayMetrics.density
            val under = Polyline(mapView).apply {
                setPoints(plan.points)
                outlinePaint.color = Color.parseColor("#88000000")
                outlinePaint.strokeWidth = 9f * density
                infoWindow = null
            }
            val over = Polyline(mapView).apply {
                setPoints(plan.points)
                outlinePaint.color = Color.parseColor("#FF2D7DD2")
                outlinePaint.strokeWidth = 5f * density
                infoWindow = null
            }
            // Insert below any pins so the badges stay readable on top of the line.
            val at = pins.firstOrNull()?.let { mapView.overlays.indexOf(it) }?.takeIf { it >= 0 }
            if (at != null) {
                mapView.overlays.add(at, over)
                mapView.overlays.add(at, under)
            } else {
                mapView.overlays.add(under)
                mapView.overlays.add(over)
            }
            casing = under
            line = over
        }
        mapView.invalidate()
    }

    /** Pins for resolved waypoints, labelled "S", "1", … "E". */
    fun showWaypoints(points: List<Pair<GeoPoint, String>>) {
        pins.forEach { mapView.overlays.remove(it) }
        pins.clear()
        points.forEach { (point, badge) ->
            val pin = Marker(mapView).apply {
                position = point
                icon = icons.waypoint(badge)
                setAnchor(0.5f, 0.5f)
                infoWindow = null
                // Non-interactive: taps fall through to whatever is underneath.
                setOnMarkerClickListener { _, _ -> false }
            }
            mapView.overlays.add(pin)
            pins.add(pin)
        }
        mapView.invalidate()
    }

    fun clear() {
        showRoute(null)
        showWaypoints(emptyList())
    }
}
