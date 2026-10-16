package com.pczumbel.navigator.tiles

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import java.io.File

/**
 * Side-loading a prebuilt MBTiles/sqlite pack.
 *
 * This is the escape hatch from the tile-policy problem: build the pack once with QGIS or
 * tilemaker, drop the file on the phone, and the app never scrapes a public tile server at
 * all. It is also the only path that works on a fresh install with no network — which is
 * the real field scenario.
 */
object MbtilesImporter {

    /** Where osmdroid itself looks for offline archives. */
    fun archiveDir(): File = Configuration.getInstance().osmdroidBasePath

    fun listArchives(): List<File> =
        archiveDir().listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("mbtiles", "sqlite", "gemf", "zip")
        }?.sortedBy { it.name } ?: emptyList()

    suspend fun import(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(context, uri) ?: "imported-${uri.hashCode()}.mbtiles"
            val target = File(archiveDir(), name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not open $uri")
            target
        }
    }

    /**
     * Points the map at an offline archive and cuts the network, so what you see is exactly
     * what the pack contains. The tile-source name must match the one baked into the archive
     * or every tile silently misses.
     */
    fun applyTo(context: Context, mapView: MapView, archive: File) {
        val provider = OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(archive))
        val sourceName = provider.archives
            .firstOrNull()
            ?.tileSources
            ?.firstOrNull()

        mapView.tileProvider = provider
        if (sourceName != null) {
            mapView.setTileSource(FileBasedTileSource.getSource(sourceName))
        }
        mapView.setUseDataConnection(false)
        mapView.invalidate()
    }

    fun delete(archive: File): Boolean = archive.delete()

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
}
