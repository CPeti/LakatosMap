package com.pczumbel.navigator.tiles

import android.content.Context
import com.pczumbel.navigator.map.LayerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox

sealed interface DownloadState {
    data object Idle : DownloadState

    data class Running(
        val layerLabel: String,
        val layerIndex: Int,
        val layerCount: Int,
        val done: Int,
        val total: Int,
        val currentZoom: Int,
    ) : DownloadState {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    data class Finished(val tiles: Int) : DownloadState
    data class Failed(val errors: Int, val message: String? = null) : DownloadState
}

/**
 * Downloads an area for one or more layers, sequentially.
 *
 * MapView-free on purpose: osmdroid's CacheManager(ITileSource, IFilesystemCache, min, max)
 * means a download needn't involve the map at all, which is what lets a single "download this
 * area" cover the base layer *and* whatever overlays are switched on.
 */
class TilePackDownloader {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var task: CacheManager.CacheManagerTask? = null
    private var cancelled = false

    private fun managerFor(layer: LayerSource, zoomMin: Int, zoomMax: Int) =
        CacheManager(layer.tileSource, SharedTileWriter.instance, zoomMin, zoomMax)

    fun estimateTiles(layer: LayerSource, box: BoundingBox, zoomMin: Int, zoomMax: Int): Int =
        runCatching {
            managerFor(layer, zoomMin, zoomMax).possibleTilesInArea(box, zoomMin, zoomMax)
        }.getOrDefault(0)

    /** Only layers whose operator permits bulk download; the rest are silently skipped. */
    fun downloadableOf(layers: List<LayerSource>): List<LayerSource> =
        layers.filter { it.bulkDownloadable }

    fun start(
        context: Context,
        layers: List<LayerSource>,
        box: BoundingBox,
        zoomMin: Int,
        zoomMax: Int,
        onComplete: (Int) -> Unit,
    ) {
        if (_state.value is DownloadState.Running) return
        val queue = downloadableOf(layers)
        if (queue.isEmpty()) {
            _state.value = DownloadState.Failed(0, "No selected layer permits offline download.")
            return
        }
        cancelled = false
        downloadLayer(context, queue, 0, box, zoomMin, zoomMax, 0, onComplete)
    }

    private fun downloadLayer(
        context: Context,
        queue: List<LayerSource>,
        index: Int,
        box: BoundingBox,
        zoomMin: Int,
        zoomMax: Int,
        tilesSoFar: Int,
        onComplete: (Int) -> Unit,
    ) {
        if (cancelled) return
        if (index >= queue.size) {
            _state.value = DownloadState.Finished(tilesSoFar)
            task = null
            onComplete(tilesSoFar)
            return
        }

        val layer = queue[index]
        // Clamp to what the source actually serves, or the last zooms are all 404s.
        val min = zoomMin.coerceAtLeast(layer.tileSource.minimumZoomLevel)
        val max = zoomMax.coerceAtMost(layer.tileSource.maximumZoomLevel)
        if (min > max) {
            downloadLayer(context, queue, index + 1, box, zoomMin, zoomMax, tilesSoFar, onComplete)
            return
        }

        var total = estimateTiles(layer, box, min, max)
        _state.value = DownloadState.Running(layer.label, index + 1, queue.size, 0, total, min)

        val callback = object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                downloadLayer(context, queue, index + 1, box, zoomMin, zoomMax, tilesSoFar + total, onComplete)
            }

            override fun onTaskFailed(errors: Int) {
                // Partial downloads still leave usable tiles, so carry on to the next layer
                // rather than discarding what succeeded.
                _state.value = DownloadState.Failed(errors, "${layer.label}: $errors tiles failed")
                task = null
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                _state.value = DownloadState.Running(
                    layer.label, index + 1, queue.size, progress, total, currentZoomLevel,
                )
            }

            override fun downloadStarted() = Unit

            override fun setPossibleTilesInArea(total_: Int) {
                total = total_
                _state.value = DownloadState.Running(layer.label, index + 1, queue.size, 0, total_, min)
            }
        }

        task = runCatching {
            // ...NoUI: the plain variant puts up osmdroid's own ProgressDialog on top of ours.
            managerFor(layer, min, max).downloadAreaAsyncNoUI(context, box, min, max, callback)
        }.onFailure {
            // TileSourcePolicyException lands here if the source forbids bulk download.
            _state.value = DownloadState.Failed(0, it.message)
        }.getOrNull()
    }

    fun cancel() {
        cancelled = true
        task?.cancel(true)
        task = null
        _state.value = DownloadState.Idle
    }

    fun clearState() {
        _state.value = DownloadState.Idle
    }
}
