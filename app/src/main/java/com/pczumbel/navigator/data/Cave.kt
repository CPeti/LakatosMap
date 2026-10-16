package com.pczumbel.navigator.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A cave entrance, as OSM knows it — the same `natural=cave_entrance` nodes OsmAnd renders.
 *
 * [id] is the OSM node id, which is stable across re-fetches, so repeated downloads of
 * overlapping areas upsert rather than duplicate.
 *
 * Per-event selection and scoring deliberately do NOT live here: the catalog is stable and
 * only *which* caves are in play changes each year. That belongs on the event join row.
 */
@Entity(
    tableName = "caves",
    indices = [Index("lat"), Index("lon")],
)
data class Cave(
    @PrimaryKey val id: Long,
    val name: String?,
    val lat: Double,
    val lon: Double,
    /** OSM `ref` — rarely set on Hungarian caves; [caveRef] is the one that matters. */
    val ref: String?,
    val elevationM: Double?,
    /** `cave_entrance` sub-type: cave, shaft, sinkhole… */
    val entranceType: String?,
    val length: String?,
    val depth: String?,
    val description: String?,
    val fetchedAtEpochMs: Long,
    /**
     * OSM `cave:ref` — in Hungary, the national cave register's cadastral number (e.g.
     * "5452-2"), the key into termeszetvedelem.hu. Added in schema v2; ~1,465 of Hungary's
     * ~2,050 mapped entrances carry one.
     */
    val caveRef: String? = null,
    /** Every OSM tag on the node, as JSON — the detail sheet shows them all. Null before v2. */
    val tagsJson: String? = null,
) {
    @Ignore
    val tags: Map<String, String> = tagsJson?.let {
        runCatching { tagJson.decodeFromString(TAG_MAP, it) }.getOrNull()
    } ?: emptyMap()

    /** The register number, whichever tag it ended up in. */
    val registerRef: String?
        get() = caveRef?.takeIf { it.isNotBlank() } ?: ref?.takeIf { it.isNotBlank() }

    /** What to show when OSM has no name — unnamed entrances are common. */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: registerRef?.let { "Cave $it" }
            ?: "Unnamed entrance ($id)"

    companion object {
        private val tagJson = Json { ignoreUnknownKeys = true }
        private val TAG_MAP = MapSerializer(String.serializer(), String.serializer())

        fun encodeTags(tags: Map<String, String>): String = tagJson.encodeToString(TAG_MAP, tags)
    }
}
