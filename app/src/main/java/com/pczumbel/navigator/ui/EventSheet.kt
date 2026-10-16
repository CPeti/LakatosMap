package com.pczumbel.navigator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import com.pczumbel.navigator.data.EventPoint
import com.pczumbel.navigator.data.EventOffline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pczumbel.navigator.data.Cave
import com.pczumbel.navigator.data.EventEntity
import com.pczumbel.navigator.data.EventRepository
import com.pczumbel.navigator.data.parseCoordinates
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the search bar is filling in when opened from the event editor. */
enum class EventPickTarget { START, POINT }

/**
 * Events list and editor in one sheet. [editingId] is hoisted to the caller so that when the
 * sheet closes to let you pick a cave from search, it reopens on the same event afterwards.
 */
@Composable
fun EventSheet(
    repo: EventRepository,
    editingId: Long?,
    activeId: Long?,
    selectedCave: Cave?,
    mapCentre: () -> GeoPoint,
    onEdit: (Long?) -> Unit,
    onSetActive: (Long?) -> Unit,
    onSearchCave: (eventId: Long, target: EventPickTarget) -> Unit,
    onMessage: (String) -> Unit,
    lookupCave: suspend (Long) -> Cave?,
) {
    if (editingId == null) {
        EventList(repo, activeId, onEdit)
    } else {
        EventEditor(
            repo = repo,
            eventId = editingId,
            isActive = activeId == editingId,
            selectedCave = selectedCave,
            mapCentre = mapCentre,
            onBack = { onEdit(null) },
            onSetActive = onSetActive,
            onSearchCave = { onSearchCave(editingId, it) },
            onMessage = onMessage,
            lookupCave = lookupCave,
        )
    }
}

