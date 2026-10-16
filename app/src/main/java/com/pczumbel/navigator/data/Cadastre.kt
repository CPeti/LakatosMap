package com.pczumbel.navigator.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One label/value row from the register page, e.g. "Hossz" → "358 méter". */
@Serializable
data class CadastreField(val label: String, val value: String)

/**
 * A cave's entry in the national cave register (Országos Barlangnyilvántartás) at
 * termeszetvedelem.hu, looked up by its cadastral number (OSM `cave:ref`).
 *
 * The register is the authoritative source for length, depth, protection and visiting rules,
 * and often has a paragraph of history. It deliberately publishes **no coordinates** — cave
 * locations are withheld to protect them — which is why positions still come from OSM.
 */
@Serializable
data class CadastreEntry(
    val ref: String,
    val name: String?,
    val fields: List<CadastreField>,
    val visiting: List<CadastreField>,
    val description: String?,
    val url: String,
    /** Full-size photo URLs from the register's gallery. Default keeps older cached entries readable. */
    val images: List<String> = emptyList(),
    val fetchedAtEpochMs: Long,
)

object Cadastre {

    private const val PAGE = "https://termeszetvedelem.hu/talalati-oldal/?type=orszagos-barlangnyilvantartas&id="
    private const val USER_AGENT = "navigator-orienteering/0.1 (personal cave map; register lookup)"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Volatile private var cacheDir: File? = null

    fun init(dir: File) {
        cacheDir = dir.also { it.mkdirs() }
    }

    fun pageUrl(ref: String): String = PAGE + URLEncoder.encode(ref, "UTF-8")

    /** Previously fetched entry, readable with no network. */
    fun cached(ref: String): CadastreEntry? =
        cacheFile(ref)?.takeIf { it.isFile }?.let { f ->
            runCatching { json.decodeFromString<CadastreEntry>(f.readText()) }.getOrNull()
        }

    /**
     * Fetch from the register and refresh the cache. Fails (rather than returning junk) when the
     * page has no "Kataszteri szám" row, which is what an unknown number produces.
     */
    suspend fun fetch(ref: String): Result<CadastreEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val url = pageUrl(ref)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Register returned ${connection.responseCode}.")
            }
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()
            val entry = parse(html, ref, url, System.currentTimeMillis())
                ?: error("No register entry for $ref.")
            cacheFile(ref)?.writeText(json.encodeToString(CadastreEntry.serializer(), entry))
            entry
        }
    }

    private fun cacheFile(ref: String): File? =
        cacheDir?.let { File(it, ref.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json") }

    // ---- parsing ---------------------------------------------------------------------------

    /**
     * The page is a WordPress template with the entry rendered as nested divs of "Label:" then
     * value. Rather than depend on class names that change with every theme update, it's
     * flattened to text lines and read positionally between known headings.
     */
    fun parse(html: String, ref: String, url: String, nowMs: Long): CadastreEntry? {
        val lines = htmlToLines(html)
        val start = lines.indexOfFirst { it.startsWith("Kataszteri szám") }
        if (start < 0) return null

        // The cave's name is the line just before the "Adatok" heading.
        val adatok = lines.subList(0, start).indexOfLast { it == "Adatok" }
        val name = if (adatok > 0) lines[adatok - 1] else null

        val fieldsEnd = lines.indexOfFirst(start) { it.startsWith("A barlang által érintett") }
            .takeIf { it > 0 } ?: lines.size
        val fields = readPairs(lines.subList(start, fieldsEnd), labelEndsWithColon = true)

        // Visiting rules: label lines followed by a value ("8") or "-" for not-applicable.
        val visitStart = lines.indexOfFirst(fieldsEnd) { it == "Denevér korlátozás" || it == "Maximális csoportlétszám" }
        val infoStart = lines.indexOfFirst(fieldsEnd) { it == "További információk" }
        val visiting = if (visitStart >= 0) {
            val end = if (infoStart > visitStart) infoStart else minOf(visitStart + 30, lines.size)
            readVisitingRules(lines.subList(visitStart, end))
        } else {
            emptyList()
        }

        // "További információk" → (name again) → the paragraph, up to "Kutatási törzslap".
        val description = if (infoStart >= 0) {
            val stop = lines.indexOfFirst(infoStart) { it.startsWith("Kutatási törzslap") }.takeIf { it > 0 } ?: lines.size
            lines.subList(infoStart + 1, stop)
                .filterNot { it == name }
                .joinToString("\n\n")
                .takeIf { it.isNotBlank() }
        } else {
            null
        }

        return CadastreEntry(ref, name, fields, visiting, description, url, images = parseImages(html), fetchedAtEpochMs = nowMs)
    }

    /**
     * Gallery photos are `<a href="/_user/cave_images/<ref>_<n>.jpg" data-fancybox="images">`
     * links (the `<img>` inside may be a thumbnail). The page repeats the gallery in several
     * blocks, hence the distinct().
     */
    private fun parseImages(html: String): List<String> =
        Regex("""<a[^>]+href="(/_user/cave_images/(?!thumbnails/)[^"]+?\.(?:jpe?g|png|webp))"[^>]*data-fancybox""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { "https://termeszetvedelem.hu" + it.groupValues[1] }
            .distinct()
            .toList()

    private fun readPairs(lines: List<String>, labelEndsWithColon: Boolean): List<CadastreField> {
        val out = mutableListOf<CadastreField>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (labelEndsWithColon && line.endsWith(":")) {
                val next = lines.getOrNull(i + 1)
                if (next != null && !next.endsWith(":")) {
                    out += CadastreField(line.removeSuffix(":").trim(), next)
                    i += 2
                    continue
                }
            }
            i++
        }
        return out
    }

    private val RULE_LABELS = listOf(
        "Denevér korlátozás",
        "Maximális csoportlétszám",
        "Napi maximális csoportszám",
        "Csak elektromos lámpa használható",
        "Csak kutatócsoporttal látogatható",
        "Csak kutatási céllal látogatható",
        "Csak tiszta ruhában látogatható",
    )

    private fun readVisitingRules(lines: List<String>): List<CadastreField> {
        val out = mutableListOf<CadastreField>()
        var i = 0
        while (i < lines.size) {
            val label = lines[i]
            if (label in RULE_LABELS) {
                val value = lines.getOrNull(i + 1)?.takeIf { it !in RULE_LABELS }
                // "-" means the restriction doesn't apply; nothing worth showing.
                if (value != null && value != "-") out += CadastreField(label, value)
                i += if (value != null) 2 else 1
            } else {
                i++
            }
        }
        return out
    }

    private fun <T> List<T>.indexOfFirst(from: Int, predicate: (T) -> Boolean): Int {
        for (i in from.coerceAtLeast(0) until size) if (predicate(this[i])) return i
        return -1
    }

    internal fun htmlToLines(html: String): List<String> {
        val stripped = html
            .replace(Regex("(?is)<script.*?</script>"), "\n")
            .replace(Regex("(?is)<style.*?</style>"), "\n")
            .replace(Regex("(?is)<svg.*?</svg>"), "\n")
            .replace(Regex("<[^>]+>"), "\n")
        return stripped.lineSequence()
            .map { decodeEntities(it).replace(' ', ' ').trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return Regex("&(#x?[0-9A-Fa-f]+|[a-zA-Z]+);").replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> when (e) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
                    "nbsp" -> " "; "ndash" -> "–"; "mdash" -> "—"; "hellip" -> "…"
                    else -> null
                }
            } ?: m.value
        }
    }
}
