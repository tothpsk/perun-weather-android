package sk.perun.weather.ui

import sk.perun.weather.R

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

private fun subtitle(l: Location): String =
    listOf(l.country, l.region, l.admin).filter { it.isNotBlank() }.distinct().joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationsSheet(state: LocationState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val spacingPx = with(LocalDensity.current) { 10.dp.toPx() }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Location>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Official alerts of the saved places, loaded when the list opens and shown as a mark before the remove button.
    var alertMarks by remember { mutableStateOf<Map<String, List<AlertItem>>>(emptyMap()) }
    LaunchedEffect(state.locations) {
        loadLocationAlerts(context, state.locations) { key, items -> alertMarks = alertMarks + (key to items) }
    }

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

    val locate: () -> Unit = {
        if (!busy) {
            busy = true
            message = uiText(R.string.ui_zistujem_polohu_1fc6be8c)
            scope.launch {
                try {
                    val fix = currentFix(context)
                    if (fix == null) {
                        message = uiText(R.string.ui_polohu_sa_nepodarilo_zistit_94d1d68c)
                    } else {
                        val place = withContext(Dispatchers.IO) { reversePlace(fix.first, fix.second) }
                            ?: Location(coordinateLabel(fix.first, fix.second), "", fix.first, fix.second)
                        state.add(place)
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
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else message = uiText(R.string.ui_povolenie_polohy_nebolo_udelene_ca808ef8)
    }
    val onGps: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locate()
        } else {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
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
                            dragging = dragging,
                            dragOffset = if (dragging) dragOffset else 0f,
                            alerts = alertMarks[BackgroundRefresh.locationKey(location)],
                            onSelect = { state.select(state.locations.indexOf(location)); onDismiss() },
                            onMoveUp = { state.move(state.locations.indexOf(location), -1) },
                            onMoveDown = { state.move(state.locations.indexOf(location), 1) },
                            onDelete = { state.remove(state.locations.indexOf(location)) },
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
                OutlinedButton(onClick = onGps, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(PerunIcons.Gps, null)
                    Text(uiText(R.string.ui_pridat_aktualnu_polohu_gps_99585c15))
                }
                Button(onClick = { adding = true; message = null }, modifier = Modifier.fillMaxWidth()) {
                    Icon(PerunIcons.Plus, null)
                    Text(uiText(R.string.ui_pridat_lokalitu_70dce8af))
                }
                message?.let { Text(it, color = ForecastMuted, fontSize = 14.sp) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { adding = false; message = null }) { Icon(PerunIcons.Back, uiText(R.string.ui_spat_000d5419)) }
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
    location: Location, selected: Boolean, canDelete: Boolean, dragging: Boolean, dragOffset: Float, alerts: List<AlertItem>?,
    onSelect: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onDelete: () -> Unit,
    onHeight: (Float) -> Unit, handle: Modifier,
) {
    // Same surface as the tiles on the Overview screen.
    val shape = RoundedCornerShape(15.dp)
    val border = if (dragging || selected) Color(0xFF4FC3F7) else Color(0xFF4B586C)
    Row(
        Modifier.fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .onSizeChanged { onHeight(it.height.toFloat()) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(uiText(R.string.ui_posunut_vyssie_f4e2d086)) { onMoveUp(); true },
                    CustomAccessibilityAction(uiText(R.string.ui_posunut_nizsie_ee019ae9)) { onMoveDown(); true },
                )
            }
            .clip(shape).background(if (dragging) Color(0xFF283241) else Color(0xFF202833))
            .border(1.dp, border, shape)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(PerunIcons.Drag, uiText(R.string.ui_presunut_lokalitu_9fe20534), tint = ForecastMuted)
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(location.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle(location), fontSize = 13.sp, color = ForecastMuted)
        }
        if (!alerts.isNullOrEmpty()) LocationAlertMark(alerts)
        IconButton(onClick = onDelete, enabled = canDelete) { Icon(PerunIcons.Close, uiText(R.string.ui_odstranit_lokalitu_7ed2d74e)) }
    }
}