@Composable
private fun EventList(repo: EventRepository, activeId: Long?, onEdit: (Long?) -> Unit) {
    val scope = rememberCoroutineScope()
    val events by repo.summaries().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Events", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = {
                scope.launch {
                    val name = "Event " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    onEdit(repo.create(name))
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("New event")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (events.isEmpty()) {
            Text(
                "No events yet. Create one, add the caves and locations in this year's competition, " +
                    "then enter event mode to see only those on the map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(e.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${e.total} points · ${e.visited} visited",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (e.id == activeId) {
                            Icon(
                                Icons.Filled.Flag,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(" Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventEditor(
    repo: EventRepository,
    eventId: Long,
    isActive: Boolean,
    selectedCave: Cave?,
    mapCentre: () -> GeoPoint,
    onBack: () -> Unit,
    onSetActive: (Long?) -> Unit,
    onSearchCave: (EventPickTarget) -> Unit,
    onMessage: (String) -> Unit,
    lookupCave: suspend (Long) -> Cave?,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val event by repo.event(eventId).collectAsStateWithLifecycle(initialValue = null)
    val points by repo.points(eventId).collectAsStateWithLifecycle(initialValue = emptyList())
    val current = event ?: return

    var name by remember(eventId) { mutableStateOf(current.name) }
    // Saved shortly after typing stops rather than per keystroke.
    LaunchedEffect(name) {
        if (name != current.name && name.isNotBlank()) {
            kotlinx.coroutines.delay(400)
            repo.update(current.copy(name = name.trim()))
        }
    }

    var coordinateDialog by remember { mutableStateOf<EventPickTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun setStart(lat: Double, lon: Double, label: String) = scope.launch {
        repo.update(current.copy(startKind = EventEntity.START_POINT, startLat = lat, startLon = lon, startLabel = label))
    }

    fun addCave(cave: Cave) = scope.launch {
        if (!repo.addCave(eventId, cave)) onMessage("${cave.displayName} is already in this event.")
    }

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All events")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Event name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        item {
            Text("Start", style = MaterialTheme.typography.titleSmall)
            LocationChooser(
                label = if (current.startKind == EventEntity.START_MY_LOCATION) {
                    "My location (when planning)"
                } else {
                    current.startLabel ?: "%.5f, %.5f".format(current.startLat, current.startLon)
                },
                selectedCave = selectedCave,
                includeMyLocation = true,
                onMyLocation = {
                    scope.launch {
                        repo.update(current.copy(startKind = EventEntity.START_MY_LOCATION, startLat = null, startLon = null, startLabel = null))
                    }
                },
                onSelectedCave = { setStart(it.lat, it.lon, it.displayName) },
                onSearch = { onSearchCave(EventPickTarget.START) },
                onMapCentre = { mapCentre().let { setStart(it.latitude, it.longitude, "Map point %.4f, %.4f".format(it.latitude, it.longitude)) } },
                onCoordinates = { coordinateDialog = EventPickTarget.START },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Finish back at the start", modifier = Modifier.weight(1f))
                Switch(
                    checked = current.finishAtStart,
                    onCheckedChange = { v -> scope.launch { repo.update(current.copy(finishAtStart = v)) } },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Points · ${points.count { it.visited }}/${points.size} visited",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AddPointButton(
                    selectedCave = selectedCave,
                    onSelectedCave = { addCave(it) },
                    onSearch = { onSearchCave(EventPickTarget.POINT) },
                    onMapCentre = {
                        val c = mapCentre()
                        scope.launch {
                            repo.addLocation(eventId, "Point %.4f, %.4f".format(c.latitude, c.longitude), c.latitude, c.longitude)
                        }
                    },
                    onCoordinates = { coordinateDialog = EventPickTarget.POINT },
                )
            }
            if (points.isEmpty()) {
                Text(
                    "Add the caves and other controls in this competition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(points, key = { it.id }) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = p.visited,
                    onCheckedChange = { v -> scope.launch { repo.setVisited(p, v) } },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (if (p.caveId != null) "Cave · " else "Location · ") + "%.5f, %.5f".format(p.lat, p.lon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { scope.launch { repo.remove(p) } }) {
                    Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove from event")
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OfflineSaveSection(context, points, lookupCave, onMessage)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Button(
                onClick = { onSetActive(if (isActive) null else eventId) },
                enabled = isActive || points.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isActive) "Exit event mode" else "Enter event mode")
            }
            Row(Modifier.padding(top = 4.dp, bottom = 28.dp)) {
                TextButton(
                    onClick = { scope.launch { repo.resetVisited(eventId) } },
                    enabled = points.any { it.visited },
                ) { Text("Reset visited") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(" Delete event", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    coordinateDialog?.let { target ->
        CoordinateDialog(
            title = if (target == EventPickTarget.START) "Start coordinates" else "Add location",
            askName = target == EventPickTarget.POINT,
            onDismiss = { coordinateDialog = null },
            onConfirm = { label, lat, lon ->
                coordinateDialog = null
                if (target == EventPickTarget.START) {
                    setStart(lat, lon, label.ifBlank { "%.5f, %.5f".format(lat, lon) })
                } else {
                    scope.launch { repo.addLocation(eventId, label.ifBlank { "Point %.4f, %.4f".format(lat, lon) }, lat, lon) }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${current.name}?") },
            text = { Text("Removes the event and its ${points.size} points. Caves in the catalogue are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (isActive) onSetActive(null)
                        repo.delete(eventId)
                        onBack()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LocationChooser(
    label: String,
    selectedCave: Cave?,
    includeMyLocation: Boolean,
    onMyLocation: () -> Unit,
    onSelectedCave: (Cave) -> Unit,
    onSearch: () -> Unit,
    onMapCentre: () -> Unit,
    onCoordinates: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp),
        )
        LocationMenu(
            expanded = open,
            onDismiss = { open = false },
            selectedCave = selectedCave,
            includeMyLocation = includeMyLocation,
            onMyLocation = onMyLocation,
            onSelectedCave = onSelectedCave,
            onSearch = onSearch,
            onMapCentre = onMapCentre,
            onCoordinates = onCoordinates,
        )
    }
}

@Composable
private fun AddPointButton(
    selectedCave: Cave?,
    onSelectedCave: (Cave) -> Unit,
    onSearch: () -> Unit,
    onMapCentre: () -> Unit,
    onCoordinates: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
        LocationMenu(
            expanded = open,
            onDismiss = { open = false },
            selectedCave = selectedCave,
            includeMyLocation = false,
            onMyLocation = {},
            onSelectedCave = onSelectedCave,
            onSearch = onSearch,
            onMapCentre = onMapCentre,
            onCoordinates = onCoordinates,
        )
    }
}

@Composable
private fun LocationMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selectedCave: Cave?,
    includeMyLocation: Boolean,
    onMyLocation: () -> Unit,
    onSelectedCave: (Cave) -> Unit,
    onSearch: () -> Unit,
    onMapCentre: () -> Unit,
    onCoordinates: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (includeMyLocation) {
            DropdownMenuItem(
                text = { Text("My location") },
                leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                onClick = { onDismiss(); onMyLocation() },
            )
        }
        DropdownMenuItem(
            text = { Text("Search cave…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            onClick = { onDismiss(); onSearch() },
        )
        selectedCave?.let { cave ->
            DropdownMenuItem(
                text = { Text("Selected: ${cave.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                onClick = { onDismiss(); onSelectedCave(cave) },
            )
        }
        DropdownMenuItem(
            text = { Text("Map centre") },
            leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, contentDescription = null) },
            onClick = { onDismiss(); onMapCentre() },
        )
        DropdownMenuItem(
            text = { Text("Coordinates…") },
            leadingIcon = { Icon(Icons.Filled.EditLocationAlt, contentDescription = null) },
            onClick = { onDismiss(); onCoordinates() },
        )
    }
}

@Composable
private fun CoordinateDialog(
    title: String,
    askName: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (label: String, lat: Double, lon: Double) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val parsed = parseCoordinates(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (askName) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Coordinates") },
                    placeholder = { Text("47.5329, 19.0161") },
                    singleLine = true,
                    isError = text.isNotBlank() && parsed == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    when {
                        text.isBlank() -> "Decimal (47.5329, 19.0161) or DMS (47°31'58\"N 19°00'58\"E)"
                        parsed == null -> "Can't read that as a coordinate"
                        else -> "→ %.5f, %.5f".format(parsed.first, parsed.second)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (text.isNotBlank() && parsed == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onConfirm(label.trim(), it.first, it.second) } }, enabled = parsed != null) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Save for offline": every register entry and photo for the event's caves, in one tap, so
 * survey maps for rigging are on the phone before you lose signal. Shows what's already saved,
 * read from the local caches.
 */
@Composable
private fun OfflineSaveSection(
    context: android.content.Context,
    points: List<EventPoint>,
    lookupCave: suspend (Long) -> Cave?,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<EventOffline.Status?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(points.map { it.id }, refreshTick) {
        status = EventOffline.status(context, points, lookupCave)
    }

    Text("Offline", style = MaterialTheme.typography.titleSmall)
    val st = status
    Text(
        when {
            st == null -> "Checking…"
            st.withRef == 0 -> "No caves with a cadastral number, so nothing to save from the register."
            else -> buildString {
                append("Register entries ${st.entriesSaved}/${st.withRef}")
                if (st.entriesSaved > 0) append(" · photos ${st.photosSaved}/${st.photosKnown}")
                if (st.noRef > 0) append(" · ${st.noRef} without register no.")
                if (st.complete) append(" — all saved")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (st?.complete == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    progress?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
    }
    OutlinedButton(
        onClick = {
            progress = "Starting…"
            scope.launch {
                val report = EventOffline.save(context, points, lookupCave) { progress = it }
                progress = null
                status = report.status
                refreshTick++
                onMessage(
                    if (report.failures.isEmpty()) {
                        "Saved for offline: ${report.status.entriesSaved} entries, ${report.status.photosSaved} photos"
                    } else {
                        "Saved with ${report.failures.size} failure(s): ${report.failures.take(3).joinToString("; ")}"
                    },
                )
            }
        },
        enabled = progress == null && (st?.withRef ?: 0) > 0,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Icon(Icons.Filled.DownloadForOffline, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(if (st?.complete == true) "Refresh offline copy" else "Save register & photos for offline")
    }
}
