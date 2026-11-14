package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.*

private val SeaPanel=Color(0xFF202833)
private val SeaEdge=Color(0xFF4B586C)
private val SeaText=Color(0xFFE8EAF0)
private val SeaBlue=Color(0xFF4FC3F7)
private val SeaOrange=Color(0xFFFFA45B)
private enum class SeaMetric(val key:String,val title:String,val color:Color,val tip:String) {
    Temperature("sea_surface_temperature",uiText(R.string.ui_teplota_mora_0e2349bf),Color(0xFFFFB74D),uiText(R.string.ui_modelova_teplota_povrchovej_vrstvy_mora_7d6ec8aa)),
    Level("sea_level_height_msl",uiText(R.string.ui_hladina_mora_a4623230),Color(0xFFBA9EDB),uiText(R.string.ui_modelova_hladina_voci_strednej_hladine_mora_msl_21f4a62d)),
    Waves("wave_height",uiText(R.string.ui_vyska_vln_4b75e096),SeaBlue,uiText(R.string.ui_vyznamna_vyska_vln_sipky_ukazuju_kam_sa_vlny_siria_cba86ed0)),
    Period("wave_period",uiText(R.string.ui_perioda_vln_47347236),Color(0xFF66D17A),uiText(R.string.ui_casovy_interval_medzi_vlnami_v_sekundach_eea8e9f8))
}
private fun seaUnit(m:SeaMetric,u:WeatherUnits)=when(m) {SeaMetric.Temperature->u.temperature.symbol;SeaMetric.Period->"s";else->"m"}
private fun seaValue(m:SeaMetric,v:Double,u:WeatherUnits)=if(m==SeaMetric.Temperature) u.temperature.fromCelsius(v) else v
private fun seaNumber(v:Double)=weatherUnitNumber(v,1)
private data class SeaCustom(val id:String,val title:String,val metrics:List<SeaMetric>,val visible:Boolean=true)
private fun seaRead(raw:String):List<SeaCustom> = runCatching {
    val a=JSONArray(raw)
    (0 until a.length()).mapNotNull {i->val o=a.optJSONObject(i) ?: return@mapNotNull null;val ma=o.optJSONArray("metrics") ?: return@mapNotNull null
        val ms=(0 until ma.length()).mapNotNull {k->SeaMetric.entries.firstOrNull {it.name==ma.optString(k)}}.distinct()
        val id=o.optString("id");val title=o.optString("title").trim().take(40)
        if(id.isBlank() || title.isBlank() || ms.size !in 1..4) null else SeaCustom(id,title,ms,o.optBoolean("visible",true))
    }.distinctBy {it.id}.take(3)
}.getOrDefault(emptyList())
private fun seaEncode(graphs:List<SeaCustom>):String {
    require(graphs.size<=3 && graphs.map {it.id}.distinct().size==graphs.size)
    val a=JSONArray();graphs.forEach {g->require(g.title.isNotBlank() && g.metrics.size in 1..4);a.put(JSONObject().put("id",g.id).put("title",g.title).put("visible",g.visible).put("metrics",JSONArray(g.metrics.map {it.name})))}
    return a.toString()
}
@Composable private fun SeaGlyph(m:SeaMetric,color:Color) {
    when(m) {
        SeaMetric.Temperature->SummaryGlyph(SummaryIcon.Temp,color,22.dp)
        SeaMetric.Waves->Icon(PerunIcons.Wave,null,tint=color,modifier=Modifier.size(24.dp))
        SeaMetric.Period->Canvas(Modifier.size(22.dp)) {val c=center;val r=size.minDimension*.42f;drawCircle(color,r,c,style=Stroke(1.7.dp.toPx()));drawLine(color,c,c+Offset(0f,-r*.6f),1.7.dp.toPx(),StrokeCap.Round);drawLine(color,c,c+Offset(r*.45f,r*.25f),1.7.dp.toPx(),StrokeCap.Round)}
        SeaMetric.Level->Canvas(Modifier.size(24.dp)) {val p=Path().apply {moveTo(size.width*.15f,size.height*.65f);cubicTo(size.width*.4f,size.height*.35f,size.width*.6f,size.height*.95f,size.width*.85f,size.height*.65f)};drawPath(p,color,style=Stroke(1.7.dp.toPx()));drawLine(color,Offset(size.width*.5f,size.height*.15f),Offset(size.width*.5f,size.height*.45f),1.7.dp.toPx(),StrokeCap.Round);drawLine(color,Offset(size.width*.35f,size.height*.3f),Offset(size.width*.5f,size.height*.15f),1.7.dp.toPx());drawLine(color,Offset(size.width*.65f,size.height*.3f),Offset(size.width*.5f,size.height*.15f),1.7.dp.toPx())}
    }
}
@Composable private fun SeaCustomControls(graphs:List<SeaCustom>,save:(List<SeaCustom>)->Unit) {
    var open by remember {mutableStateOf(false)};var edit by remember {mutableStateOf<SeaCustom?>(null)};var deleting by remember {mutableStateOf<SeaCustom?>(null)}
    val shape=RoundedCornerShape(50)
    Row(Modifier.height(48.dp).clip(shape).background(SeaPanel).border(1.dp,SeaEdge,shape).clickable(role=Role.Button,onClick={open=true}).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Icon(PerunIcons.Settings,null,tint=SeaText,modifier=Modifier.size(20.dp));Text(uiText(R.string.ui_vlastne_value_3_2f94d9c4, graphs.size),color=SeaText,fontSize=13.sp,fontWeight=FontWeight.SemiBold)}
    if(open) AlertDialog(onDismissRequest={open=false},title={Text(uiText(R.string.ui_vlastne_morske_grafy_6a19042b))},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(uiText(R.string.ui_najviac_tri_grafy_kazdy_s_jednou_az_styrmi_velicinami_8d7b0051))
        graphs.forEach {g->Column {Row(verticalAlignment=Alignment.CenterVertically) {Text(g.title,Modifier.weight(1f));Switch(g.visible,{on->save(graphs.map {if(it.id==g.id) it.copy(visible=on) else it})})};Text(g.metrics.joinToString(" · ") {it.title},fontSize=12.sp);Row {TextButton(onClick={edit=g}) {Text(uiText(R.string.ui_upravit_0e8a3f5b))};TextButton(onClick={deleting=g}) {Text(uiText(R.string.ui_odstranit_d24effb5))}}}}
        TextButton(enabled=graphs.size<3,onClick={edit=SeaCustom(UUID.randomUUID().toString(),uiText(R.string.ui_moj_morsky_graf_38d619b9),listOf(SeaMetric.Temperature))}) {Text(uiText(R.string.ui_pridat_graf_96f50f22))}
    }},confirmButton={TextButton(onClick={open=false}) {Text(uiText(R.string.ui_zavriet_7c32fd0b))}})
    deleting?.let {g->AlertDialog(onDismissRequest={deleting=null},title={Text(uiText(R.string.ui_odstranit_graf_76f7f441))},text={Text(g.title)},confirmButton={TextButton(onClick={save(graphs.filterNot {it.id==g.id});deleting=null}) {Text(uiText(R.string.ui_odstranit_d24effb5))}},dismissButton={TextButton(onClick={deleting=null}) {Text(uiText(R.string.ui_zrusit_520e257a))}})}
    edit?.let {g->key(g.id) {
        var title by remember {mutableStateOf(g.title)};var ms by remember {mutableStateOf(g.metrics.toSet())}
        AlertDialog(onDismissRequest={edit=null},title={Text(uiText(R.string.ui_upravit_morsky_graf_b8568add))},text={Column(Modifier.verticalScroll(rememberScrollState())) {OutlinedTextField(title,{title=it.take(40)},label={Text(uiText(R.string.ui_nazov_f7f0ac88))},singleLine=true);SeaMetric.entries.forEach {m->Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(m in ms,{on->ms=if(on) ms+m else ms-m});Text(m.title)}}}},confirmButton={TextButton(enabled=title.isNotBlank() && ms.isNotEmpty(),onClick={val v=g.copy(title=title.trim(),metrics=SeaMetric.entries.filter {it in ms});if(graphs.any {it.id==g.id}) save(graphs.map {if(it.id==g.id) v else it}) else if(graphs.size<3) save(graphs+v);edit=null}) {Text(uiText(R.string.ui_ulozit_bf7cd927))}},dismissButton={TextButton(onClick={edit=null}) {Text(uiText(R.string.ui_zrusit_520e257a))}})
    }}
}
@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable private fun SeaInfo(ms:List<SeaMetric>,title:String,d:MarineData,modifier:Modifier) {
    var tip by remember {mutableStateOf(false)};var help by remember {mutableStateOf(false)}
    Box(modifier.size(28.dp).combinedClickable(role=Role.Button,onClickLabel=uiText(R.string.ui_strucny_popis_e8d7e5f7),onLongClickLabel=uiText(R.string.ui_pomocnik_c68b7b84),onClick={tip=true},onLongClick={help=true}).semantics {contentDescription=uiText(R.string.ui_informacie_bc63e043)+title},contentAlignment=Alignment.Center) {
        Text("ⓘ",color=ForecastMuted,fontSize=16.sp)
        if(tip) {LaunchedEffect(tip) {delay(2600);tip=false};DropdownMenu(expanded=tip,onDismissRequest={tip=false}) {Text(ms.joinToString(" ") {it.tip},Modifier.widthIn(max=240.dp).padding(10.dp),fontSize=13.sp)}}
    }
    if(help) ModalBottomSheet(onDismissRequest={help=false},containerColor=SeaPanel,contentColor=SeaText) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            ms.forEach {m->HelpText(m.tip);when(m) {
                SeaMetric.Temperature->HelpText(uiText(R.string.ui_povrchova_teplota_modelovej_bunky_nie_meranie_na_plazi_jed_dfd0e93b))
                SeaMetric.Waves->HelpText(uiText(R.string.ui_vyznamna_vyska_vln_opisuje_priemer_najvyssej_tretiny_vln_j_0f032819))
                SeaMetric.Period->HelpText(uiText(R.string.ui_perioda_je_v_sekundach_nie_rychlost_ani_vyska_vln_8376497d))
                SeaMetric.Level->{HelpText(uiText(R.string.ui_msl_je_stredna_hladina_mora_nie_miestna_namorna_nula_zapor_40fcb415));HelpRow(SeaBlue,uiText(R.string.ui_modelove_maximum_cf189a4c));HelpRow(SeaOrange,uiText(R.string.ui_modelove_minimum_97d29dc6));HelpText(uiText(R.string.ui_lokalne_extremy_hodinoveho_modelu_nie_garantovane_casy_pri_dc84a8ea))}
            }}
            HelpText(uiText(R.string.ui_modelova_bunka_value_value_vzdialenost_value_km_zdroj_open_1032d7ec, d.latitude, d.longitude, seaNumber(d.distance)))
            HelpText(uiText(R.string.ui_plynula_interpolacia_nemeni_hodinove_hodnoty_ani_polohu_ex_84f1971f))
            HelpText(uiText(R.string.ui_podrz_prst_v_grafe_na_odcitanie_cas_posuvas_tahom_na_nadpi_22830073))
            HelpText(uiText(R.string.ui_modelove_udaje_nie_su_urcene_na_navigaciu_ani_bezpecnostne_b26ce977))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MarineScreen(location:Location,onBack:()->Unit,targetMetric:String="") {
    val context=LocalContext.current;val store=remember(context) {context.getSharedPreferences("perun_marine",Context.MODE_PRIVATE)}
    val units=rememberForecastPreferences().units
    var enabled by remember(store) {mutableStateOf(store.getBoolean("enabled",false))}
    var metrics by remember(store) {mutableStateOf((store.getStringSet("visibleMetrics",null) ?: SeaMetric.entries.map {it.name}.toSet()).mapNotNull {n->SeaMetric.entries.firstOrNull {it.name==n}}.toSet())}
    var custom by remember(store) {mutableStateOf(seaRead(store.getString("customGraphsV1","[]") ?: "[]"))}
    // Opened from an overview tile: show only that graph (its button alone active) and scroll to it once.
    val target=remember(targetMetric) {SeaMetric.entries.firstOrNull {it.name==targetMetric}}
    val page=rememberScrollState();val scope=rememberCoroutineScope();var scrolled by remember(targetMetric) {mutableStateOf(false)}
    LaunchedEffect(target) {if(target!=null && metrics!=setOf(target)) {metrics=setOf(target);store.edit().putStringSet("visibleMetrics",metrics.map {it.name}.toSet()).apply()}}
    var data by remember(location) {mutableStateOf<MarineData?>(null)};var error by remember(location) {mutableStateOf<String?>(null)}
    var loading by remember(location) {mutableStateOf(false)};var reload by remember {mutableIntStateOf(0)}
    var shift by remember(location) {mutableDoubleStateOf(0.0)};var cursor by remember(location) {mutableStateOf<Long?>(null)}
    var now by remember {mutableLongStateOf(Instant.now().epochSecond)}
    LaunchedEffect(Unit) {while(true) {now=Instant.now().epochSecond;delay(30_000)}}
    LaunchedEffect(location,enabled,reload) {error=null;loading=enabled;if(!enabled) data=null else try {data=loadMarine(location,8)} catch(e:CancellationException) {throw e} catch(e:Exception) {error=e.message ?: uiText(R.string.ui_chyba_nacitania_62fc4ef6)} finally {loading=false}}
    PullToRefreshBox(isRefreshing=loading,onRefresh={if(enabled && !loading) {NetCache.invalidate();reload++}},modifier=Modifier.fillMaxSize().background(Color(0xFF323E4F))) {
        Column(Modifier.fillMaxSize().verticalScroll(page).padding(horizontal=12.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                SeaMetric.entries.forEach {m->val active=m in metrics;val shape=RoundedCornerShape(50);Box(Modifier.size(48.dp).clip(shape).background(if(active) SeaBlue else SeaPanel).border(1.dp,if(active) SeaBlue else SeaEdge,shape).toggleable(value=active,role=Role.Checkbox,onValueChange={on->metrics=if(on) metrics+m else metrics-m;store.edit().putStringSet("visibleMetrics",metrics.map {it.name}.toSet()).apply()}).semantics {contentDescription=m.title},contentAlignment=Alignment.Center) {SeaGlyph(m,if(active) Color(0xFF101F39) else SeaText)}}
                SeaCustomControls(custom) {graphs->store.edit().putString("customGraphsV1",seaEncode(graphs)).apply();custom=graphs}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text(uiText(R.string.ui_morske_udaje_931b71bd),Modifier.weight(1f),color=SeaText,fontSize=14.sp,fontWeight=FontWeight.Bold);PerunSegments(listOf(uiText(R.string.ui_vypnute_6eaf04b7),uiText(R.string.ui_zapnute_4b796e4a)),if(enabled) 1 else 0,{enabled=it==1;store.edit().putBoolean("enabled",enabled).apply()},compact=true);ChartNowButton(enabled=shift>0.0) {shift=0.0;cursor=null}}
            if(!enabled) Text(uiText(R.string.ui_zapni_morske_udaje_pre_zvolenu_lokalitu_pri_vypnuti_sa_mar_cd3f4af5),color=SeaText,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            error?.let {Surface(color=SeaPanel,shape=RoundedCornerShape(16.dp)) {Text(uiText(R.string.ui_nacitanie_zlyhalo_value_potiahni_nadol_pre_obnovenie_pripa_bf1bd2f6, it),Modifier.padding(12.dp),color=SeaOrange,fontSize=13.sp)}}
            data?.let {d->if(!d.relevant) Text(uiText(R.string.ui_pre_tuto_lokalitu_nie_su_dostupne_pouzitelne_morske_udaje_b05257f0),color=SeaText,fontSize=16.sp,fontWeight=FontWeight.SemiBold) else {
                val span=5L*86400;val maxShift=(d.times.last()+3600-d.times.first()-span).coerceAtLeast(0).toDouble();val start=d.times.first()+shift.coerceIn(0.0,maxShift).toLong();val move:(Double)->Unit={s->shift=(shift+s).coerceIn(0.0,maxShift)}
                SeaMetric.entries.filter {it in metrics}.forEach {m->key(m) {Box(Modifier.onGloballyPositioned {if(m==target && !scrolled) {scrolled=true;val y=it.positionInParent().y.roundToInt();scope.launch {page.animateScrollTo(y)}}}) {SeaChart(d,listOf(m),m.title,units,start,span,now,cursor,{cursor=it},move)}}}
                custom.filter {it.visible}.forEach {g->key(g.id) {SeaChart(d,g.metrics,g.title,units,start,span,now,cursor,{cursor=it},move)}}
                if(metrics.isEmpty() && custom.none {it.visible}) Surface(color=SeaPanel,shape=RoundedCornerShape(16.dp)) {Text(uiText(R.string.ui_vyber_aspon_jeden_graf_b3265ee1),Modifier.padding(12.dp),color=ForecastMuted)}
            }}
            AttributionFooter()
        }
    }
}
private fun seaCurve(points:List<Offset>):Path {
    val path=Path();if(points.isEmpty()) return path;path.moveTo(points.first().x,points.first().y);if(points.size<2) return path
    val delta=FloatArray(points.size-1) {i->(points[i+1].y-points[i].y)/(points[i+1].x-points[i].x)}
    val slope=FloatArray(points.size);slope[0]=delta[0];slope[slope.lastIndex]=delta.last()
    for(i in 1 until points.lastIndex) {val a=delta[i-1];val b=delta[i];slope[i]=if(a*b<=0f) 0f else 2f*a*b/(a+b)}
    for(i in delta.indices) {if(delta[i]==0f) {slope[i]=0f;slope[i+1]=0f} else {val a=slope[i]/delta[i];val b=slope[i+1]/delta[i];val length=sqrt(a*a+b*b);if(length>3f) {slope[i]=3f/length*a*delta[i];slope[i+1]=3f/length*b*delta[i]}}}
    for(i in delta.indices) {val a=points[i];val b=points[i+1];val h=(b.x-a.x)/3f;path.cubicTo(a.x+h,a.y+slope[i]*h,b.x-h,b.y-slope[i+1]*h,b.x,b.y)}
    return path
}
@Composable private fun SeaChart(d:MarineData,metrics:List<SeaMetric>,title:String,u:WeatherUnits,start:Long,span:Long,now:Long,cursor:Long?,select:(Long?)->Unit,move:(Double)->Unit) {
    val tm=rememberTextMeasurer();val density=LocalDensity.current;var hidden by remember(metrics) {mutableStateOf(emptySet<SeaMetric>())};val shown=metrics.filter {it !in hidden}
    val axes=shown.distinctBy {seaUnit(it,u)};val leftDp=if(axes.size>=3) 76.dp else 38.dp;val rightDp=38.dp
    var canvasWidth by remember {mutableFloatStateOf(800f)};val plotPixels=(canvasWidth-with(density) {(leftDp+rightDp).toPx()}).coerceAtLeast(1f)
    val currentMove by rememberUpdatedState(move);val currentStart by rememberUpdatedState(start);val scroll=rememberScrollableState {delta->currentMove(-delta.toDouble()*span/plotPixels);delta}
    val scales=axes.associate {axis->val group=shown.filter {seaUnit(it,u)==seaUnit(axis,u)};val v=group.flatMap {m->d.times.indices.filter {d.times[it] in start..start+span}.map {seaValue(m,d.at(m.key,it),u)}}.filter {it.isFinite()};val zero=group.all {it==SeaMetric.Waves || it==SeaMetric.Period};var lo=if(zero) 0.0 else v.minOrNull() ?: 0.0;var hi=v.maxOrNull() ?: lo+1;if(hi<=lo) hi=lo+1;val p=(hi-lo)*.1;hi+=p;if(!zero) lo-=p;seaUnit(axis,u) to (lo to hi)}
    val extremes=remember(d) {marineExtremes(d.values["sea_level_height_msl"].orEmpty())}
    val selected=cursor?.takeIf {it in start..start+span}?.let {t->d.times.indices.minByOrNull {abs(d.times[it]-t)}?.takeIf {abs(d.times[it]-t)<=1800}}
    Surface(color=SeaPanel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,SeaEdge)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=28.dp).scrollable(scroll,Orientation.Horizontal),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(title,Modifier.weight(1f),color=SeaText,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(metrics.size>1) Row(Modifier.weight(1.5f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {metrics.forEach {m->Row(Modifier.heightIn(min=28.dp).alpha(if(m in shown) 1f else .4f).toggleable(value=m in shown,role=Role.Checkbox,onValueChange={on->if(on) hidden=hidden-m else if(shown.size>1) hidden=hidden+m}),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {Canvas(Modifier.size(10.dp,8.dp)) {drawLine(m.color,Offset(0f,size.height/2),Offset(size.width,size.height/2),2.dp.toPx())};Text(m.title,color=m.color,fontSize=10.sp,maxLines=1,softWrap=false)}}}
            }
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(234.dp).onSizeChanged {canvasWidth=it.width.toFloat()}.semantics {contentDescription=title+uiText(R.string.ui_odcitanie_v_grafe_posun_na_nadpise_a_okrajoch_6ae1a3d9)}.pointerInput(d,span,leftDp) {
                    awaitEachGesture {val down=awaitFirstDown(requireUnconsumed=false);val left=leftDp.toPx();val right=(size.width-rightDp.toPx()).coerceAtLeast(left+1);val top=48.dp.toPx();val bottom=size.height-18.dp.toPx();val w=right-left
                        if(down.position.x in left..right && down.position.y in top..bottom) {fun choose(px:Float) {select(currentStart+(((px-left)/w).coerceIn(0f,1f)*span).toLong())};down.consume();choose(down.position.x);try {do {val e=awaitPointerEvent();val c=e.changes.firstOrNull {it.id==down.id} ?: break;if(!c.pressed) break;c.consume();choose(c.position.x)} while(true)} finally {select(null)}} else {var previous=down.position.x;val drag=awaitHorizontalTouchSlopOrCancellation(down.id) {c,over->currentMove(-over.toDouble()*span/w);previous=c.position.x;c.consume()};if(drag!=null) horizontalDrag(drag.id) {c->currentMove(-(c.position.x-previous).toDouble()*span/w);previous=c.position.x;c.consume()}}
                    }
                }) {
                    val left=leftDp.toPx();val right=(size.width-rightDp.toPx()).coerceAtLeast(left+1);val top=48.dp.toPx();val bottom=size.height-18.dp.toPx();val style=TextStyle(color=ForecastMuted,fontSize=9.sp)
                    fun x(t:Long)=left+((t-start).toDouble()/span*(right-left)).toFloat()
                    fun y(m:SeaMetric,v:Double):Float {val (lo,hi)=scales.getValue(seaUnit(m,u));return bottom-((v-lo)/(hi-lo)*(bottom-top)).toFloat()}
                    fun text(s:String,xx:Float,yy:Float,c:Color=ForecastMuted) {val r=tm.measure(s,style.copy(color=c));drawText(r,topLeft=Offset(xx.coerceIn(0f,(size.width-r.size.width).coerceAtLeast(0f)),yy))}
                    clipRect(left,top,right,bottom) {
                        for(k in 0..4) {val yy=top+(bottom-top)*k/4;drawLine(SeaEdge.copy(alpha=.5f),Offset(left,yy),Offset(right,yy),.5.dp.toPx())}
                        shown.forEach {m->val segment=ArrayList<Offset>();var prev:Long?=null
                            fun flush() {drawPath(seaCurve(segment),m.color,style=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round));segment.clear()}
                            d.times.indices.forEach {i->val v=seaValue(m,d.at(m.key,i),u);val t=d.times[i];if(!v.isFinite()) {flush();prev=null} else {if(prev!=null && t-prev!!!=3600L) flush();segment.add(Offset(x(t),y(m,v)));prev=t}};flush()
                            if(m==SeaMetric.Level) extremes.filter {d.times[it.index] in start..start+span}.forEach {e->val xx=x(d.times[e.index]);val yy=y(m,d.at(m.key,e.index));val r=4.dp.toPx();val sign=if(e.maximum) -1f else 1f;val triangle=Path().apply {moveTo(xx,yy+sign*r);lineTo(xx-r,yy-sign*r);lineTo(xx+r,yy-sign*r);close()};drawPath(triangle,if(e.maximum) SeaBlue else SeaOrange)}
                        }
                    }
                    axes.forEachIndexed {a,m->val xx=if(a%2==0) left-38.dp.toPx()*(a/2+1) else right+3.dp.toPx();val ax=if(a%2==0) left-38.dp.toPx()*(a/2) else right;text(seaUnit(m,u),xx,top-22.dp.toPx(),m.color);drawLine(m.color.copy(alpha=.7f),Offset(ax,top),Offset(ax,bottom),.7.dp.toPx());val (lo,hi)=scales.getValue(seaUnit(m,u));for(k in 0..4) text(seaNumber(hi-(hi-lo)*k/4),xx,top+(bottom-top)*k/4-5.dp.toPx(),m.color)}
                    var day=Instant.ofEpochSecond(start).atZone(d.zone).toLocalDate();val today=Instant.ofEpochSecond(now).atZone(d.zone).toLocalDate()
                    repeat(7) {val a=day.atStartOfDay(d.zone).toEpochSecond();val b=day.plusDays(1).atStartOfDay(d.zone).toEpochSecond();if(a<start+span && b>start) {val x0=x(a).coerceIn(left,right);val x1=x(b).coerceIn(left,right);drawLine(SeaEdge.copy(alpha=.5f),Offset(x0,top),Offset(x0,bottom),.7.dp.toPx());val name=when(day) {today->uiText(R.string.ui_dnes_ce1bed34);today.plusDays(1)->uiText(R.string.ui_zajtra_c3e86eb2);else->day.format(DateTimeFormatter.ofPattern("EEE",appLocale()))};val full=name+" · "+day.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()));val s=if(tm.measure(full,style).size.width<=x1-x0-2.dp.toPx()) full else day.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()));val r=tm.measure(s,style);if(r.size.width<=x1-x0) drawText(r,topLeft=Offset((x0+x1-r.size.width)/2,bottom+5.dp.toPx()))};day=day.plusDays(1)}
                    if(SeaMetric.Waves in shown) d.times.indices.filter {d.times[it] in start..start+span && Instant.ofEpochSecond(d.times[it]).atZone(d.zone).hour%12==0}.forEach {i->val b=marineTravelBearing(d.at("wave_direction",i));if(b.isFinite()) {val a=b*PI/180;val v=Offset(sin(a).toFloat(),-cos(a).toFloat());val c=Offset(x(d.times[i]),top-7.dp.toPx());val tip=c+v*5.dp.toPx();val side=Offset(-v.y,v.x)*2.dp.toPx();drawLine(SeaOrange,c-v*5.dp.toPx(),tip,1.dp.toPx());drawLine(SeaOrange,tip,tip-v*3.dp.toPx()+side,1.dp.toPx());drawLine(SeaOrange,tip,tip-v*3.dp.toPx()-side,1.dp.toPx())}}
                    fun marker(t:Long,c:Color,label:String,by:Float) {if(t in start..start+span) {val xx=x(t);val r=tm.measure(label,style.copy(color=c));val p=3.dp.toPx();val bx=(xx-r.size.width/2).coerceIn(left,(right-r.size.width).coerceAtLeast(left));drawLine(c,Offset(xx,top),Offset(xx,bottom),1.dp.toPx());drawRoundRect(Color(0xFF111A24),Offset(bx-p,by),Size(r.size.width+2*p,r.size.height+2*p),CornerRadius(3.dp.toPx()));drawText(r,topLeft=Offset(bx,by+p));drawLine(c,Offset(xx,by+r.size.height+2*p),Offset(xx,top),.7.dp.toPx())}}
                    marker(now,SeaText,uiText(R.string.ui_teraz_e6989b57)+Instant.ofEpochSecond(now).atZone(d.zone).format(DateTimeFormatter.ofPattern("HH:mm")),0f)
                    selected?.let {i->marker(d.times[i],SeaBlue,Instant.ofEpochSecond(d.times[i]).atZone(d.zone).format(DateTimeFormatter.ofPattern(appPattern("d. M. HH:mm"),appLocale())),20.dp.toPx());var nextY=top
                        shown.mapNotNull {m->val v=seaValue(m,d.at(m.key,i),u);if(v.isFinite()) Triple(m,v,y(m,v).coerceIn(top,bottom)) else null}.sortedBy {it.third}.forEach {(m,v,yy)->val r=tm.measure(seaNumber(v)+" "+seaUnit(m,u),style.copy(color=m.color));val p=3.dp.toPx();val w=r.size.width+2*p;val h=r.size.height+2*p;val xx=x(d.times[i]);val cx=(if(xx+6.dp.toPx()+w<=right) xx+6.dp.toPx() else xx-6.dp.toPx()-w).coerceIn(left,(right-w).coerceAtLeast(left));val cy=maxOf(yy-h/2,nextY).coerceAtMost((bottom-h).coerceAtLeast(top));nextY=cy+h+2.dp.toPx();drawCircle(m.color,3.dp.toPx(),Offset(xx,yy));drawRoundRect(Color(0xFF111A24),Offset(cx,cy),Size(w,h),CornerRadius(3.dp.toPx()));drawRoundRect(m.color,Offset(cx,cy),Size(w,h),CornerRadius(3.dp.toPx()),style=Stroke(1.dp.toPx()));drawText(r,topLeft=Offset(cx+p,cy+p))}
                    }
                    if(shown.all {m->d.times.indices.none {d.times[it] in start..start+span && d.at(m.key,it).isFinite()}}) text(uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61),left,top+30.dp.toPx())
                }
                SeaInfo(metrics,title,d,Modifier.align(Alignment.BottomEnd).offset(y=6.dp))
            }
        }
    }
}
