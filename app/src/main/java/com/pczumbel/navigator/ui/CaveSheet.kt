package com.pczumbel.navigator.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pczumbel.navigator.data.Cadastre
import com.pczumbel.navigator.data.CadastreEntry
import com.pczumbel.navigator.data.Cave
import com.pczumbel.navigator.data.formatDistance
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

private sealed interface RegisterState {
    data object None : RegisterState
    data object Loading : RegisterState
    /** [saved]: from the on-phone cache; [refreshing]: a live fetch is still in flight. */
    data class Loaded(val entry: CadastreEntry, val saved: Boolean, val refreshing: Boolean) : RegisterState
    data class Failed(val message: String) : RegisterState
}

/** Velocity (px/s) above which a release flings to the next stop instead of the nearest one. */
private const val FLING_VELOCITY = 1200f

/**
 * The cave detail sheet, modelled on ut.no. Three resting heights:
 *  - **collapsed** — exactly the header (name, actions, key facts), ending on the divider;
 *  - **half** — the map stays visible above, and the content scrolls inside the sheet;
 *  - **full** — reading mode, stops below the status bar.
 *
 * Only the header drags the sheet. The content below it only ever scrolls, so at half height
 * you can read the whole entry without the sheet jumping to full. Drawn on top of the map's
 * side buttons rather than pushing them around.
 */
@Composable
fun CaveSheet(
    cave: Cave,
    distanceMetres: Double?,
    plannerOpen: Boolean,
    eventName: String?,
    /** Non-null when the sheet shows an event point: adds the Mark visited button. */
    visited: Boolean? = null,
    onToggleVisited: () -> Unit = {},
    /** False for an event's custom location: no register lookup, no OSM section. */
    isCatalogueCave: Boolean = true,
    halfHeightPx: Float,
    fullHeightPx: Float,
    onDirections: () -> Unit,
    onAddToRoute: () -> Unit,
    onAddToEvent: () -> Unit,
    onCentre: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tags = cave.tags
    val ref = if (isCatalogueCave) cave.registerRef else null
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var register by remember(cave.id) { mutableStateOf<RegisterState>(RegisterState.None) }
    LaunchedEffect(cave.id) {
        if (ref == null) return@LaunchedEffect
        // Show the saved copy instantly (works offline), then try to refresh it.
        val cached = Cadastre.cached(ref)
        register = cached?.let { RegisterState.Loaded(it, saved = true, refreshing = true) } ?: RegisterState.Loading
        Cadastre.fetch(ref).fold(
            onSuccess = { register = RegisterState.Loaded(it, saved = false, refreshing = false) },
            onFailure = {
                register = cached?.let { c -> RegisterState.Loaded(c, saved = true, refreshing = false) }
                    ?: RegisterState.Failed(it.message ?: "Could not reach the register.")
            },
        )
    }

    // ---- sizing ------------------------------------------------------------------------
    val navInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    var headerPx by remember { mutableIntStateOf(0) }
    val peekPx = headerPx + navInsetPx
    val stops = listOf(peekPx, halfHeightPx.coerceAtLeast(peekPx), fullHeightPx.coerceAtLeast(peekPx))
    var stop by remember(cave.id) { mutableIntStateOf(0) }
    var heightPx by remember(cave.id) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    // Slide in on open, and follow the collapsed height as the header grows (e.g. when the
    // register loads and adds a protection chip) — but never fight an active drag.
    LaunchedEffect(cave.id, headerPx, halfHeightPx, fullHeightPx) {
        if (headerPx == 0 || dragging) return@LaunchedEffect
        animate(heightPx, stops[stop]) { v, _ -> heightPx = v }
    }

    val dragState = rememberDraggableState { delta ->
        heightPx = (heightPx - delta).coerceIn(peekPx, stops.last())
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() }),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            // ---- header: the draggable part, and all that shows when collapsed -----------
            Column(
                Modifier
                    // Measured at its natural height even while the sheet is still sliding in.
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { headerPx = it.height }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragging = true },
                        onDragStopped = { velocity ->
                            val upward = -velocity
                            val target = when {
                                upward > FLING_VELOCITY ->
                                    stops.indexOfFirst { it > heightPx + 1 }.takeIf { it >= 0 } ?: stops.lastIndex
                                upward < -FLING_VELOCITY ->
                                    stops.indexOfLast { it < heightPx - 1 }.takeIf { it >= 0 } ?: 0
                                else -> stops.indices.minBy { kotlin.math.abs(stops[it] - heightPx) }
                            }
                            stop = target
                            scope.launch {
                                animate(heightPx, stops[target], initialVelocity = upward) { v, _ -> heightPx = v }
                                dragging = false
                            }
                        },
                    )
                    .padding(horizontal = 20.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        Modifier.size(width = 36.dp, height = 4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ) {}
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            cave.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                if (isCatalogueCave) entranceLabel(tags, cave.entranceType) else "Event location",
                                if (visited == true) "Visited" else null,
                                ref,
                                distanceMetres?.let { formatDistance(it) + " away" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // In event mode, marking it done is the main action, so it comes first.
                    if (visited != null) {
                        if (visited) {
                            OutlinedButton(onClick = onToggleVisited) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Unmark")
                            }
                        } else {
                            Button(onClick = onToggleVisited) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mark visited")
                            }
                        }
                    }
                    if (plannerOpen) {
                        FilledTonalButton(onClick = onAddToRoute) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add to route")
                        }
                    } else {
                        FilledTonalButton(onClick = onDirections) {
                            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Directions")
                        }
                    }
                    if (eventName != null && visited == null) {
                        OutlinedButton(onClick = onAddToEvent) {
                            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add to event", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onCentre) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Centre on map")
                    }
                }

                KeyFacts(cave, tags, register)
                Spacer(Modifier.height(10.dp))
                // The collapsed sheet ends exactly on this line.
                HorizontalDivider()
            }

            // ---- body: scrolls, never drags --------------------------------------------
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                (register as? RegisterState.Loaded)?.entry?.images?.takeIf { it.isNotEmpty() }?.let { images ->
                    Gallery(images)
                }
                if (isCatalogueCave) {
                    RegisterSection(ref, register)
                    OsmSection(cave, tags)
                } else {
                    SectionTitle("Location", divider = false)
                    KeyValue("Coordinates", "%.6f, %.6f".format(cave.lat, cave.lon))
                    Muted("A custom point in this event, not a catalogue cave.")
                }
            }
        }
    }
}

