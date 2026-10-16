package com.pczumbel.navigator.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox

class CaveRepository(context: Context) {

    private val appContext = context.applicationContext

    private val db = Room.databaseBuilder(
        context.applicationContext,
        NavigatorDatabase::class.java,
        "navigator.db",
    ).addMigrations(NavigatorDatabase.MIGRATION_1_2).build()

    private val dao = db.caveDao()

    fun observeAll(): Flow<List<Cave>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeInBox(box: BoundingBox): Flow<List<Cave>> =
        dao.observeInBox(box.latSouth, box.latNorth, box.lonWest, box.lonEast)

    suspend fun all(): List<Cave> = dao.all()

    suspend fun byId(id: Long): Cave? = dao.byId(id)

    /** True when the catalogue predates full-tag storage and should be re-fetched once. */
    suspend fun needsTagRefresh(): Boolean = dao.countWithoutTags() > 0

    /** Returns how many entrances the area yielded, or the failure to show the user. */
    suspend fun downloadArea(box: BoundingBox, nowMs: Long): Result<Int> =
        OverpassClient.fetchCaves(box, nowMs).mapCatching { caves ->
            dao.upsertAll(caves)
            caves.size
        }

    /**
     * Every cave entrance in Hungary, in one go — ~7,800 nodes, ~2 MB, ~15 s on a good link.
     *
     * Fetched as a bbox (the only form Overpass will answer at this scale) and then clipped
     * to the real border here, so entrances just over the Slovak or Romanian line are dropped.
     *
     * This *replaces* the Hungary region rather than merging into it: anything stored inside
     * the bbox that isn't in the fresh result is deleted. That removes entrances deleted from
     * OSM, and the four Slovak caves the old 1 km border used to let through.
     */
    suspend fun downloadHungary(nowMs: Long, onProgress: (String) -> Unit = {}): Result<Int> {
        onProgress("Querying Overpass for all of Hungary…")
        return OverpassClient.fetchHungaryBbox(nowMs).mapCatching { caves ->
            onProgress("Clipping ${caves.size} entrances to the border…")
            // ~11k border edges × ~8k points: off the main thread.
            val inHungary = withContext(Dispatchers.Default) {
                caves.filter { HungaryBoundary.contains(appContext, it.lat, it.lon) }
            }
            onProgress("Saving ${inHungary.size} entrances…")
            // Chunked so one failure doesn't discard the lot, and so Room isn't handed a
            // single enormous transaction.
            inHungary.chunked(500).forEach { dao.upsertAll(it) }

            val keep = inHungary.mapTo(HashSet()) { it.id }
            val stale = dao.idsInBox(
                HungaryBoundary.SOUTH, HungaryBoundary.NORTH, HungaryBoundary.WEST, HungaryBoundary.EAST,
            ).filterNot { it in keep }
            // SQLite caps bound parameters at 999 per statement.
            stale.chunked(500).forEach { dao.deleteIds(it) }
            inHungary.size
        }
    }

    suspend fun clear() = dao.clear()
}
