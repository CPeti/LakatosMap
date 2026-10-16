package com.pczumbel.navigator.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CaveDao {

    @Query("SELECT * FROM caves ORDER BY name IS NULL, name")
    fun observeAll(): Flow<List<Cave>>

    /**
     * Bounded query so the map only loads what it draws. Room indexes lat/lon, so this stays
     * cheap even once the catalog covers a whole karst region.
     */
    @Query(
        """
        SELECT * FROM caves
        WHERE lat BETWEEN :south AND :north
          AND lon BETWEEN :west AND :east
        LIMIT :limit
        """,
    )
    fun observeInBox(
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        limit: Int = 10_000,
    ): Flow<List<Cave>>

    @Query("SELECT * FROM caves WHERE id = :id")
    suspend fun byId(id: Long): Cave?

    @Query("SELECT COUNT(*) FROM caves")
    fun observeCount(): Flow<Int>

    /**
     * Whole catalog, for in-memory search.
     *
     * Not a `LIKE` query on purpose: SQLite's LIKE is only case-insensitive for ASCII, so
     * `%palvolgyi%` would never match "Pál-völgyi-barlang". Accent folding in Kotlin handles
     * Hungarian properly, and 2,000-odd rows is nothing to filter.
     */
    @Query("SELECT * FROM caves")
    suspend fun all(): List<Cave>

    /** REPLACE, so re-fetching an area refreshes tags rather than duplicating nodes. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(caves: List<Cave>)

    @Query("DELETE FROM caves")
    suspend fun clear()

    /** Caves stored before schema v2 have no tags — the signal to re-fetch once. */
    @Query("SELECT COUNT(*) FROM caves WHERE tagsJson IS NULL")
    suspend fun countWithoutTags(): Int

    @Query("SELECT id FROM caves WHERE lat BETWEEN :south AND :north AND lon BETWEEN :west AND :east")
    suspend fun idsInBox(south: Double, north: Double, west: Double, east: Double): List<Long>

    @Query("DELETE FROM caves WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}

@Database(entities = [Cave::class], version = 2, exportSchema = false)
abstract class NavigatorDatabase : RoomDatabase() {
    abstract fun caveDao(): CaveDao

    companion object {
        /**
         * v1 → v2: `cave:ref` and the full tag set. Plain ADD COLUMNs (nullable TEXT, matching
         * the entity), so the existing catalogue survives; rows get filled on the next fetch.
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caves ADD COLUMN caveRef TEXT")
                db.execSQL("ALTER TABLE caves ADD COLUMN tagsJson TEXT")
            }
        }
    }
}