/** Horizontal strip of register photos; tapping one opens a full-screen swipeable viewer. */
@Composable
private fun Gallery(images: List<String>) {
    var openAt by remember { mutableStateOf<Int?>(null) }
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(images) { i, url ->
            AsyncImage(
                model = url,
                contentDescription = "Photo ${i + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 168.dp, height = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { openAt = i },
            )
        }
    }
    Text(
        "${images.size} photo${if (images.size == 1) "" else "s"} · national cave register",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )

    openAt?.let { start ->
        Dialog(
            onDismissRequest = { openAt = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val pager = rememberPagerState(initialPage = start) { images.size }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    ZoomableImage(images[page])
                }
                Text(
                    "${pager.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
                )
                IconButton(
                    onClick = { openAt = null },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close photos", tint = Color.White)
                }
            }
        }
    }
}

/** Pinch to zoom, drag to pan while zoomed; double-tap toggles 2.5×. Swiping pages works at 1×. */
@Composable
private fun ZoomableImage(url: String) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(url) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        // Only take over when zooming or already zoomed, so the pager can still
                        // swipe between photos at normal size.
                        if (zoom != 1f || scale > 1f) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

private fun entranceLabel(tags: Map<String, String>, entranceType: String?): String = when {
    tags["natural"] == "sinkhole" -> when (tags["sinkhole"]) {
        "pit" -> "Pit (zsomboly)"
        "doline" -> "Doline"
        else -> "Sinkhole"
    }
    tags["sinkhole"] == "pit" -> "Shaft entrance"
    else -> when (entranceType) {
        null, "cave_entrance" -> "Cave entrance"
        else -> "Cave entrance (${entranceType.replace('_', ' ')})"
    }
}

