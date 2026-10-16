package com.pczumbel.navigator.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A competition: a named set of points to visit, from a chosen start.
 *
 * Kept in its own database rather than as new tables in navigator.db: that one is at schema
 * version 1 with no exported schema, so adding tables would need a hand-written migration or a
 * destructive one that throws away the downloaded cave catalogue. A second tiny database
 * sidesteps both.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "my_location" (resolved when planning) or "point" (startLat/startLon). */
    val startKind: String = START_MY_LOCATION,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val startLabel: String? = null,
    /** Score events usually finish where they start; the planned route then loops back. */
    val finishAtStart: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        const val START_MY_LOCATION = "my_location"
        const val START_POINT = "point"
    }
}

@Entity(
    tableName = "event_points",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("eventId")],
)
data class EventPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    /** Set when the point is a cave from the catalogue; null for a custom location. */
    val caveId: Long? = null,
    val name: String,
    val lat: Double,
    val lon: Double,
    val visitedAtEpochMs: Long? = null,
    val position: Int = 0,
) {
    val visited: Boolean get() = visitedAtEpochMs != null
}

data class EventSummary(
    val id: Long,
    val name: String,
    val total: Int,
    val visited: Int,
)

@Dao
interface EventDao {
    @Query(
        """
        SELECT e.id, e.name,
               (SELECT COUNT(*) FROM event_points p WHERE p.eventId = e.id) AS total,
               (SELECT COUNT(*) FROM event_points p WHERE p.eventId = e.id AND p.visitedAtEpochMs IS NOT NULL) AS visited
        FROM events e ORDER BY e.createdAtEpochMs DESC
        """,
    )
    fun observeSummaries(): Flow<List<EventSummary>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeEvent(id: Long): Flow<EventEntity?>

    @Query("SELECT * FROM event_points WHERE eventId = :eventId ORDER BY position, id")
    fun observePoints(eventId: Long): Flow<List<EventPoint>>

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM event_points WHERE eventId = :eventId")
    suspend fun nextPosition(eventId: Long): Int

    @Insert
    suspend fun insertPoint(point: EventPoint): Long

    @Delete
    suspend fun deletePoint(point: EventPoint)

    @Query("UPDATE event_points SET visitedAtEpochMs = :at WHERE id = :pointId")
    suspend fun setVisited(pointId: Long, at: Long?)

    @Query("UPDATE event_points SET visitedAtEpochMs = NULL WHERE eventId = :eventId")
    suspend fun resetVisited(eventId: Long)

    @Query("SELECT COUNT(*) FROM event_points WHERE eventId = :eventId AND caveId = :caveId")
    suspend fun countCave(eventId: Long, caveId: Long): Int

    @Transaction
    suspend fun addPoint(point: EventPoint): Long =
        insertPoint(point.copy(position = nextPosition(point.eventId)))
}

@Database(entities = [EventEntity::class, EventPoint::class], version = 1, exportSchema = false)
abstract class EventDatabase : RoomDatabase() {
    abstract fun events(): EventDao
}

class EventRepository(context: Context) {

    private val dao = Room.databaseBuilder(context.applicationContext, EventDatabase::class.java, "events.db")
        .build()
        .events()

    private val prefs = context.applicationContext.getSharedPreferences("events", Context.MODE_PRIVATE)

    /** The event whose points are shown in event mode, or null when event mode is off. */
    var activeEventId: Long?
        get() = prefs.getLong(KEY_ACTIVE, -1).takeIf { it >= 0 }
        set(value) = prefs.edit().putLong(KEY_ACTIVE, value ?: -1).apply()

    fun summaries() = dao.observeSummaries()
    fun event(id: Long) = dao.observeEvent(id)
    fun points(eventId: Long) = dao.observePoints(eventId)

    suspend fun create(name: String): Long = dao.insertEvent(EventEntity(name = name))
    suspend fun update(event: EventEntity) = dao.updateEvent(event)
    suspend fun delete(id: Long) {
        if (activeEventId == id) activeEventId = null
        dao.deleteEvent(id)
    }

    /** Returns false when that cave is already in the event, so it isn't added twice. */
    suspend fun addCave(eventId: Long, cave: Cave): Boolean {
        if (dao.countCave(eventId, cave.id) > 0) return false
        dao.addPoint(EventPoint(eventId = eventId, caveId = cave.id, name = cave.displayName, lat = cave.lat, lon = cave.lon))
        return true
    }

    suspend fun addLocation(eventId: Long, name: String, lat: Double, lon: Double) {
        dao.addPoint(EventPoint(eventId = eventId, name = name, lat = lat, lon = lon))
    }

    suspend fun remove(point: EventPoint) = dao.deletePoint(point)

    suspend fun setVisited(point: EventPoint, visited: Boolean) =
        dao.setVisited(point.id, if (visited) System.currentTimeMillis() else null)

    suspend fun resetVisited(eventId: Long) = dao.resetVisited(eventId)

    private companion object {
        const val KEY_ACTIVE = "active_event_id"
    }
}

/**
 * Lenient coordinate parsing for typed-in points: "47.5329, 19.0161", "47.5329 19.0161",
 * "N47.5329 E19.0161", Google-Maps style pastes, and degrees-minutes-seconds like
 * 47°31'58.4"N 19°00'58.1"E. Returns lat to lon, or null.
 */
fun parseCoordinates(text: String): Pair<Double, Double>? {
    val t = text.trim().uppercase()

    val dms = Regex("""(\d+(?:\.\d+)?)\s*°\s*(\d+(?:\.\d+)?)?\s*['′]?\s*(\d+(?:\.\d+)?)?\s*(?:"|″|'')?\s*([NSEW])""")
    val dmsMatches = dms.findAll(t).toList()
    if (dmsMatches.size == 2) {
        val values = dmsMatches.map { m ->
            val (d, mi, s, h) = m.destructured
            val v = d.toDouble() + (mi.toDoubleOrNull() ?: 0.0) / 60 + (s.toDoubleOrNull() ?: 0.0) / 3600
            h to if (h == "S" || h == "W") -v else v
        }
        val lat = values.firstOrNull { it.first == "N" || it.first == "S" }?.second
        val lon = values.firstOrNull { it.first == "E" || it.first == "W" }?.second
        if (lat != null && lon != null) return lat to lon
    }

    val numbers = Regex("""-?\d+(?:\.\d+)?""").findAll(t).map { it.value.toDouble() }.toList()
    if (numbers.size != 2) return null
    var (lat, lon) = numbers
    // Standalone hemisphere letters only, so an 'S' inside a pasted place name doesn't flip it.
    if (Regex("""\bS\b""").containsMatchIn(t)) lat = -kotlin.math.abs(lat)
    if (Regex("""\bW\b""").containsMatchIn(t)) lon = -kotlin.math.abs(lon)
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}
