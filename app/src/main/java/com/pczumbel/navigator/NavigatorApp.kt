package com.pczumbel.navigator

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.pczumbel.navigator.data.Cadastre
import com.pczumbel.navigator.data.LayerPreferences
import com.pczumbel.navigator.data.RoutingData
import com.pczumbel.navigator.map.MapyKey
import org.osmdroid.config.Configuration
import java.io.File

class NavigatorApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        configureOsmdroid(this)
        RoutingData.init(this)
        Cadastre.init(File(filesDir, "cadastre"))
        MapyKey.value = LayerPreferences(this).mapyApiKey
    }

    /**
     * Register photos (survey maps used for rigging) must still be there underground, so:
     *  - the disk cache lives in filesDir, not cacheDir, which Android may wipe when storage
     *    runs low;
     *  - HTTP cache headers are ignored — the register sends none, which would otherwise leave
     *    whether a photo is kept up to OkHttp's heuristics.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .diskCache {
            DiskCache.Builder()
                .directory(File(filesDir, "register_photos"))
                .maxSizeBytes(1024L * 1024 * 1024)
                .build()
        }
        .build()
}

/**
 * Must run before any MapView is inflated.
 *
 * Two things here are load-bearing:
 *  - userAgentValue: the OSM tile servers hard-block osmdroid's default UA. Tiles will
 *    silently come back as 403 and the map stays blank if this is wrong.
 *  - base path: app-private external storage, so no WRITE_EXTERNAL_STORAGE is needed on
 *    any API level, and uninstalling the app reclaims the tile cache.
 */
fun configureOsmdroid(context: Context) {
    val cfg = Configuration.getInstance()
    cfg.load(context, PreferenceManager.getDefaultSharedPreferences(context))
    cfg.userAgentValue = context.packageName

    val base = File(context.getExternalFilesDir(null), "osmdroid")
    val tiles = File(base, "tiles")
    base.mkdirs()
    tiles.mkdirs()
    cfg.osmdroidBasePath = base
    cfg.osmdroidTileCache = tiles

    // Default is 600 MB. Our areas are small; keep headroom so the trimmer never evicts
    // a deliberately downloaded pack mid-event.
    cfg.tileFileSystemCacheMaxBytes = 2L * 1024 * 1024 * 1024
    cfg.tileFileSystemCacheTrimBytes = 1800L * 1024 * 1024
}
