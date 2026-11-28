package sk.perun.weather.ui

import sk.perun.weather.R

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private fun subtitle(l: Location): String =
    listOf(l.country, l.region, l.admin).filter { it.isNotBlank() }.distinct().joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationsSheet(state: LocationState, onCurrentAdded: () -> Unit = {}, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val spacingPx = with(LocalDensity.current) { 10.dp.toPx() }
    // Without any saved place the sheet opens straight on adding one.
    var adding by remember { mutableStateOf(state.locations.isEmpty()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Location>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Official alerts of the saved places, loaded when the list opens and shown as a mark before the drag handle.
    var alertMarks by remember { mutableStateOf<Map<String, List<UnifiedAlert>>>(emptyMap()) }
    LaunchedEffect(state.locations) {
        loadLocationAlerts(context, state.locations) { key, items -> alertMarks = alertMarks + (key to items) }
    }
    // Current weather of the saved places, shown at the left of each card: the same request as the Now screen with the
    // model and sources of the place, so it comes from the shared cache when the place was shown or refreshed recently.
    var weather by remember { mutableStateOf<Map<String, OverviewData>>(emptyMap()) }
    LaunchedEffect(state.locations) {
        val store = LocationSourceStore(context)
        val gate = Semaphore(2)
        state.locations.filter { BackgroundRefresh.positionKey(it) !in weather }.forEach { place ->
            launch {
                val data = gate.withPermit {
                    try { withContext(Dispatchers.IO) { val src = store.read(place); loadOverview(place, src.alertSource, src.weatherModel) } }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { null }
                }
                if (data != null) weather = weather + (BackgroundRefresh.positionKey(place) to data)
            }
        }
    }
    val units = rememberForecastPreferences().units
    val fixTime = rememberCurrentFixTime()
    // The last removed place, its position and whether it was selected, while the undo row is shown.
    var removed by remember { mutableStateOf<Triple<Location, Int, Boolean>?>(null) }
    LaunchedEffect(removed) { if (removed != null) { delay(6000); removed = null } }

    // Drag state: a working copy of the order exists only while a handle is being dragged.
    var order by remember { mutableStateOf<List<Location>?>(null) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }

    val search: () -> Unit = {
        if (query.isNotBlank() && !busy) {
            busy = true
            message = null
            scope.launch {
                try {
                    results = withContext(Dispatchers.IO) { searchPlaces(query) }
                    if (results.isEmpty()) message = uiText(R.string.ui_nic_sa_nenaslo_9c709cd6)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = uiText(R.string.ui_vyhladavanie_zlyhalo_skontroluj_pripojenie_3fbe572d)
                } finally {
                    busy = false
                }
            }
        }
    }

    // "current": the Current location card that follows the device; otherwise a fixed place at the current position.
    fun locate(current: Boolean) {
        if (busy) return
        busy = true
        message = uiText(R.string.loc_locating)
        scope.launch {
            try {
                val place = if (current) CurrentLocation.create(context) else CurrentLocation.fix(context, precise = true)?.let { fix ->
                    withContext(Dispatchers.IO) { reversePlace(fix.latitude, fix.longitude) } ?: Location(coordinateLabel(fix.latitude, fix.longitude), "", fix.latitude, fix.longitude)
                }
                if (place == null) {
                    message = uiText(R.string.loc_location_off)
                } else {
                    state.add(place)
                    if (current) onCurrentAdded()
                    onDismiss()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = uiText(R.string.ui_polohu_sa_nepodarilo_zistit_94d1d68c)
            } finally {
                busy = false
            }
        }
    }

    // Precise and approximate location are asked together; the user may grant only the approximate one.
    var wantCurrent by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate(wantCurrent) else message = uiText(R.string.loc_no_permission)
    }
    fun withPermission(current: Boolean) {
        wantCurrent = current
        if (CurrentLocation.foreground(context)) locate(current)
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!adding) {
                Text(uiText(R.string.ui_ulozene_lokality_e678b27e), style = MaterialTheme.typography.titleLarge)
                val shown = order ?: state.locations
                shown.forEachIndexed { index, location ->
                    key(location) {
                        val dragging = order != null && index == dragIndex
                        LocationRow(
                            location = location,
                            selected = location == state.current,
                            canDelete = shown.size > 1,
                            weather = weather[BackgroundRefresh.positionKey(location)],
                            units = units,
                            fixTime = fixTime,
                            dragging = dragging,
                            dragOffset = if (dragging) dragOffset else 0f,
                            alerts = alertMarks[BackgroundRefresh.locationKey(location)],
                            onSelect = { state.select(state.locations.indexOf(location)); onDismiss() },
                            onMoveUp = { state.move(state.locations.indexOf(location), -1) },
                            onMoveDown = { state.move(state.locations.indexOf(location), 1) },
                            onDelete = {
                                val at = state.locations.indexOf(location)
                                if (at >= 0 && state.locations.size > 1) {
                                    removed = Triple(location, at, location == state.current)
                                    state.remove(at)
                                    // Its warnings and rules leave the notifications at once (Undo brings them back with the next refresh).
                                    Notifier.syncRuleNotifications(context)
                                }
                            },
                            onHeight = { rowHeight = it + spacingPx },
                            handle = Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        order = state.locations
                                        dragIndex = state.locations.indexOf(location)
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        order?.let { state.reorder(it) }
                                        order = null
                                        dragIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        order = null
                                        dragIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val current = order
                                        if (current != null && rowHeight > 0f) {
                                            dragOffset += amount.y
                                            val list = current.toMutableList()
                                            var at = dragIndex
                                            while (dragOffset > rowHeight / 2 && at < list.lastIndex) {
                                                Collections.swap(list, at, at + 1)
                                                at++
                                                dragOffset -= rowHeight
                                            }
                                            while (dragOffset < -rowHeight / 2 && at > 0) {
                                                Collections.swap(list, at, at - 1)
                                                at--
                                                dragOffset += rowHeight
                                            }
                                            dragIndex = at
                                            order = list
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
                removed?.let { (place, at, wasSelected) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A3543)).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(uiText(R.string.loc_removed, place.name), Modifier.weight(1f), fontSize = 14.sp)
                        TextButton(onClick = { state.restore(at, place, wasSelected); removed = null }) {
                            Text(uiText(R.string.loc_undo), color = Color(0xFF4FC3F7), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (shown.size > 1) Text(uiText(R.string.loc_swipe_hint), fontSize = 12.sp, color = ForecastMuted)
                Button(onClick = { adding = true; message = null }, modifier = Modifier.fillMaxWidth()) {
                    Icon(PerunIcons.Plus, null)
                    Text(uiText(R.string.ui_pridat_lokalitu_70dce8af))
                }
                message?.let { Text(it, color = ForecastMuted, fontSize = 14.sp) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (state.locations.isEmpty()) onDismiss() else { adding = false; message = null } }) { Icon(PerunIcons.Back, uiText(R.string.ui_spat_000d5419)) }
                    Text(uiText(R.string.ui_pridat_lokalitu_0252522b), style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(uiText(R.string.ui_miesto_alebo_suradnice_10e88c14)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4FC3F7), unfocusedBorderColor = Color(0xFF4FC3F7),
                        cursorColor = Color(0xFF4FC3F7), focusedLabelColor = Color(0xFF4FC3F7),
                    ),
                )
                Button(onClick = search, enabled = !busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) uiText(R.string.ui_hladam_ee3413d5) else uiText(R.string.ui_hladat_d6982a8b))
                }
                // A fixed place at the current position; the place does not move afterwards.
                // A fixed place at the current position, and the Current location card (at most once); the (i) explains the difference.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { withPermission(false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(PerunIcons.Gps, null)
                        Text("  " + uiText(R.string.loc_use_here))
                    }
                    LocationAddInfo()
                }
                if (!state.hasCurrent) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { withPermission(true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(PerunIcons.Gps, null)
                        Text("  " + uiText(R.string.loc_add_current))
                    }
                    LocationAddInfo()
                }
                message?.let { Text(it, color = ForecastMuted, fontSize = 14.sp) }
                results.forEach { place ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1D24))
                            .border(1.dp, Color(0xFF414958), RoundedCornerShape(14.dp))
                            .clickable { state.add(place); onDismiss() }.padding(12.dp),
                    ) {
                        Text(place.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(subtitle(place), fontSize = 13.sp, color = ForecastMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationRow(
    location: Location, selected: Boolean, canDelete: Boolean, weather: OverviewData?, units: WeatherUnits, fixTime: Long,
    dragging: Boolean, dragOffset: Float, alerts: List<UnifiedAlert>?,
    onSelect: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onDelete: () -> Unit,
    onHeight: (Float) -> Unit, handle: Modifier,
) {
    // Same surface as the tiles on the Overview screen.
    val shape = RoundedCornerShape(15.dp)
    val border = if (dragging || selected) Color(0xFF4FC3F7) else Color(0xFF4B586C)
    val scope = rememberCoroutineScope()
    // Swipe to the left removes the place: past 40 % of the width on release, otherwise the card returns.
    // The last place cannot be removed; its card moves only a little and returns.
    val swipe = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    val removeLabel = uiText(R.string.ui_odstranit_lokalitu_7ed2d74e)
    Box(
        Modifier.fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .onSizeChanged { onHeight(it.height.toFloat()); width = it.width.toFloat() }
            .clip(shape),
    ) {
        if (swipe.value < 0f) Box(
            Modifier.matchParentSize().background(Color(0xFFC62828)).padding(end = 22.dp),
            contentAlignment = Alignment.CenterEnd,
        ) { Icon(PerunIcons.Trash, null, tint = Color.White) }
        Row(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = swipe.value }
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(uiText(R.string.ui_posunut_vyssie_f4e2d086)) { onMoveUp(); true },
                        CustomAccessibilityAction(uiText(R.string.ui_posunut_nizsie_ee019ae9)) { onMoveDown(); true },
                        if (canDelete) CustomAccessibilityAction(removeLabel) { onDelete(); true } else null,
                    )
                }
                .pointerInput(canDelete) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (canDelete && width > 0f && swipe.value < -width * .4f) {
                                    swipe.animateTo(-width)
                                    onDelete()
                                } else swipe.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                    ) { change, amount ->
                        change.consume()
                        val limit = if (canDelete) -width else -width * .12f
                        scope.launch { swipe.snapTo((swipe.value + amount).coerceIn(limit, 0f)) }
                    }
                }
                .clip(shape).background(if (dragging) Color(0xFF283241) else Color(0xFF202833))
                .border(1.dp, border, shape)
                .clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Current weather: the symbol and the temperature of the Now screen; empty until it is loaded.
            Column(Modifier.width(60.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (weather != null) {
                    WeatherSymbol(weather.code, 28.dp, weather.isDayFlag)
                    Text(overviewNumber(units.temperature.fromCelsius(weather.temperature), "°"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else Box(Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(location.name, Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (location.current) Icon(PerunIcons.Gps, uiText(R.string.loc_current), Modifier.padding(start = 5.dp).size(16.dp), tint = Color(0xFF4FC3F7))
                }
                if (location.current) Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF4FC3F7))) { append(currentLocationLabel(fixTime)) }
                        subtitle(location).takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    fontSize = 13.sp, color = ForecastMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                ) else Text(subtitle(location), fontSize = 13.sp, color = ForecastMuted)
            }
            if (!alerts.isNullOrEmpty()) LocationAlertMark(alerts)
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(PerunIcons.Drag, uiText(R.string.ui_presunut_lokalitu_9fe20534), tint = ForecastMuted)
            }
        }
    }
}

