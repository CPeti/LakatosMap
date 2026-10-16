package com.pczumbel.navigator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pczumbel.navigator.data.Cave
import com.pczumbel.navigator.data.RoutePlan
import com.pczumbel.navigator.data.formatDistance
import com.pczumbel.navigator.data.formatDuration
import org.osmdroid.util.GeoPoint

/** One row of the planner. Position in the list decides the role: first = start, last = end. */
sealed interface Waypoint {
    data object Unset : Waypoint
    data object MyLocation : Waypoint
    data class CavePoint(val cave: Cave) : Waypoint
    data class MapPoint(val lat: Double, val lon: Double) : Waypoint
    /** A point with its own name — used for event controls that aren't catalogue caves. */
    data class NamedPoint(val name: String, val lat: Double, val lon: Double) : Waypoint

    val label: String
        get() = when (this) {
            Unset -> "Choose…"
            MyLocation -> "My location"
            is CavePoint -> cave.displayName
            is MapPoint -> "Map point %.4f, %.4f".format(lat, lon)
            is NamedPoint -> name
        }

    /** Null when unset, or when it is "my location" and there is no fix yet. */
    fun resolve(myFix: GeoPoint?): GeoPoint? = when (this) {
        Unset -> null
        MyLocation -> myFix
        is CavePoint -> GeoPoint(cave.lat, cave.lon)
        is MapPoint -> GeoPoint(lat, lon)
        is NamedPoint -> GeoPoint(lat, lon)
    }
}

/** "S", "1", "2", … "E" — shared by the planner rows and the map pins so they match. */
fun waypointBadge(index: Int, count: Int): String = when (index) {
    0 -> "S"
    count - 1 -> "E"
    else -> index.toString()
}

private val RouteBlue = Color(0xFF2D7DD2)

/**
 * Non-modal route planner. Lives in the bottom stack with the other cards so the map stays
 * pannable while you build the route, and collapses to a one-line summary for walking.
 */
@Composable
fun RoutePlannerCard(
    waypoints: List<Waypoint>,
    plan: RoutePlan?,
    routing: Boolean,
    collapsed: Boolean,
    selectedCave: Cave?,
    canUseMyLocation: Boolean,
    onSetWaypoint: (index: Int, Waypoint) -> Unit,
    onSearchFor: (index: Int) -> Unit,
    onUseMapCentre: (index: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onAddStop: () -> Unit,
    onReverse: () -> Unit,
    onRoute: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onClose: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (collapsed) {
                        "${waypoints.first().label} → ${waypoints.last().label}"
                    } else {
                        "Directions"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleCollapsed) {
                    Icon(
                        if (collapsed) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (collapsed) "Expand planner" else "Collapse planner",
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close directions")
                }
            }

            if (!collapsed) {
                // Capped so a long list of stops scrolls instead of pushing the card off-screen.
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    waypoints.forEachIndexed { index, waypoint ->
                        WaypointRow(
                            badge = waypointBadge(index, waypoints.size),
                            waypoint = waypoint,
                            canMoveUp = index > 0,
                            canMoveDown = index < waypoints.lastIndex,
                            canRemove = waypoints.size > 2,
                            selectedCave = selectedCave,
                            canUseMyLocation = canUseMyLocation,
                            onSet = { onSetWaypoint(index, it) },
                            onSearch = { onSearchFor(index) },
                            onMapCentre = { onUseMapCentre(index) },
                            onUp = { onMove(index, index - 1) },
                            onDown = { onMove(index, index + 1) },
                            onRemove = { onRemove(index) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onAddStop) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add stop")
                    }
                    TextButton(onClick = onReverse) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reverse")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onRoute,
                        enabled = !routing && waypoints.none { it == Waypoint.Unset },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (routing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Route")
                        }
                    }
                }
            }

            plan?.let { RouteTotals(it) }
        }
    }
}

@Composable
private fun RouteTotals(plan: RoutePlan) {
    Column(Modifier.padding(top = 2.dp, end = 8.dp)) {
        Text(
            text = buildString {
                append(formatDistance(plan.lengthMetres.toDouble()))
                append(" · ")
                append(formatDuration(plan.durationSeconds))
                plan.ascentMetres?.let { append(" · ↑$it m") }
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "%s straight line · %.1f× detour · %s"
                .format(
                    formatDistance(plan.straightLineMetres),
                    plan.detourFactor,
                    if (plan.offline) "computed offline" else "computed online",
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaypointRow(
    badge: String,
    waypoint: Waypoint,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    selectedCave: Cave?,
    canUseMyLocation: Boolean,
    onSet: (Waypoint) -> Unit,
    onSearch: () -> Unit,
    onMapCentre: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = RouteBlue, modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(badge, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        Box(Modifier.weight(1f)) {
            Text(
                text = waypoint.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (waypoint == Waypoint.Unset) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuOpen = true }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("My location") },
                    leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                    enabled = canUseMyLocation,
                    onClick = {
                        menuOpen = false
                        onSet(Waypoint.MyLocation)
                    },
                )
                selectedCave?.let { cave ->
                    DropdownMenuItem(
                        text = { Text("Selected: ${cave.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSet(Waypoint.CavePoint(cave))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Search cave…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSearch()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Map centre") },
                    leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onMapCentre()
                    },
                )
            }
        }

        IconButton(onClick = onUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove")
        }
    }
}
