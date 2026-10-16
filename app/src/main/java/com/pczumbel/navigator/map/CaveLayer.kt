package com.pczumbel.navigator.map

import com.pczumbel.navigator.data.Cave
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Below this zoom nearby caves merge into numbered clusters; from here up each is drawn. */
const val CLUSTER_BELOW_ZOOM = 14

/**
 * Grid cell size in world pixels (256-px tiles). With tiles scaled to DPI this comes out at
 * roughly 50 dp on screen — about two marker widths, so markers that would overlap merge.
 */
private const val CELL_WORLD_PX = 50.0

/**
 * Draws cave entrances, clustered when zoomed out, inside a single FolderOverlay so the whole
 * layer can be swapped in one operation.
 *
 * Clustering is a fixed grid in world-pixel space rather than distance-based: cell membership
 * depends only on the cave and the integer zoom, never on the viewport, so clusters don't
 * reshuffle while panning — they only change when the zoom level crosses an integer.
 */
class CaveLayer(
    private val mapView: MapView,
    private val icons: CaveIconFactory,
) {

    private var folder = FolderOverlay()

    init {
        mapView.overlays.add(folder)
    }

    fun update(
        caves: List<Cave>,
        selected: Cave?,
        showLabels: Boolean,
        zoom: Int,
        onTap: (Cave) -> Unit,
        onClusterTap: (BoundingBox) -> Unit,
    ) {
        val replacement = FolderOverlay()

        // The selected cave is pulled out before clustering so it can never be absorbed into a
        // cluster, and added last so it draws on top of everything else.
        val others = if (selected == null) caves else caves.filter { it.id != selected.id }

        if (zoom >= CLUSTER_BELOW_ZOOM) {
            others.forEach { replacement.add(caveMarker(it, false, showLabels, onTap)) }
        } else {
            cluster(others, zoom).forEach { members ->
                if (members.size == 1) {
                    replacement.add(caveMarker(members[0], false, showLabels, onTap))
                } else {
                    replacement.add(clusterMarker(members, onClusterTap))
                }
            }
        }

        selected?.let { replacement.add(caveMarker(it, true, showLabels, onTap)) }

        val overlays = mapView.overlays
        val index = overlays.indexOf(folder)
        if (index >= 0) overlays[index] = replacement else overlays.add(replacement)
        folder = replacement
        mapView.invalidate()
    }

    private fun cluster(caves: List<Cave>, zoom: Int): Collection<List<Cave>> {
        val worldSize = 256.0 * 2.0.pow(zoom)
        return caves.groupBy { cave ->
            val x = (cave.lon + 180.0) / 360.0 * worldSize
            val latRad = Math.toRadians(cave.lat)
            val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * worldSize
            floor(x / CELL_WORLD_PX).toLong() to floor(y / CELL_WORLD_PX).toLong()
        }.values
    }

    private fun caveMarker(
        cave: Cave,
        isSelected: Boolean,
        showLabels: Boolean,
        onTap: (Cave) -> Unit,
    ): Marker {
        val icon = icons.icon(
            label = if (showLabels) cave.displayName else null,
            selected = isSelected,
        )
        return Marker(mapView).apply {
            position = GeoPoint(cave.lat, cave.lon)
            setAnchor(icon.anchorU, icon.anchorV)
            this.icon = icon.drawable
            title = cave.displayName
            // Suppress osmdroid's built-in bubble; the detail card is Compose.
            infoWindow = null
            setOnMarkerClickListener { _, _ ->
                onTap(cave)
                true
            }
        }
    }

    private fun clusterMarker(members: List<Cave>, onClusterTap: (BoundingBox) -> Unit): Marker {
        val lat = members.sumOf { it.lat } / members.size
        val lon = members.sumOf { it.lon } / members.size
        val icon = icons.cluster(members.size)
        return Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            setAnchor(0.5f, 0.5f)
            this.icon = icon
            infoWindow = null
            setOnMarkerClickListener { _, _ ->
                onClusterTap(
                    BoundingBox(
                        members.maxOf { it.lat },
                        members.maxOf { it.lon },
                        members.minOf { it.lat },
                        members.minOf { it.lon },
                    ),
                )
                true
            }
        }
    }

    fun detach() {
        mapView.overlays.remove(folder)
    }
}