/**
 * Shown once the Current location is added without the permission to use the position in the background. It lives
 * outside the sheet, which closes at the same moment (on the first run the app switches to its main screen).
 */
@Composable
internal fun BackgroundLocationDialog(onClose: () -> Unit) {
    // On Android 11 and newer this request opens the location page of the app in the system settings.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onClose() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(uiText(R.string.loc_bg_title)) },
        text = { Text(uiText(R.string.loc_bg_text)) },
        confirmButton = { TextButton(onClick = { launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text(uiText(R.string.loc_bg_open)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(uiText(R.string.loc_bg_later)) } },
    )
}

/** The (i) next to the two buttons: a picture of a fixed place and of the Current location on a trip, with two short texts. */
@Composable
private fun LocationAddInfo() {
    var open by remember { mutableStateOf(false) }
    val muted = ForecastMuted
    IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp).semantics { contentDescription = uiText(R.string.loc_info_title) }) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(muted, radius = size.minDimension / 2 - .65.dp.toPx(), style = Stroke(1.3.dp.toPx()))
            drawLine(muted, Offset(size.width / 2, size.height * .42f), Offset(size.width / 2, size.height * .74f), 1.6.dp.toPx(), StrokeCap.Round)
            drawCircle(muted, 1.1.dp.toPx(), Offset(size.width / 2, size.height * .28f))
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        containerColor = Color(0xFF202833), titleContentColor = Color(0xFFE8EAF0), textContentColor = Color(0xFFE8EAF0),
        title = { Text(uiText(R.string.loc_info_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TripPicture(follow = false, label = uiText(R.string.loc_info_fixed_short), modifier = Modifier.weight(1f))
                    TripPicture(follow = true, label = uiText(R.string.loc_info_follow_short), modifier = Modifier.weight(1f))
                }
                Text(uiText(R.string.loc_use_here), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(uiText(R.string.loc_info_fixed), fontSize = 13.sp, color = Color(0xFFBBC4D2))
                Text(uiText(R.string.loc_add_current), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(uiText(R.string.loc_info_follow), fontSize = 13.sp, color = Color(0xFFBBC4D2))
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("OK", color = Color(0xFF4FC3F7)) } },
    )
}

