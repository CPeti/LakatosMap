package com.pczumbel.navigator.map

import com.pczumbel.navigator.data.EventPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Event-mode markers: only the event's points, never clustered (an event has a few dozen at
 * most, and hiding a control inside a cluster defeats the point of the mode).
 */
class EventLayer(
    private val mapView: MapView,
    private val icons: CaveIconFactory,
) {
    private var folder = FolderOverlay()

    init {
        mapView.overlays.add(folder)
    }

    fun update(points: List<EventPoint>, focusedId: Long?, showLabels: Boolean, onTap: (EventPoint) -> Unit) {
        val replacement = FolderOverlay()
        // Visited first so the still-to-do points draw on top where they overlap.
        points.sortedByDescending { it.visited }.forEach { point ->
            replacement.add(
                Marker(mapView).apply {
                    position = GeoPoint(point.lat, point.lon)
                    icon = icons.eventPoint(
                        isCave = point.caveId != null,
                        visited = point.visited,
                        focused = point.id == focusedId,
                    )
                    setAnchor(0.5f, 0.5f)
                    title = point.name
                    infoWindow = null
                    setOnMarkerClickListener { _, _ ->
                        onTap(point)
                        true
                    }
                },
            )
            if (showLabels) replacement.add(textLabel(point))
        }
        val overlays = mapView.overlays
        val index = overlays.indexOf(folder)
        if (index >= 0) overlays[index] = replacement else overlays.add(replacement)
        folder = replacement
        mapView.invalidate()
    }

    /** Name drawn to the right of the marker, non-interactive. */
    private fun textLabel(point: EventPoint): Marker {
        val label = icons.labelOnly(point.name)
        return Marker(mapView).apply {
            position = GeoPoint(point.lat, point.lon)
            icon = label.drawable
            setAnchor(label.anchorU, label.anchorV)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> false }
        }
    }

    fun clear() = update(emptyList(), null, false) {}
}