/**
 * The three numbers people actually want, preferring the register (authoritative) and falling
 * back to OSM. Kept in the collapsed area so they're readable without dragging.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyFacts(cave: Cave, tags: Map<String, String>, register: RegisterState) {
    val reg = (register as? RegisterState.Loaded)?.entry
    fun regField(label: String) = reg?.fields?.firstOrNull { it.label == label }?.value
        ?.takeUnless { it.startsWith("0 ") }
    val facts = listOfNotNull(
        (regField("Hossz") ?: (tags["length"] ?: cave.length)?.let { "$it m" })?.let { "Length $it" },
        (regField("Mélység") ?: (tags["depth"] ?: cave.depth)?.let { "$it m" })?.let { "Depth $it" },
        (tags["ele"] ?: cave.elevationM?.toInt()?.toString())?.let { "Elevation $it m" },
        regField("Védettség"),
    ).map { it.replace("méter", "m") }
    if (facts.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        facts.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
    }
}

@Composable
private fun RegisterSection(ref: String?, state: RegisterState) {
    val uri = LocalUriHandler.current
    SectionTitle("National cave register", divider = false)
    when {
        ref == null -> Muted("No cadastral number (cave:ref) in OSM, so this cave can't be looked up in the register.")
        state is RegisterState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Muted("Fetching $ref from termeszetvedelem.hu…")
        }
        state is RegisterState.Failed -> Muted("Couldn't load: ${state.message} Opening it once with signal saves it for offline.")
        state is RegisterState.Loaded -> {
            val e = state.entry
            if (state.saved) {
                val date = DateFormat.getDateInstance().format(Date(e.fetchedAtEpochMs))
                Muted(if (state.refreshing) "Saved copy from $date — refreshing…" else "Saved copy from $date (offline — couldn't refresh)")
            }
            e.fields.forEach { KeyValue(it.label, it.value) }
            if (e.visiting.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Visiting rules", style = MaterialTheme.typography.labelLarge)
                e.visiting.forEach { KeyValue(it.label, it.value) }
            }
            e.description?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            LinkButton("Open register page") { uri.openUri(e.url) }
        }
    }
}

/** Tags worth a friendly label, in the order they're most useful. Everything else goes raw. */
private val FRIENDLY = linkedMapOf(
    "alt_name" to "Also known as",
    "access" to "Access",
    "protection_title" to "Protection",
    "protection_title:en" to "Protection (en)",
    "note:hu" to "Note",
    "note:en" to "Note (en)",
    "note" to "Note",
    "description" to "Description",
    "description:hu" to "Description",
    "fee" to "Fee",
    "opening_hours" to "Opening hours",
    "operator" to "Operator",
    "cave" to "Cave type",
    "sinkhole" to "Sinkhole type",
    "ref:sk" to "Slovak register no.",
)

@Composable
private fun OsmSection(cave: Cave, tags: Map<String, String>) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var showAll by remember(cave.id) { mutableStateOf(false) }

    SectionTitle("OpenStreetMap")
    if (tags.isEmpty()) {
        Muted("Full OSM details arrive with the next cave fetch.")
    }
    FRIENDLY.forEach { (key, label) -> tags[key]?.let { KeyValue(label, it) } }

    val coords = "%.6f, %.6f".format(cave.lat, cave.lon)
    Row(verticalAlignment = Alignment.CenterVertically) {
        KeyValue("Coordinates", coords, Modifier.weight(1f))
        IconButton(onClick = { clipboard.setText(AnnotatedString(coords)) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy coordinates", modifier = Modifier.size(18.dp))
        }
    }

    tags["wikipedia"]?.let { wp ->
        val (lang, title) = wp.split(":", limit = 2).let { if (it.size == 2) it[0] to it[1] else "hu" to it[0] }
        LinkButton("Wikipedia: $title") {
            uri.openUri("https://$lang.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8"))
        }
    }
    tags["website"]?.takeUnless { it.contains("termeszetvedelem.hu") }?.let { site ->
        LinkButton("Website") { uri.openUri(site) }
    }
    LinkButton("OSM node ${cave.id}") { uri.openUri("https://www.openstreetmap.org/node/${cave.id}") }

    if (tags.isNotEmpty()) {
        TextButton(onClick = { showAll = !showAll }) {
            Icon(if (showAll) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            Text(if (showAll) "Hide all tags" else "All ${tags.size} OSM tags")
        }
        if (showAll) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(10.dp)) {
                    tags.toSortedMap().forEach { (k, v) -> KeyValue(k, v, monospaceKey = true) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, divider: Boolean = true) {
    Spacer(Modifier.height(16.dp))
    if (divider) {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
    }
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Muted(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun KeyValue(label: String, value: String, modifier: Modifier = Modifier, monospaceKey: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = if (monospaceKey) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (monospaceKey) FontWeight.Medium else null,
            modifier = Modifier.weight(0.42f).padding(end = 8.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
}
