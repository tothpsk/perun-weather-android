package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.json.JSONArray
import java.util.Collections
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class OverviewTileId(val key:String,val label:String) {
    Precipitation("precipitation",uiText(R.string.ui_zrazky_66d17319)), Wind("wind",uiText(R.string.ui_vietor_8a3ebf26)), Cloud("cloud",uiText(R.string.ui_oblacnost_b5a6e70c)), Uv("uv",uiText(R.string.ui_uv_027fcbbc)),
    Humidity("humidity",uiText(R.string.ui_vlhkost_e06c751e)), Pressure("pressure",uiText(R.string.ui_tlak_vzduchu_cc4c1846)), Visibility("visibility",uiText(R.string.ui_viditelnost_7f90db41)),
    Sun("sun",uiText(R.string.ui_slnko_dnes_4d65cb81)), Moon("moon",uiText(R.string.ui_mesiac_dnes_0e15b5c9)), Air("air",uiText(R.string.ui_kvalita_ovzdusia_08946dda)), Pollen("pollen",uiText(R.string.ui_pel_aktualne_0fd4a0ea)), Alerts("alerts",uiText(R.string.ui_vystrahy_d88f15ec))
}
// Marine tiles share one ordered list with the weather tiles; their order keys carry the "marine:" prefix.
internal enum class MarineOverviewTileId(val key:String,val label:String) {
    Temperature("sea_surface_temperature",uiText(R.string.ui_teplota_mora_0e2349bf)),
    Level("sea_level_height_msl",uiText(R.string.ui_hladina_mora_a4623230)),
    Waves("wave_height",uiText(R.string.ui_vyska_vln_4b75e096)),
    Period("wave_period",uiText(R.string.ui_perioda_vln_47347236));
    val orderKey:String get() = "marine:$key"
}
internal fun overviewTileSelection(stored:Set<String>?):Set<OverviewTileId> =
    if(stored==null) OverviewTileId.entries.toSet() else OverviewTileId.entries.filter { it.key in stored }.toSet()
// The alerts are not a tile of the grid: they sit in the header of Now, so they are neither ordered nor switched off.
internal fun defaultOverviewTileOrder():List<String> = OverviewTileId.entries.filter { it!=OverviewTileId.Alerts }.map { it.key }+MarineOverviewTileId.entries.map { it.orderKey }
// Stored order is a JSON array of keys: unknown keys are dropped, missing keys are appended in default order.
internal fun overviewTileOrder(stored:String?):List<String> {
    val defaults=defaultOverviewTileOrder()
    val parsed=if(stored==null) emptyList() else runCatching { val a=JSONArray(stored);List(a.length()) { a.optString(it) } }.getOrDefault(emptyList())
    val known=parsed.filter { it in defaults }.distinct()
    return known+defaults.filter { it !in known }
}
internal fun overviewTileRank(order:List<String>,cellKey:String):Int {
    // Weather cells are keyed "overview:<EnumName>", marine cells "marine:<apiKey>".
    val name=cellKey.removePrefix("overview:")
    val key=if(cellKey.startsWith("marine:")) cellKey else OverviewTileId.entries.firstOrNull { it.name==name || it.key==name }?.key ?: name
    val i=order.indexOf(key)
    return if(i<0) order.size else i
}
private const val TileKey="visibleOverviewTiles2"
private const val OutlookKey="showOverviewOutlook"
private const val OrderKey="overviewTileOrder"
private const val MarineTilesKey="marineOverviewTiles"
private fun readTiles(p:SharedPreferences)=overviewTileSelection(p.getStringSet(TileKey,null)?.toSet())
private fun readOutlook(p:SharedPreferences)=p.getBoolean(OutlookKey,true)
private fun readOrder(p:SharedPreferences)=overviewTileOrder(p.getString(OrderKey,null))
private fun readMarine(p:SharedPreferences)=p.getStringSet(MarineTilesKey,null)?.toSet() ?: MarineOverviewTileId.entries.map { it.key }.toSet()
@Composable private fun <T> rememberForecastPreference(keys:Set<String>,read:(SharedPreferences)->T):T {
    val context=LocalContext.current
    val p=remember(context) { context.getSharedPreferences("perun_forecast",android.content.Context.MODE_PRIVATE) }
    var value by remember(p) { mutableStateOf(read(p)) }
    DisposableEffect(p) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,key -> if(key==null || key in keys) value=read(p) }
        p.registerOnSharedPreferenceChangeListener(listener)
        value=read(p)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
