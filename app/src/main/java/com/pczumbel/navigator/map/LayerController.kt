package com.pczumbel.navigator.map

import android.content.Context
import android.graphics.Color
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Owns the base tile source and the transparent overlay layers on a MapView.
 *
 * Overlays are inserted at the bottom of the overlay list so they draw above the base tiles
 * but beneath cave markers and the location puck — otherwise trail marks would paint over
 * the things you actually tap.
 */
class LayerController(
    private val context: Context,
    private val mapView: MapView,
) {

    private val active = LinkedHashMap<String, TilesOverlay>()
    private val providers = LinkedHashMap<String, MapTileProviderBasic>()
    private var offlineOnly = false

    fun setBase(layer: LayerSource) {
        mapView.setTileSource(layer.tileSource)
        mapView.setUseDataConnection(!offlineOnly)
        mapView.invalidate()
    }

    fun setOverlays(layers: List<LayerSource>) {
        val wanted = layers.associateBy { it.id }

        (active.keys - wanted.keys).toList().forEach { id ->
            active.remove(id)?.let { mapView.overlays.remove(it) }
            providers.remove(id)?.detach()
        }

        layers.forEach { layer ->
            if (active.containsKey(layer.id)) return@forEach
            val provider = MapTileProviderBasic(context, layer.tileSource)
            provider.setUseDataConnection(!offlineOnly)
            val overlay = TilesOverlay(provider, context).apply {
                // Without these the overlay paints an opaque grey grid over the base map
                // while tiles load, which looks like the base layer broke.
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor = Color.TRANSPARENT
            }
            mapView.overlays.add(0, overlay)
            active[layer.id] = overlay
            providers[layer.id] = provider
        }

        mapView.invalidate()
    }

    fun setOfflineOnly(offline: Boolean) {
        offlineOnly = offline
        mapView.setUseDataConnection(!offline)
        providers.values.forEach { it.setUseDataConnection(!offline) }
        active.values.forEach { it.setUseDataConnection(!offline) }
        mapView.invalidate()
    }

    fun detach() {
        active.values.forEach { mapView.overlays.remove(it) }
        providers.values.forEach { it.detach() }
        active.clear()
        providers.clear()
    }
}
