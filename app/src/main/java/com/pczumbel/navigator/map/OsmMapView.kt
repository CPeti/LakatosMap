package com.pczumbel.navigator.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/** Hoisted camera so Compose recomposition / rotation never resets the view. */
data class CameraState(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
) {
    companion object {
        /** Somewhere neutral until the catalog is imported in Phase 2. */
        val Default = CameraState(47.4979, 19.0402, 13.0)
    }
}

/**
 * Builds a MapView bound to the host lifecycle.
 *
 * MapView holds a tile-fetch thread pool and an SQLite handle; forgetting onPause/onDetach
 * leaks both, and the leak only shows up as battery drain in the field, not in a profiler
 * run. Hence the DisposableEffect rather than a bare remember.
 */
@Composable
fun rememberMapViewWithLifecycle(
    onCameraChanged: (CameraState) -> Unit = {},
): MapView {
    val context: Context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            // A source must exist before the first draw; LayerController replaces it with the
            // user's saved choice immediately afterwards.
            setTileSource(MapLayers.OPEN_TOPO.tileSource)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            // osmdroid's default "zoom buttons" overlap our FABs and are redundant with pinch.
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.emitCamera(onCameraChanged)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                mapView.emitCamera(onCameraChanged)
                return false
            }
        }
        mapView.addMapListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(listener)
            mapView.onDetach()
        }
    }

    return mapView
}

private fun MapView.emitCamera(onCameraChanged: (CameraState) -> Unit) {
    val center = mapCenter
    onCameraChanged(CameraState(center.latitude, center.longitude, zoomLevelDouble))
}

fun MapView.applyCamera(state: CameraState) {
    controller.setZoom(state.zoom)
    controller.setCenter(GeoPoint(state.lat, state.lon))
}
