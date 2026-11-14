package sk.perun.weather.ui

import sk.perun.weather.R

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val NBg=Color(0xFF323E4F)
private val NEdge=Color(0xFF4B586C)
private val NPanel=Color(0xFF202833)
private val NDry=Color(0xFF2A3446)
private val NBftMin=listOf(0.0,1.1,5.8,12.2,19.8,28.8,38.9,50.0,61.9,74.9,88.2,102.6,117.7)
private val NBftNames=listOf(uiText(R.string.ui_bezvetrie_cd38ecb2),uiText(R.string.ui_vanok_565e5344),uiText(R.string.ui_slaby_vietor_96b40e12),uiText(R.string.ui_mierny_vietor_93ea48e7),uiText(R.string.ui_dost_cerstvy_vietor_aac5a22e),uiText(R.string.ui_cerstvy_vietor_a5ad3c92),uiText(R.string.ui_silny_vietor_2766694f),uiText(R.string.ui_prudky_vietor_03f07e8a),uiText(R.string.ui_burlivy_vietor_12ab2d33),uiText(R.string.ui_vichrica_c95b9839),uiText(R.string.ui_silna_vichrica_2d7e107a),uiText(R.string.ui_mohutna_vichrica_453ef247),uiText(R.string.ui_orkan_8f9a5a59))
private val NBftColors=listOf(0xFF6270B7,0xFF39619F,0xFF4994A9,0xFF4D8D7C,0xFF53A553,0xFF359E35,0xFFA69D50,0xFFA0645C,0xFFAF5088,0xFF754A92,0xFF45698D,0xFF5B9098,0xFF7D44A5).map {Color(it)}
private fun nBft(v:Double)=if(!v.isFinite()||v<0) null else NBftMin.indexOfLast {v>=it}
private fun nWind(v:Double)=nBft(v)?.let {NBftColors[it]}?:ForecastMuted
private fun nCloud(v:Double)=when { !v.isFinite()||v !in 0.0..100.0->"";v<12.5->uiText(R.string.ui_jasno_e78eee64);v<37.5->uiText(R.string.ui_prevazne_jasno_7331ba47);v<62.5->uiText(R.string.ui_polooblacno_83f2c354);v<87.5->uiText(R.string.ui_prevazne_oblacno_e5122573);else->uiText(R.string.ui_zamracene_829159bb) }
private fun nClock(t:Instant?,z:ZoneId)=t?.atZone(z)?.format(DateTimeFormatter.ofPattern("HH:mm"))?:"—"
private fun nJson(url:String)=JSONObject(String(fetchBytes(url),Charsets.UTF_8))
private suspend fun <T> nTry(block:suspend ()->T):T?=try {block()} catch(e:CancellationException){throw e} catch(e:Exception){null}
private fun nColor(r:Double)=when {r>=7.6->Color(0xFFEF5350);r>=2.5->Color(0xFFFFCA28);r>=0.5->Color(0xFF66BB6A);r>=0.1->Color(0xFFA5D6A7);else->NDry}
private fun nIntensity(r:Double)=when {r>=7.6->uiText(R.string.ui_silne_zrazky_208eb2db);r>=2.5->uiText(R.string.ui_mierne_zrazky_beedc2e1);r>=0.5->uiText(R.string.ui_slabe_zrazky_135b3ccf);r>=0.1->uiText(R.string.ui_velmi_slabe_zrazky_aa3f54f0);else->uiText(R.string.ui_bez_zrazok_2da018fe)}
private data class NP(val min:Int,val rate:Double,val dbz:Int?=null)
private data class NCell(val dx:Double,val dy:Double,val dbz:Double,val speed:Double,val heading:Double,val eta:Int?) {val dist get()=hypot(dx,dy);val bearing get()=(atan2(dx,dy)*180/PI+360)%360}
private data class NStrike(val dx:Double,val dy:Double) {val dist get()=hypot(dx,dy)}
private data class NStorm(val cells:List<NCell>?,val strikes:List<NStrike>?)
private data class NNow(val points:List<NP>,val source:String,val fallback:Boolean,val base:Instant,val storm:NStorm) {
    val wet get()=points.any {it.rate>=0.1}
    val peak get()=points.maxBy {it.rate}
    val headline:String get() {
        val first=points.firstOrNull {it.rate>=0.1}?:return uiText(R.string.ui_bez_zrazok_cf1104e5)
        if(points.first().rate<0.1) return uiText(R.string.ui_dazd_zacne_o_value_min_1d0cabf6, first.min)
        val dry=points.indices.firstOrNull {i->i>0&&points.drop(i).all {it.rate<0.1}}
        return dry?.let {uiText(R.string.ui_dazd_skonci_o_value_min_25bffcd9, points[it].min)}?:uiText(R.string.ui_dazd_potrva_aspon_hodinu_c0146efe)
    }
}
private const val NBase="https://api.librewxr.net"
private val NPalette="009f9f 009e9e 009d99 009a8c 009573 008c4b 008c4a 009047 009b40 00b031 00d319 00d319 02d519 07dc1a 10e81d 21fd22 22fd21 2ffd21 50fd20 92fd1e fffd1b fffc1a fffa19 fff415 ffe80d ffd400 ffd300 ffd100 ffcb00 ffbf00 ffab00 ffaa00 ffa700 ff9d00 ff8b00 ff6e00 fe6d00 fe6700 fb5900 f73d01 f01002 ef0f02 ed0f04 e90d09 df0a12 d00523 d00424 d1042c d40341 da026b e400b1 e400b1 e503b5 e90bc1 f11ad8 ff33ff ff33ff ff36ff ff3eff ff4dff ff66ff ff66ff ff69ff ff71ff ff80ff ff99ff ff99ff ff9aff ff9cff ffa1ff ffaaff".split(" ").map {it.toInt(16)}
private fun nDbz(pixel:Int):Int {
    if((pixel ushr 24)<128) return 0
    val r=(pixel ushr 16) and 255;val g=(pixel ushr 8) and 255;val b=pixel and 255
    var best=0;var distance=Int.MAX_VALUE
    NPalette.forEachIndexed {i,c->val dr=r-((c ushr 16) and 255);val dg=g-((c ushr 8) and 255);val db=b-(c and 255);val d=dr*dr+dg*dg+db*db;if(d<distance){distance=d;best=i}}
    return if(distance<=4800) 10+best else 0
}
private fun nSample(path:String,l:Location):Int {
    require(path.startsWith("/v2/")&&!path.contains("..")&&!path.contains('?'))
    val x=(l.longitude+180)/360*128;val rad=l.latitude.coerceIn(-85.05112878,85.05112878)*PI/180
    val y=(1-ln(tan(rad)+1/cos(rad))/PI)/2*128
    val tx=floor(x).toInt().coerceIn(0,127);val ty=floor(y).toInt().coerceIn(0,127)
    val px=floor((x-floor(x))*256).toInt().coerceIn(0,255);val py=floor((y-floor(y))*256).toInt().coerceIn(0,255)
    val bytes=fetchBytes("$NBase$path/256/7/$tx/$ty/7/0_0.png");val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Invalid radar tile")
    try {require(image.width==256&&image.height==256);var best=0
        for(a in max(0,px-1)..min(255,px+1)) for(b in max(0,py-1)..min(255,py+1)) best=max(best,nDbz(image.getPixel(a,b)))
        return best
    } finally {image.recycle()}
}
private suspend fun nRadar(l:Location):Pair<Instant,List<NP>> = coroutineScope {
    val radar=nJson("$NBase/public/weather-maps.json").getJSONObject("radar");val past=radar.getJSONArray("past");val future=radar.getJSONArray("nowcast")
    check(past.length()>0&&future.length()>0);val last=past.getJSONObject(past.length()-1);val base=last.getLong("time")
    val frames=listOf(last)+(0 until min(6,future.length())).map {future.getJSONObject(it)}
    val points=frames.map {f->async {val d=nSample(f.getString("path"),l);NP(max(0,((f.getLong("time")-base)/600.0).roundToInt()*10),if(d>=10) (10.0.pow(d/10.0)/200).pow(1/1.6) else 0.0,d)}}.awaitAll().sortedBy {it.min}
    check(points.last().min>=60) {"Incomplete radar horizon"};Instant.ofEpochSecond(base) to points
}
private fun nModel(l:Location):Pair<Instant,List<NP>> {
    val m=nJson("https://api.open-meteo.com/v1/forecast?latitude=${l.latitude}&longitude=${l.longitude}&minutely_15=precipitation&forecast_minutely_15=8&past_minutely_15=1&timeformat=unixtime&timezone=GMT").getJSONObject("minutely_15")
    val times=m.getJSONArray("time");val values=m.getJSONArray("precipitation");val base=Instant.now()
    return base to (0..60 step 10).map {min->val stamp=base.epochSecond+min*60;val i=(0 until times.length()).firstOrNull {stamp>times.getLong(it)-900&&stamp<=times.getLong(it)}?:error("Missing nowcast interval")
        val amount=values.optDouble(i,Double.NaN);check(amount.isFinite()&&amount>=0);NP(min,amount*4)}
}
private fun nXY(l:Location,c:org.json.JSONArray):Pair<Double,Double>? {val lon=c.optDouble(0,Double.NaN);val lat=c.optDouble(1,Double.NaN);return if(lon.isFinite()&&lat.isFinite()) (lon-l.longitude)*111.32*cos(l.latitude*PI/180) to (lat-l.latitude)*110.57 else null}
private suspend fun nStorm(l:Location):NStorm=coroutineScope {
    val q="lat=${l.latitude}&lon=${l.longitude}&radius_km=100"
    val cells=async {nTry {val a=nJson("$NBase/v2/storm-cells?$q").getJSONArray("features");(0 until a.length()).mapNotNull {i->
        val f=a.getJSONObject(i);val c=f.optJSONObject("geometry")?.optJSONArray("coordinates")?:return@mapNotNull null;val (dx,dy)=nXY(l,c)?:return@mapNotNull null;val p=f.optJSONObject("properties")?:JSONObject()
        val speed=p.optDouble("motion_speed_kmh",Double.NaN);val heading=p.optDouble("motion_heading_deg",Double.NaN)
        val eta=if(speed.isFinite()&&heading.isFinite()) (0..60 step 5).firstOrNull {t->hypot(dx+speed*sin(heading*PI/180)/60*t,dy+speed*cos(heading*PI/180)/60*t)<8} else null
        NCell(dx,dy,p.optDouble("max_dbz",Double.NaN),speed,heading,eta)
    }.sortedWith(compareBy<NCell> {it.eta?:1000000}.thenBy {it.dist})}}
    val strikes=async {nTry {val a=nJson("$NBase/v2/lightning?$q&limit=500").getJSONArray("features");(0 until a.length()).mapNotNull {i->val c=a.getJSONObject(i).optJSONObject("geometry")?.optJSONArray("coordinates")?:return@mapNotNull null;nXY(l,c)?.let {(dx,dy)->NStrike(dx,dy)}}}}
    NStorm(cells.await(),strikes.await())
}
private suspend fun nLoad(l:Location,selected:String):NNow=withContext(Dispatchers.IO) {coroutineScope {
    val storm=async {nStorm(l)};val first=nTry {if(selected=="radar") nRadar(l) else nModel(l)}
    val actual=if(first!=null) selected else if(selected=="radar") "openmeteo" else "radar";val result=first?:if(actual=="radar") nRadar(l) else nModel(l)
    NNow(result.second,actual,first==null,result.first,storm.await())
}}
private data class NF(val label:String,val sub:String,val code:Int,val temp:Double,val low:Double,val rain:Double,val snow:Double,val prob:Double,val wind:Double,val gust:Double,val direction:Double,val cloud:Double,val cloudMax:Double,val uv:Double,val aqi:Double)
private suspend fun nHours(l:Location,z:ZoneId):List<NF> = withContext(Dispatchers.IO) {coroutineScope {
    val q="latitude=${l.latitude}&longitude=${l.longitude}&timeformat=unixtime&timezone=GMT&forecast_days=3"
    val air=async {nTry {nJson("https://air-quality-api.open-meteo.com/v1/air-quality?$q&hourly=european_aqi").getJSONObject("hourly")}}
    val h=nJson("https://api.open-meteo.com/v1/forecast?$q&wind_speed_unit=kmh&precipitation_unit=mm&hourly=temperature_2m,weather_code,precipitation,precipitation_probability,wind_speed_10m,wind_gusts_10m,wind_direction_10m,cloud_cover,uv_index").getJSONObject("hourly")
    val a=air.await();val aq=mutableMapOf<Long,Double>();a?.optJSONArray("time")?.let {t->for(i in 0 until t.length()) aq[t.getLong(i)]=a.optJSONArray("european_aqi")?.optDouble(i,Double.NaN)?:Double.NaN}
    val t=h.getJSONArray("time")
    fun n(k:String,i:Int)=h.optJSONArray(k)?.optDouble(i,Double.NaN)?:Double.NaN
    val now=Instant.now().epochSecond
    (0 until t.length()).filter {t.getLong(it)>=now}.take(24).map {i->val at=Instant.ofEpochSecond(t.getLong(i));NF(nClock(at,z),at.atZone(z).format(DateTimeFormatter.ofPattern(appPattern("d.M."),appLocale())),n("weather_code",i).let {if(it.isFinite()) it.toInt() else -1},n("temperature_2m",i),Double.NaN,n("precipitation",i),Double.NaN,n("precipitation_probability",i),n("wind_speed_10m",i),n("wind_gusts_10m",i),n("wind_direction_10m",i),n("cloud_cover",i),Double.NaN,n("uv_index",i),aq[t.getLong(i)]?:Double.NaN)}
}}
private fun nPhase(date:Instant):String {
    val t=(date.toEpochMilli()/86400000.0+2440587.5-2451545)/36525
    fun norm(x:Double)=((x%360)+360)%360
    val lp=norm(218.3164477+481267.88123421*t);val d=norm(297.8501921+445267.1114034*t)*PI/180;val m=norm(357.5291092+35999.0502909*t)*PI/180;val p=norm(134.9633964+477198.8675055*t)*PI/180
    val lon=lp+6.289*sin(p)-1.274*sin(2*d-p)+.658*sin(2*d)-.186*sin(m)-.059*sin(2*p-2*d)-.057*sin(p-2*d+m)+.053*sin(p+2*d)+.046*sin(2*d-m)+.041*sin(p-m)-.035*sin(d)-.031*sin(p+m)
    val index=(norm(lon-norm(280.4665+36000.7698*t))/45).roundToInt()%8
    return listOf(uiText(R.string.ui_nov_3e630d29),uiText(R.string.ui_dorastajuci_kosacik_8e90f090),uiText(R.string.ui_prva_stvrt_4ecd8721),uiText(R.string.ui_dorastajuci_mesiac_0d9af424),uiText(R.string.ui_spln_cc87a236),uiText(R.string.ui_cuvajuci_mesiac_4a6a004c),uiText(R.string.ui_posledna_stvrt_7a12e0f9),uiText(R.string.ui_cuvajuci_kosacik_03e67d5f))[index]
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NCard(title:String,modifier:Modifier=Modifier,dot:Color=ForecastMuted,help:String=uiText(R.string.ui_modelove_udaje_pre_zvolenu_lokalitu_nie_meranie_zo_stanice_6895cfe0),content:@Composable ColumnScope.()->Unit) {
    var open by remember {mutableStateOf(false)}
    if(open) ModalBottomSheet(onDismissRequest={open=false}) {Column(Modifier.padding(24.dp)) {Text(title,style=MaterialTheme.typography.titleLarge);Text(help);TextButton({open=false}) {Text(uiText(R.string.ui_zavriet_7c32fd0b))}}}
    OutlinedCard(modifier,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,NEdge),colors=CardDefaults.outlinedCardColors(containerColor=NPanel)) {Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Row(verticalAlignment=Alignment.CenterVertically) {InfoDot(dot,title) {open=true};Text(title,fontWeight=FontWeight.Bold,fontSize=14.sp)};content()}}
}
@Composable private fun NS(text:String,color:Color=ForecastMuted)=Text(text,fontSize=13.sp,color=color)
@Composable private fun NV(v:Double,unit:String="",color:Color=Color.Unspecified)=Text(overviewNumber(v,unit),fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=color)
@Composable private fun NBar(v:Double,maxValue:Double,color:Color) {Canvas(Modifier.fillMaxWidth().height(6.dp)) {drawRect(NEdge);if(v.isFinite()&&v>0) drawRect(color,size=Size((size.width*(v/maxValue).coerceIn(0.0,1.0)).toFloat(),size.height))}}
@Composable private fun NWindBar(wind:Double,gust:Double,maxValue:Double) {Canvas(Modifier.fillMaxWidth().height(6.dp)) {
    drawRect(NEdge);val w=if(wind.isFinite()) max(0.0,wind) else 0.0;val g=if(gust.isFinite()) max(0.0,gust) else 0.0;val top=max(w,g)
    if(top>0) {val total=(top/maxValue).coerceIn(.06,1.0)*size.width;val split=w/top*total;if(split>0) drawRect(nWind(wind),size=Size(split.toFloat(),size.height));if(total>split) drawRect(nWind(gust),Offset(split.toFloat(),0f),Size((total-split).toFloat(),size.height))}
}}
@Composable private fun NScale(n:NNow) {Row(Modifier.fillMaxWidth().semantics {contentDescription=n.points.joinToString {uiText(R.string.ui_value_min_value_246677fa, it.min, nIntensity(it.rate))}},horizontalArrangement=Arrangement.spacedBy(3.dp)) {n.points.forEach {Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(3.dp)).background(nColor(it.rate)))}}}
@Composable private fun NNowCard(n:NNow,selected:String,onSource:(String)->Unit,z:ZoneId) {
    NCard(uiText(R.string.ui_nowcast_zrazok_60_min_24463d15),Modifier.fillMaxWidth(),nColor(n.peak.rate),uiText(R.string.ui_librewxr_orientacny_odhad_intenzity_z_radarovej_odrazivost_c3457efe)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("radar" to uiText(R.string.ui_radar_524f8327),"openmeteo" to "Open-Meteo").forEach {(id,label)->FilterChip(selected==id,{onSource(id)},{Text(label)})}}
        Text(n.headline,fontSize=23.sp,fontWeight=FontWeight.Bold);NS(uiText(R.string.ui_najsilnejsie_value_61d58ca0, nIntensity(n.peak.rate)))
        Row(Modifier.fillMaxWidth().height(76.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom) {n.points.forEach {p->Box(Modifier.weight(1f).height((if(p.rate>=.1) max(9.0,min(72.0,sqrt(p.rate/10)*72)) else 4.0).dp).background(nColor(p.rate),RoundedCornerShape(3.dp)))}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {n.points.forEach {NS(if(it.min==0) uiText(R.string.ui_teraz_6172392c) else "+${it.min}")}}
        NS(uiText(R.string.ui_najsilnejsie_o_value_valuevalue_7f222bf2, nClock(n.base.plusSeconds(n.peak.min*60L),z), overviewNumber(n.peak.rate,uiText(R.string.ui_mm_h_0c7ac40f),2), n.peak.dbz?.let {uiText(R.string.ui_value_dbz_be53f7a7, it)}?:""))
        NS(if(n.source=="radar") uiText(R.string.ui_librewxr_radar_a_nowcast_orientacny_odhad_nie_meranie_3f6df595) else uiText(R.string.ui_open_meteo_15_minutova_modelova_predpoved_ff7057a6))
        NS(uiText(R.string.ui_referencny_cas_value_b8abe88e, nClock(n.base,z))+(if(n.fallback) uiText(R.string.ui_zaloha_vybrany_zdroj_nebol_dostupny_58663d88) else ""))
    }
}
@Composable private fun NRadar(cells:List<NCell>,strikes:List<NStrike>,animate:Boolean) {
    val main=cells.firstOrNull();val shown=strikes.take(80);val maxD=max(main?.dist?:0.0,shown.maxOfOrNull {it.dist}?:0.0);val range=listOf(25.0,50.0,100.0).firstOrNull {maxD*1.2<=it}?:100.0
    val duration=main?.speed?.takeIf {it.isFinite()&&it>=3}?.let {(120/it).coerceIn(1.2,8.0)}?:8.0
    val transition=rememberInfiniteTransition(label="storm");val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween((duration*1000).toInt(),easing=LinearEasing)),label="cellMotion")
    Column(horizontalAlignment=Alignment.CenterHorizontally) {NS(uiText(R.string.ui_s_value_km_b12c6a08, range.toInt()));Canvas(Modifier.size(116.dp).semantics {contentDescription=uiText(R.string.ui_burkove_bunky_a_blesky_voci_lokalite_sever_je_hore_a2fbf923)}) {
        val center=Offset(size.width/2,size.height/2);val radius=size.width*.43f;val dash=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),3.dp.toPx()))
        fun xy(dx:Double,dy:Double)=Offset(center.x+(dx/range*radius).toFloat(),center.y-(dy/range*radius).toFloat())
        fun color(c:NCell)=if(c.dbz.isFinite()) Color(0xFF000000 or NPalette[(c.dbz.roundToInt()-10).coerceIn(0,NPalette.lastIndex)].toLong()) else ForecastMuted
        drawCircle(Color(0xFF141821),radius,center);drawCircle(NEdge,radius,center,style=Stroke(1.dp.toPx()));drawCircle(NEdge,radius/2,center,style=Stroke(1.dp.toPx(),pathEffect=dash));drawCircle(Color(0x55FFFFFF),(8/range*radius).toFloat(),center,style=Stroke(1.dp.toPx(),pathEffect=dash))
        cells.take(6).filter {it.dist<=range*1.05}.forEach {drawCircle(color(it),if(it===main) 5.dp.toPx() else 3.dp.toPx(),xy(it.dx,it.dy))}
        if(main!=null&&main.speed.isFinite()&&main.heading.isFinite()&&main.speed>=3) {var ex=main.dx+main.speed*.5*sin(main.heading*PI/180);var ey=main.dy+main.speed*.5*cos(main.heading*PI/180);val len=hypot(ex,ey);if(len>range*1.02){ex*=range*1.02/len;ey*=range*1.02/len};val a=xy(main.dx,main.dy);val b=xy(ex,ey);drawLine(ForecastMuted.copy(alpha=.35f),a,b,1.dp.toPx(),pathEffect=dash);if(animate) {val t=phase;drawCircle(color(main).copy(alpha=min(t/.15f,(1-t)/.25f).coerceIn(0f,1f)),3.6.dp.toPx(),Offset(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t))}}
        shown.filter {it.dist<=range*1.05}.forEach {s->val p=xy(s.dx,s.dy);val k=1.dp.toPx();val bolt=Path().apply {moveTo(p.x+.6f*k,p.y-4.5f*k);lineTo(p.x-2.6f*k,p.y+.8f*k);lineTo(p.x-.4f*k,p.y+.8f*k);lineTo(p.x-1.2f*k,p.y+4.5f*k);lineTo(p.x+2.6f*k,p.y-k);lineTo(p.x+.4f*k,p.y-k);close()};drawPath(bolt,Color(0xFFFFE14D))}
        val k=1.dp.toPx();val pin=Path().apply {moveTo(center.x,center.y);cubicTo(center.x-8*k,center.y-8*k,center.x-8*k,center.y-17*k,center.x,center.y-17*k);cubicTo(center.x+8*k,center.y-17*k,center.x+8*k,center.y-8*k,center.x,center.y);close()};drawPath(pin,Color(0xFFE53935));drawPath(pin,Color.White,style=Stroke(1.dp.toPx()));drawCircle(Color.White,2.5.dp.toPx(),Offset(center.x,center.y-11*k))
    }}
}
@Composable private fun NStormCard(s:NStorm,animate:Boolean) {NCard(uiText(R.string.ui_burky_v_okoli_60c0371d),Modifier.fillMaxWidth(),if((s.strikes?.count {it.dist<=25}?:0)>0) Color(0xFFFF7043) else Color(0xFFFFB74D),uiText(R.string.ui_bunky_a_blesky_z_librewxr_v_okoli_100_km_odhad_prichodu_zn_5d1bc5d5)) {
    val main=s.cells?.firstOrNull();Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        if(main!=null||!s.strikes.isNullOrEmpty()) NRadar(s.cells.orEmpty(),s.strikes.orEmpty(),animate)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(when {main!=null->uiText(R.string.ui_value_km_na_value_bf0f0787, main.dist.roundToInt(), cardinal(main.bearing));s.cells!=null->uiText(R.string.ui_ziadne_aa1757a8);else->"—"},fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            if(main?.eta!=null) NS(if(main.eta==0) uiText(R.string.ui_uz_je_pri_vas_f67eeb25) else uiText(R.string.ui_pri_vas_o_value_min_84402b5d, main.eta))
            if(s.cells==null) NS(uiText(R.string.ui_udaje_o_burkovych_bunkach_nie_su_dostupne_05ced842)) else if(s.cells.isEmpty()) NS(uiText(R.string.ui_v_okoli_100_km_sa_nenasla_burkova_bunka_ce84559e))
            NS(uiText(R.string.ui_blesky_25_km_value_69d5d7c7, s.strikes?.count {it.dist<=25}?:"—"))
        }
    };NS(uiText(R.string.ui_zdroj_librewxr_odhad_nie_oficialna_vystraha_3c713275))
}}
@Composable private fun NForecast(title:String,items:List<NF>,mode:OutlookMode,onMode:(OutlookMode)->Unit) {NCard(title,Modifier.fillMaxWidth(),mode.dot,uiText(R.string.ui_hodinovy_vyhlad_pouziva_jednotlive_hodinove_modelove_hodno_27561dcf)) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1E2835)).horizontalScroll(rememberScrollState()).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {OutlookMode.entries.forEach {m->FilterChip(mode==m,{onMode(m)},{Text(m.label)},shape=RoundedCornerShape(50))}}
    val temperatures=items.flatMap {listOf(it.temp,it.low)}.filter {it.isFinite()};val low=temperatures.minOrNull()?:0.0;val span=max(2.0,(temperatures.maxOrNull()?:2.0)-low);val maxGust=max(1.0,items.flatMap {listOf(it.wind,it.gust)}.filter {it.isFinite()}.maxOrNull()?:1.0)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {items.forEach {x->Column(Modifier.width(132.dp).border(1.dp,NEdge,RoundedCornerShape(14.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {Text(x.label,fontWeight=FontWeight.Bold);NS(x.sub)
        if(mode==OutlookMode.Wind&&x.direction.isFinite()) WindCompass(x.direction,nWind(x.wind),Modifier.size(48.dp)) else WeatherSymbol(x.code,48.dp)
        when(mode) {
            OutlookMode.Temp->{NV(x.temp,"°");if(x.low.isFinite()) NS(uiText(R.string.ui_min_value_e4167bc2, overviewNumber(x.low,"°")));Canvas(Modifier.fillMaxWidth().height(6.dp)) {drawRect(NEdge);if(x.temp.isFinite()) {val start=if(x.low.isFinite()) ((x.low-low)/span).coerceIn(0.0,1.0) else 0.0;val width=if(x.low.isFinite()) max(.02,(x.temp-x.low)/span) else ((x.temp-low)/span).coerceAtLeast(.02);drawRect(tempDotColor(x.temp),Offset((start*size.width).toFloat(),0f),Size((width.coerceAtMost(1-start)*size.width).toFloat(),size.height))}};NS(overviewCondition(x.code))}
            OutlookMode.Rain->{Text(overviewNumber(x.rain,uiText(R.string.ui_mm_c1752600),1),fontSize=23.sp,color=RainBlue);if(x.snow>0) NS(overviewNumber(x.snow,uiText(R.string.ui_cm_snehu_9f691427),1));NS(uiText(R.string.ui_pravd_value_81df4778, overviewNumber(x.prob," %")));NBar(x.rain,20.0,RainBlue)}
            OutlookMode.Wind->{NV(x.wind,uiText(R.string.ui_km_h_14c28063),nWind(x.wind));NS(uiText(R.string.ui_narazy_value_8270e71d, overviewNumber(x.gust,uiText(R.string.ui_km_h_14c28063))),nWind(x.gust));NWindBar(x.wind,x.gust,maxGust);NS(nBft(x.wind)?.let {NBftNames[it]}?:"—")}
            OutlookMode.Cloud->{NV(x.cloud," %");NBar(x.cloud,100.0,Color(0xFF8294AD));NS(nCloud(x.cloud));if(x.cloudMax.isFinite()) NS(uiText(R.string.ui_najviac_value_49103e3f, overviewNumber(x.cloudMax," %")))}
            OutlookMode.Uv->{NV(x.uv,color=uvColor(x.uv));NBar(x.uv,11.0,uvColor(x.uv));NS(uvLabel(x.uv))}
            OutlookMode.Air->{val b=aqiBand(x.aqi);NV(x.aqi,color=b?.color?:ForecastMuted);NS(b?.name?:uiText(R.string.ui_udaj_nedostupny_8bbc9c94));NBar(x.aqi,100.0,b?.color?:ForecastMuted)}
        }
    }}}
}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OverviewV2(location:Location) {
    val context=LocalContext.current;val prefs=remember {context.getSharedPreferences("perun_nowcast",0)}
    var source by rememberSaveable {mutableStateOf(prefs.getString("source","radar")?.takeIf {it in listOf("radar","openmeteo")}?:"radar")}
    var data by remember(location) {mutableStateOf<OverviewData?>(null)};var hours by remember(location) {mutableStateOf<List<NF>>(emptyList())};var nowcast by remember(location) {mutableStateOf<NNow?>(null)}
    var failed by remember(location) {mutableStateOf(false)};var ncFailed by remember(location) {mutableStateOf(false)};var hourFailed by remember(location) {mutableStateOf(false)};var loading by remember(location) {mutableStateOf(true)};var refresh by remember {mutableIntStateOf(0)}
    var mode by rememberSaveable {mutableStateOf(OutlookMode.Temp)};var hourMode by rememberSaveable {mutableStateOf(OutlookMode.Temp)}
    val animations=remember {Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0}
    LaunchedEffect(location,refresh) {loading=true;try {data=loadOverview(location);failed=false} catch(e:CancellationException){throw e} catch(e:Exception){failed=true} finally {loading=false}}
    LaunchedEffect(location,refresh,source) {nowcast=null;while(isActive) {val loaded=nTry {nLoad(location,source)};ncFailed=loaded==null;nowcast=loaded;delay(600000)}}
    val zone=data?.zone
    LaunchedEffect(location,refresh,zone) {if(zone!=null) {val loaded=nTry {nHours(location,zone)};hourFailed=loaded==null;if(loaded!=null) hours=loaded}}
    val transition=rememberInfiniteTransition(label="hero");val drift by transition.animateFloat(-7f,7f,infiniteRepeatable(tween(2100,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="drift")
    PullToRefreshBox(isRefreshing=loading,onRefresh={if(!loading) {NetCache.invalidate();refresh++}},modifier=Modifier.fillMaxSize().background(NBg)) {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(failed) NS(uiText(R.string.ui_udaje_sa_nepodarilo_obnovit_zobrazene_udaje_mozu_byt_stars_d68150be));if(data==null&&!loading) NS(uiText(R.string.ui_predpoved_nie_je_dostupna_potiahni_nadol_a_skus_znova_d03cf27d))
        data?.let {w->
            NS(w.time.format(DateTimeFormatter.ofPattern(appPattern("EEEE d. MMMM · HH:mm"),appLocale())))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text(overviewNumber(w.temperature,"°"),fontSize=80.sp,fontWeight=FontWeight.ExtraBold);Text(overviewCondition(w.code),fontSize=22.sp)};Box(Modifier.graphicsLayer {if(animations){translationX=drift;translationY=-drift*.6f;rotationZ=drift*.35f}}) {WeatherSymbol(w.code,112.dp,w.isDayFlag,animated=true)}}
            Row(verticalAlignment=Alignment.CenterVertically) {InfoDot(tempDotColor(w.apparent),uiText(R.string.ui_pocitova_teplota_a08f2e01)) {};NS(uiText(R.string.ui_pocitovo_value_2f1c5876, overviewNumber(w.apparent,uiText(R.string.ui_c_ccd18b02))))}
            val n=nowcast?.takeIf {it.wet};if(ncFailed) NS(uiText(R.string.ui_nowcast_sa_nepodarilo_nacitat_neznamena_to_ze_nebudu_zrazk_def9a9ee))
            if(n!=null) NNowCard(n,source,{source=it;prefs.edit().putString("source",it).apply()},w.zone)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                NCard(uiText(R.string.ui_zrazky_66d17319),Modifier.weight(1f),RainBlue,uiText(R.string.ui_uhrn_za_uvedeny_modelovy_interval_pravdepodobnost_nevyjadr_3f71976b)) {Text(overviewNumber(w.precipitation,uiText(R.string.ui_mm_c1752600),1),fontSize=24.sp,color=RainBlue);NS(uiText(R.string.ui_za_value_min_ce9a8163, w.intervalSeconds/60));Row(verticalAlignment=Alignment.CenterVertically) {Umbrella(probabilityColor(w.probability));NS(overviewNumber(w.probability," %"))};val rate=if(w.precipitation.isFinite()) w.precipitation*3600/w.intervalSeconds else 0.0;CloudFill(min(1.0,sqrt(max(0.0,rate)/6)),RainBlue,null,if(rate>0) 1 else 0,Modifier.width(68.dp));if(n!=null) {NS(n.headline);NScale(n)}}
                NCard(uiText(R.string.ui_vietor_8a3ebf26),Modifier.weight(1f),nWind(w.wind),uiText(R.string.ui_vietor_a_narazy_vo_vyske_10_m_farby_a_slovny_stupen_su_pod_ffe78baa)) {NV(w.wind,uiText(R.string.ui_km_h_14c28063),nWind(w.wind));NS(uiText(R.string.ui_narazy_value_8270e71d, overviewNumber(w.gust,uiText(R.string.ui_km_h_14c28063))),nWind(w.gust));if(w.direction.isFinite()) WindCompass(w.direction,ForecastGreen,Modifier.size(54.dp));NS(nBft(w.wind)?.let {NBftNames[it]}?:"—",nWind(w.wind))}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                NCard(uiText(R.string.ui_uv_027fcbbc),Modifier.weight(1f),uvColor(w.uv)) {ArcGauge(w.uv,11.0,{uvColor(it)},false,overviewNumber(w.uv),uvColor(w.uv),"0","11+",Modifier.fillMaxWidth());NS(uvLabel(w.uv),uvColor(w.uv));NS(uiText(R.string.ui_dnes_max_value_4d6092b5, overviewNumber(w.days.firstOrNull()?.uv?:Double.NaN)))}
                NCard(uiText(R.string.ui_oblacnost_b5a6e70c),Modifier.weight(1f),Color(0xFF8294AD),uiText(R.string.ui_pokrytie_oblohy_jasno_pod_12_5_prevazne_jasno_pod_37_5_pol_5cd2eda1)) {CloudFill(if(w.cloud.isFinite()) w.cloud/100 else 0.0,Color(0xFF8294AD),overviewNumber(w.cloud," %"),0,Modifier.fillMaxWidth());NS(nCloud(w.cloud))}
            }
            val noon=w.time.toLocalDate().atTime(12,0).atZone(w.zone).toInstant();val start=w.time.toLocalDate().atStartOfDay(w.zone).toInstant();val moon=remember(w.time.toLocalDate(),w.latitude,w.longitude) {Astro.moonDay(start,noon,w.latitude,w.longitude)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                NCard(uiText(R.string.ui_slnko_dnes_4d65cb81),Modifier.weight(1f),Color(0xFFFFD166)) {Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("↑ ${nClock(w.sunrise,w.zone)}",fontSize=22.sp);Text("↓ ${nClock(w.sunset,w.zone)}",fontSize=22.sp)};SunGraphic(Astro.sunVerticalPosition(w.sunrise,w.sunset,w.time.atZone(w.zone).toInstant(),w.zone),Modifier.width(30.dp))}}
                NCard(uiText(R.string.ui_mesiac_dnes_0e15b5c9),Modifier.weight(1f),Color(0xFF8C9BB2)) {Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("↑ ${nClock(moon.rise,w.zone)}",fontSize=20.sp);Text("↓ ${nClock(moon.set,w.zone)}",fontSize=20.sp)};MoonGraphic(moon.illumination,moon.waxing,moon.southern,Modifier.size(44.dp))};NS("${nPhase(noon)} · ${moon.illumination}%")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                NCard(uiText(R.string.ui_kvalita_ovzdusia_08946dda),Modifier.weight(1f),Color.White) {val a=w.air;val b=a?.let {aqiBand(it.aqi)};if(a!=null&&b!=null) {ArcGauge(a.aqi,100.0,{aqiBand(it)?.color?:ForecastMuted},false,overviewNumber(a.aqi),b.color,"0","100+",Modifier.fillMaxWidth());NS(b.name,b.color)} else NS(uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61));NS(uiText(R.string.ui_pm2_5_value_4038bed9, overviewNumber(a?.pm25?:Double.NaN,uiText(R.string.ui_g_m3_db57960b),1)));NS(uiText(R.string.ui_pm10_value_840f8ad6, overviewNumber(a?.pm10?:Double.NaN,uiText(R.string.ui_g_m3_db57960b),1)))}
                NCard(uiText(R.string.ui_pel_aktualne_0fd4a0ea),Modifier.weight(1f),ForecastGreen,uiText(R.string.ui_najviac_tri_najvyznamnejsie_druhy_podla_stupna_a_pomeru_k_65df291a)) {val p=w.air?.pollen.orEmpty().filter {it.value>0}.entries.sortedWith(compareByDescending<Map.Entry<String,Double>> {pollenLevel(it.key,it.value)}.thenByDescending {pollenRatio(it.key,it.value)}).take(3);if(p.isEmpty()) NS(if(w.air?.pollen?.isNotEmpty()==true) uiText(R.string.ui_v_dostupnych_udajoch_nie_je_ziadny_pel_96308221) else uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61));p.forEach {val level=pollenLevel(it.key,it.value);Text(PollenNames[it.key]?:it.key,fontWeight=FontWeight.Bold);NS(PollenLevelNames[level],PollenLevelColors[level])}}
            }
            if(n!=null) NStormCard(n.storm,animations)
            if(w.alerts.isNotEmpty()) NCard(uiText(R.string.ui_vystrahy_d88f15ec),Modifier.fillMaxWidth(),Color(0xFFFFB74D),uiText(R.string.ui_oficialne_vystrahy_meteoalarm_kde_a_samostatne_oznacene_lo_fbb8b096)) {w.alerts.forEach {NS(it.event+(if(it.local) uiText(R.string.ui_lokalny_odhad_d1e2c561) else uiText(R.string.ui_oficialna_vystraha_85131657)))}}
            if(hourFailed) NS(uiText(R.string.ui_hodinovy_vyhlad_sa_nepodarilo_obnovit_zobrazene_udaje_mozu_f6724c79))
            NForecast(uiText(R.string.ui_vyhlad_na_24_hodin_62605462),hours,hourMode,{hourMode=it})
            val daily=w.days.mapIndexed {i,d->NF(if(i==0) uiText(R.string.ui_dnes_ce1bed34) else d.date.format(DateTimeFormatter.ofPattern("EEEE",appLocale())),d.date.format(DateTimeFormatter.ofPattern(appPattern("d.M."),appLocale())),d.code,d.tmax,d.tmin,d.liquid,d.snow,d.probability,d.wind,d.gust,d.windDirection,d.cloudMean,d.cloudMax,d.uv,d.aqi)}
            NForecast(uiText(R.string.ui_vyhlad_na_5_dni_90f1b8c5),daily,mode,{mode=it})
            NS(uiText(R.string.ui_modelove_udaje_nie_meranie_zo_stanice_open_meteo_com_cc_by_1b91e112))
        }
    }}
}