@Composable internal fun rememberOverviewTiles():Set<OverviewTileId> = rememberForecastPreference(setOf(TileKey)) { readTiles(it) }
@Composable internal fun rememberOverviewOutlook():Boolean = rememberForecastPreference(setOf(OutlookKey)) { readOutlook(it) }
@Composable internal fun rememberOverviewTileOrder():List<String> = rememberForecastPreference(setOf(OrderKey)) { readOrder(it) }
@Composable internal fun OverviewTileSettings() {
    val context=LocalContext.current
    val p=remember(context) { context.getSharedPreferences("perun_forecast",android.content.Context.MODE_PRIVATE) }
    val selected=rememberOverviewTiles()
    val marine=rememberForecastPreference(setOf(MarineTilesKey)) { readMarine(it) }
    val saved=rememberOverviewTileOrder()
    // Drag state: a working copy of the order exists only while a handle is being dragged (same pattern as saved locations).
    var working by remember { mutableStateOf<List<String>?>(null) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    // The distance between two neighbouring cells of the grid in pixels, across and down; set when the grid is laid out.
    val pitch=remember { FloatArray(2) }
    fun persist(list:List<String>) { p.edit().putString(OrderKey,JSONArray(list).toString()).apply() }
    fun move(tileKey:String,delta:Int) {
        val current=readOrder(p).toMutableList()
        val i=current.indexOf(tileKey);val j=i+delta
        if(i<0 || j !in current.indices) return
        Collections.swap(current,i,j);persist(current)
    }
    // Whole cells a drag has passed. A cell is left only after 0.6 of the way to the next one, so that it does not jump back and forth at the half.
    fun steps(distance:Float,size:Float):Int = if(size<=0f || abs(distance)<=size*0.6f) 0 else (distance/size).roundToInt()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(uiText(R.string.ui_dlazdice_obrazovky_teraz_8ac7b973),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        SettingsInfo(uiText(R.string.tile_order_hint_v2))
    }
    val order=working ?: saved
    // Two columns in the order of the list, as the tiles are paired on Now. All cells have one parent, so a dragged cell
    // keeps its gesture while it moves to another row.
    Layout(content={
        order.forEachIndexed { index,tileKey ->
            key(tileKey) {
                val marineTile=MarineOverviewTileId.entries.firstOrNull { it.orderKey==tileKey }
                val weatherTile=OverviewTileId.entries.firstOrNull { it.key==tileKey }
                val label=marineTile?.label ?: weatherTile?.label ?: tileKey
                val checked=if(marineTile!=null) marineTile.key in marine else weatherTile!=null && weatherTile in selected
                val dragging=working!=null && index==dragIndex
                TileOrderCell(label,checked,dragging,if(dragging) dragX else 0f,if(dragging) dragY else 0f,
                    onToggle={ on ->
                        if(marineTile!=null) {
                            val next=readMarine(p).toMutableSet();if(on) next.add(marineTile.key) else next.remove(marineTile.key)
                            p.edit().putStringSet(MarineTilesKey,next).apply()
                        } else if(weatherTile!=null) {
                            val next=readTiles(p).toMutableSet();if(on) next.add(weatherTile) else next.remove(weatherTile)
                            p.edit().putStringSet(TileKey,next.map { it.key }.toSet()).apply()
                        }
                    },
                    onMoveUp={ move(tileKey,-1) },onMoveDown={ move(tileKey,1) },
                    handle=Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart={ val base=readOrder(p);working=base;dragIndex=base.indexOf(tileKey);dragX=0f;dragY=0f },
                            onDragEnd={ working?.let { persist(it) };working=null;dragIndex=-1;dragX=0f;dragY=0f },
                            onDragCancel={ working=null;dragIndex=-1;dragX=0f;dragY=0f },
                            onDrag={ change,amount ->
                                change.consume()
                                val current=working
                                val at=dragIndex
                                if(current!=null && at in current.indices) {
                                    dragX+=amount.x;dragY+=amount.y
                                    val column=(at%2+steps(dragX,pitch[0])).coerceIn(0,1)
                                    val row=(at/2+steps(dragY,pitch[1])).coerceIn(0,current.lastIndex/2)
                                    val target=(row*2+column).coerceAtMost(current.lastIndex)
                                    if(target!=at) {
                                        // The tile is taken out and put in at the new place; the tiles between move by one.
                                        val list=current.toMutableList()
                                        val moved=list.removeAt(at)
                                        list.add(target,moved)
                                        dragX-=(target%2-at%2)*pitch[0];dragY-=(target/2-at/2)*pitch[1]
                                        dragIndex=target;working=list
                                    }
                                }
                            },
                        )
                    })
            }
        }
    },modifier=Modifier.fillMaxWidth()) { measurables,constraints ->
        val gap=8.dp.roundToPx()
        val cell=((constraints.maxWidth-gap)/2).coerceAtLeast(0)
        val height=measurables.maxOfOrNull { it.maxIntrinsicHeight(cell) } ?: 0
        val placeables=measurables.map { it.measure(Constraints.fixed(cell,height)) }
        pitch[0]=(cell+gap).toFloat();pitch[1]=(height+gap).toFloat()
        val rows=(placeables.size+1)/2
        layout(constraints.maxWidth,rows*height+(rows-1).coerceAtLeast(0)*gap) {
            placeables.forEachIndexed { i,placeable -> placeable.place((i%2)*(cell+gap),(i/2)*(height+gap)) }
        }
    }
    OutlinedButton(onClick={ p.edit().remove(TileKey).remove(OutlookKey).remove(MarineTilesKey).remove(OrderKey).apply() },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.tile_order_reset)) }
}
@Composable private fun TileOrderCell(label:String,checked:Boolean,dragging:Boolean,dragX:Float,dragY:Float,onToggle:(Boolean)->Unit,onMoveUp:()->Unit,onMoveDown:()->Unit,handle:Modifier) {
    val shape=RoundedCornerShape(14.dp)
    val up=uiText(R.string.ui_posunut_vyssie_f4e2d086);val down=uiText(R.string.ui_posunut_nizsie_ee019ae9)
    Row(
        Modifier.zIndex(if(dragging) 1f else 0f).graphicsLayer { translationX=dragX;translationY=dragY }
            .semantics { customActions=listOf(CustomAccessibilityAction(up) { onMoveUp();true },CustomAccessibilityAction(down) { onMoveDown();true }) }
            .clip(shape).background(if(dragging) Color(0xFF283241) else Color(0xFF1A1D24)).border(1.dp,if(dragging) Color(0xFF4FC3F7) else Color(0xFF414958),shape),
        verticalAlignment=Alignment.CenterVertically,
    ) {
        Box(handle.width(40.dp).heightIn(min=52.dp),contentAlignment=Alignment.Center) { Icon(PerunIcons.Drag,uiText(R.string.tile_drag_handle),tint=ForecastMuted) }
        Row(Modifier.weight(1f).heightIn(min=52.dp).toggleable(value=checked,role=Role.Checkbox,onValueChange=onToggle).padding(end=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface,fontSize=14.sp,lineHeight=17.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            Checkbox(checked=checked,onCheckedChange=null)
        }
    }
}