/** A trip on a small map: a fixed place stays at the start, the Current location goes along with you (fainter marks behind it). */
@Composable
private fun TripPicture(follow: Boolean, label: String, modifier: Modifier) {
    val accent = Color(0xFF4FC3F7)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF283241))) {
            val w = size.width; val h = size.height
            // Light grid of the map.
            for (i in 1..3) {
                drawLine(Color(0xFF34404F), Offset(w * i / 4, 0f), Offset(w * i / 4, h), 1.dp.toPx())
                drawLine(Color(0xFF34404F), Offset(0f, h * i / 4), Offset(w, h * i / 4), 1.dp.toPx())
            }
            fun at(t: Float): Offset {
                // A gentle S-shaped road from the bottom left to the top right.
                val u = 1 - t
                val p0 = Offset(w * .14f, h * .80f); val p1 = Offset(w * .55f, h * .95f); val p2 = Offset(w * .40f, h * .15f); val p3 = Offset(w * .86f, h * .22f)
                return p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
            }
            val road = Path().apply { moveTo(at(0f).x, at(0f).y); for (i in 1..40) { val o = at(i / 40f); lineTo(o.x, o.y) } }
            drawPath(road, Color(0xFF8FA1B5), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
            fun mark(o: Offset, alpha: Float) {
                val c = accent.copy(alpha = alpha); val r = 6.dp.toPx(); val s = 1.8.dp.toPx()
                drawCircle(c, r, o, style = Stroke(s)); drawCircle(c, 1.6.dp.toPx(), o)
                drawLine(c, Offset(o.x, o.y - r - 4.dp.toPx()), Offset(o.x, o.y - r - 1.dp.toPx()), s, StrokeCap.Round)
                drawLine(c, Offset(o.x, o.y + r + 1.dp.toPx()), Offset(o.x, o.y + r + 4.dp.toPx()), s, StrokeCap.Round)
                drawLine(c, Offset(o.x - r - 4.dp.toPx(), o.y), Offset(o.x - r - 1.dp.toPx(), o.y), s, StrokeCap.Round)
                drawLine(c, Offset(o.x + r + 1.dp.toPx(), o.y), Offset(o.x + r + 4.dp.toPx(), o.y), s, StrokeCap.Round)
            }
            if (follow) {
                mark(at(.2f), .25f); mark(at(.55f), .5f); mark(at(1f), 1f)
            } else {
                // A pin at the start of the road; you (a small dot) are already further on.
                val o = at(0f); val r = 7.dp.toPx()
                val pin = Path().apply {
                    moveTo(o.x, o.y)
                    cubicTo(o.x - r * 1.4f, o.y - r * 1.6f, o.x - r, o.y - r * 2.9f, o.x, o.y - r * 2.9f)
                    cubicTo(o.x + r, o.y - r * 2.9f, o.x + r * 1.4f, o.y - r * 1.6f, o.x, o.y)
                    close()
                }
                drawPath(pin, Color(0xFFE57373))
                drawCircle(Color(0xFF283241), r * .38f, Offset(o.x, o.y - r * 1.95f))
                drawCircle(Color(0xFFBBC4D2), 3.dp.toPx(), at(1f))
            }
        }
        Text(label, fontSize = 12.sp, color = Color(0xFFBBC4D2), textAlign = TextAlign.Center)
    }
}
