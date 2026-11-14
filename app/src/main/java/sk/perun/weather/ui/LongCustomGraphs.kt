package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class LongCustomGraph(val id:String,val title:String,val metrics:List<LongMetric>,val visible:Boolean=true)
private const val CustomKey="customGraphsV1"
private fun readLongCustomGraphs(p:SharedPreferences):List<LongCustomGraph> = runCatching {
    val array=JSONArray(p.getString(CustomKey,"[]") ?: "[]")
    (0 until array.length()).mapNotNull { i->
        val o=array.optJSONObject(i) ?: return@mapNotNull null
        val id=o.optString("id");val title=o.optString("title").trim();val m=o.optJSONArray("metrics") ?: return@mapNotNull null
        val metrics=(0 until m.length()).mapNotNull { k->LongMetric.entries.firstOrNull { it.name==m.optString(k) } }.distinct()
        if(id.isBlank() || title.isBlank() || metrics.size !in 1..4) null else LongCustomGraph(id,title.take(40),metrics,o.optBoolean("visible",true))
    }.distinctBy { it.id }.take(3)
}.getOrDefault(emptyList())
private fun writeLongCustomGraphs(p:SharedPreferences,graphs:List<LongCustomGraph>) {
    require(graphs.size<=3 && graphs.map { it.id }.distinct().size==graphs.size)
    require(graphs.all { it.title.isNotBlank() && it.metrics.size in 1..4 && it.metrics.distinct().size==it.metrics.size })
    val array=JSONArray()
    graphs.forEach { g->array.put(JSONObject().put("id",g.id).put("title",g.title.trim().take(40)).put("visible",g.visible).put("metrics",JSONArray(g.metrics.map { it.name }))) }
    p.edit().putString(CustomKey,array.toString()).apply()
}
@Composable internal fun rememberLongCustomGraphs():List<LongCustomGraph> {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("perun_long_forecast",Context.MODE_PRIVATE) }
    var graphs by remember(prefs) { mutableStateOf(readLongCustomGraphs(prefs)) }
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,key->if(key==CustomKey) graphs=readLongCustomGraphs(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        graphs=readLongCustomGraphs(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return graphs
}
@Composable internal fun LongCustomControls(graphs:List<LongCustomGraph>) {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("perun_long_forecast",Context.MODE_PRIVATE) }
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LongCustomGraph?>(null) }
    var deleting by remember { mutableStateOf<LongCustomGraph?>(null) }
    val pill=RoundedCornerShape(50)
    Row(Modifier.height(48.dp).clip(pill).background(Color(0xFF202833)).border(1.dp,Color(0xFF4B586C),pill).clickable(role=Role.Button,onClickLabel=uiText(R.string.ui_vlastne_grafy_22c14bc5),onClick={open=true}).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(PerunIcons.Settings,contentDescription=null,tint=Color(0xFFE8EAF0),modifier=Modifier.size(20.dp))
        Text(uiText(R.string.ui_vlastne_value_3_2f94d9c4, graphs.size),color=Color(0xFFE8EAF0),fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false)
    }
    if(open) AlertDialog(onDismissRequest={open=false},containerColor=Color(0xFF202833),titleContentColor=Color(0xFFE8EAF0),textContentColor=ForecastMuted,
        title={Text(uiText(R.string.ui_vlastne_grafy_22c14bc5))},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(uiText(R.string.ui_najviac_3_grafy_kazdy_s_1_az_4_premennymi_prepinac_urcuje_b037d8ed))
            graphs.forEach { g->Column {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(g.title,Modifier.weight(1f))
                    Switch(checked=g.visible,onCheckedChange={ visible->writeLongCustomGraphs(prefs,readLongCustomGraphs(prefs).map { if(it.id==g.id) it.copy(visible=visible) else it }) })
                }
                Text(g.metrics.joinToString(" · ") { it.label })
                Row {
                    TextButton(onClick={editing=g}) {Text(uiText(R.string.ui_upravit_0e8a3f5b))}
                    TextButton(onClick={deleting=g}) {Text(uiText(R.string.ui_odstranit_d24effb5))}
                }
            } }
            TextButton(enabled=graphs.size<3,onClick={editing=LongCustomGraph(UUID.randomUUID().toString(),uiText(R.string.ui_moj_graf_8d99f519),listOf(LongMetric.Temp))}) {Text(uiText(R.string.ui_pridat_graf_96f50f22))}
        }},confirmButton={TextButton(onClick={open=false}) {Text(uiText(R.string.ui_zavriet_7c32fd0b))}})
    deleting?.let { g->AlertDialog(onDismissRequest={deleting=null},title={Text(uiText(R.string.ui_odstranit_graf_76f7f441))},text={Text(g.title)},
        confirmButton={TextButton(onClick={writeLongCustomGraphs(prefs,readLongCustomGraphs(prefs).filterNot { it.id==g.id });deleting=null}) {Text(uiText(R.string.ui_odstranit_d24effb5))}},
        dismissButton={TextButton(onClick={deleting=null}) {Text(uiText(R.string.ui_zrusit_520e257a))}}) }
    editing?.let { g->key(g.id) {
        var title by remember {mutableStateOf(g.title)}
        var metrics by remember {mutableStateOf(g.metrics)}
        AlertDialog(onDismissRequest={editing=null},containerColor=Color(0xFF202833),titleContentColor=Color(0xFFE8EAF0),textContentColor=ForecastMuted,
            title={Text(uiText(R.string.ui_upravit_vlastny_graf_d4e9b317))},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value=title,onValueChange={title=it.take(40)},label={Text(uiText(R.string.ui_nazov_grafu_c2727703))},singleLine=true)
                Text(uiText(R.string.ui_premenne_value_4_a87d9828, metrics.size))
                LongMetric.entries.forEach { m->Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=m in metrics,enabled=m in metrics || metrics.size<4,onCheckedChange={ checked->metrics=if(checked) (metrics+m).distinct().take(4) else metrics-m })
                    Text(m.label)
                } }
            }},confirmButton={TextButton(enabled=title.isNotBlank() && metrics.size in 1..4,onClick={
                val current=readLongCustomGraphs(prefs)
                val updated=g.copy(title=title.trim(),metrics=metrics)
                if(current.any {it.id==g.id}) writeLongCustomGraphs(prefs,current.map {if(it.id==g.id) updated else it})
                else if(current.size<3) writeLongCustomGraphs(prefs,current+updated)
                editing=null
            }) {Text(uiText(R.string.ui_ulozit_bf7cd927))}},dismissButton={TextButton(onClick={editing=null}) {Text(uiText(R.string.ui_zrusit_520e257a))}})
    } }
}
