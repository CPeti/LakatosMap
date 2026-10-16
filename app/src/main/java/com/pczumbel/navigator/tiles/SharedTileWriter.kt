package com.pczumbel.navigator.tiles

import org.osmdroid.tileprovider.modules.SqlTileWriter

/**
 * One sqlite handle on the tile cache for the whole app.
 *
 * CacheManager's download and clean tasks keep using the writer after the call that started
 * them returns, so a per-operation writer would either leak or be closed mid-flight.
 */
object SharedTileWriter {
    val instance: SqlTileWriter by lazy { SqlTileWriter() }
}
