package com.pczumbel.navigator.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor

/**
 * Offline routing data for the embedded BRouter engine.
 *
 * BRouter's data comes in fixed 5°×5° tiles ("segments", e.g. `E15_N45.rd5`); there is no way
 * to fetch a smaller area. Hungary needs two: E15_N45 (~124 MB, covers Budapest and the west)
 * and E20_N45 (~65 MB, the east incl. Aggtelek). A downloaded map area therefore needs whichever
 * of those it overlaps.
 *
 * Downloads run in an app-wide scope so they survive leaving the screen that started them.
 */
object RoutingData {

    private const val SEGMENT_URL = "https://brouter.de/brouter/segments4/"
    private const val USER_AGENT = "navigator-orienteering/0.1 (offline routing data)"

    /** The two tiles that cover Hungary — offered as a one-tap "all of Hungary". */
    val HUNGARY = listOf("E15_N45", "E20_N45")

    sealed interface State {
        data object Missing : State
        data class Downloading(val bytes: Long, val total: Long) : State {
            val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
        }
        data class Installed(val bytes: Long) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    private lateinit var baseDir: File
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    val segmentDir: File get() = File(baseDir, "segments4")
    val profileDir: File get() = File(baseDir, "profiles2")

    fun init(context: Context) {
        baseDir = File(context.getExternalFilesDir(null), "brouter")
        segmentDir.mkdirs()
        profileDir.mkdirs()
        // The engine reads the profile from a file path, so the bundled assets are copied out.
        // Always overwritten, so an app update that ships a newer profile takes effect.
        listOf("hiking-beta.brf", "lookups.dat").forEach { name ->
            context.assets.open("brouter/$name").use { input ->
                File(profileDir, name).outputStream().use { input.copyTo(it) }
            }
        }
        // A half-finished download from a killed process is not a valid segment.
        segmentDir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        refresh()
    }

    private fun refresh() {
        val installed = segmentDir.listFiles { f -> f.name.endsWith(".rd5") }
            ?.associate { it.nameWithoutExtension to State.Installed(it.length()) as State }
            ?: emptyMap()
        _states.update { current ->
            // Keep in-flight and failed states; everything else reflects what is on disk.
            current.filterValues { it is State.Downloading || it is State.Failed } + installed
        }
    }

    fun stateOf(segment: String): State = _states.value[segment] ?: State.Missing

    fun isInstalled(segment: String) = File(segmentDir, "$segment.rd5").isFile

    /** BRouter's tile naming: the south-west corner, rounded down to a multiple of 5°. */
    fun segmentFor(lat: Double, lon: Double): String {
        val lonBase = (floor(lon / 5.0) * 5).toInt()
        val latBase = (floor(lat / 5.0) * 5).toInt()
        val ew = if (lonBase < 0) "W${-lonBase}" else "E$lonBase"
        val ns = if (latBase < 0) "S${-latBase}" else "N$latBase"
        return "${ew}_$ns"
    }

    fun segmentsFor(box: BoundingBox): List<String> {
        val result = linkedSetOf<String>()
        var lat = floor(box.latSouth / 5.0) * 5
        while (lat <= box.latNorth) {
            var lon = floor(box.lonWest / 5.0) * 5
            while (lon <= box.lonEast) {
                result += segmentFor(lat + 0.1, lon + 0.1)
                lon += 5
            }
            lat += 5
        }
        return result.toList()
    }

    fun download(segment: String) {
        if (jobs[segment]?.isActive == true || isInstalled(segment)) return
        _states.update { it + (segment to State.Downloading(0, 0)) }
        jobs[segment] = scope.launch {
            val target = File(segmentDir, "$segment.rd5")
            val part = File(segmentDir, "$segment.rd5.part")
            runCatching {
                val connection = (URL("$SEGMENT_URL$segment.rd5").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error(
                        if (connection.responseCode == 404) "No routing data exists for $segment."
                        else "Server returned ${connection.responseCode}.",
                    )
                }
                val total = connection.contentLengthLong
                var done = 0L
                var lastReport = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (isActive) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            // Throttled: emitting per 64 KB chunk would recompose ~2,000 times.
                            if (done - lastReport > 512 * 1024) {
                                lastReport = done
                                _states.update { it + (segment to State.Downloading(done, total)) }
                            }
                        }
                    }
                }
                if (!isActive) error("Cancelled")
                if (total > 0 && done != total) error("Incomplete download ($done of $total bytes).")
                // Rename only once complete, so the engine never sees a truncated segment.
                if (!part.renameTo(target)) error("Could not save $segment.")
            }.onFailure { e ->
                part.delete()
                _states.update { it + (segment to State.Failed(e.message ?: "Download failed")) }
            }.onSuccess {
                _states.update { it - segment }
            }
            refresh()
        }
    }

    fun cancel(segment: String) {
        jobs.remove(segment)?.cancel()
        _states.update { it - segment }
        refresh()
    }

    fun delete(segment: String) {
        cancel(segment)
        File(segmentDir, "$segment.rd5").delete()
        _states.update { it - segment }
        refresh()
    }
}
