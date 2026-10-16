package com.pczumbel.navigator.tiles

import android.content.Context
import com.pczumbel.navigator.map.MapLayers
import org.osmdroid.tileprovider.cachemanager.CacheManager

/**
 * Evicts the tiles of one downloaded area without needing a MapView.
 *
 * Note this clears the area for every layer that was downloaded into it, since osmdroid's
 * cache has no per-area ownership — see the known gaps in the README.
 */
object TileCacheCleaner {

    fun deleteArea(context: Context, pack: TilePack): Result<Unit> = runCatching {
        val layers = (MapLayers.bases + MapLayers.overlays).filter { it.bulkDownloadable }
        layers.forEach { layer ->
            val manager = CacheManager(
                layer.tileSource,
                SharedTileWriter.instance,
                pack.zoomMin,
                pack.zoomMax,
            )
            manager.cleanAreaAsync(context, pack.boundingBox(), pack.zoomMin, pack.zoomMax)
        }
    }
}
