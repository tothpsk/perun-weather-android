package sk.perun.weather.ui

import sk.perun.weather.R

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val NcBg=Color(0xFF202833)
private val NcEdge=Color(0xFF4B586C)
private const val NcBase="https://api.librewxr.net"
private val NcPalette="009f9f 009e9e 009d99 009a8c 009573 008c4b 008c4a 009047 009b40 00b031 00d319 00d319 02d519 07dc1a 10e81d 21fd22 22fd21 2ffd21 50fd20 92fd1e fffd1b fffc1a fffa19 fff415 ffe80d ffd400 ffd300 ffd100 ffcb00 ffbf00 ffab00 ffaa00 ffa700 ff9d00 ff8b00 ff6e00 fe6d00 fe6700 fb5900 f73d01 f01002 ef0f02 ed0f04 e90d09 df0a12 d00523 d00424 d1042c d40341 da026b e400b1 e400b1 e503b5 e90bc1 f11ad8 ff33ff ff33ff ff36ff ff3eff ff4dff ff66ff ff66ff ff69ff ff71ff ff80ff ff99ff ff99ff ff9aff ff9cff ffa1ff ffaaff".split(" ").map { it.toInt(16) }

private fun ncColor(r:Double)=when { r>=7.6->Color(0xFFEF5350);r>=2.5->Color(0xFFFFCA28);r>=0.5->Color(0xFF66BB6A);r>=0.1->Color(0xFFA5D6A7);else->Color(0xFF2A3446) }
private fun ncIntensity(r:Double)=when { r>=7.6->uiText(R.string.ui_silne_zrazky_208eb2db);r>=2.5->uiText(R.string.ui_mierne_zrazky_beedc2e1);r>=0.5->uiText(R.string.ui_slabe_zrazky_135b3ccf);r>=0.1->uiText(R.string.ui_velmi_slabe_zrazky_aa3f54f0);else->uiText(R.string.ui_bez_zrazok_2da018fe) }
private fun ncClock(t:Instant?,z:ZoneId)=t?.atZone(z)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"
private fun ncJson(url:String)=JSONObject(String(fetchBytes(url),Charsets.UTF_8))
internal suspend fun <T> ncTry(block:suspend ()->T):T? = try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { null }

internal data class NcPoint(val min:Int,val rate:Double,val dbz:Int?=null)
internal data class NcCell(val dx:Double,val dy:Double,val dbz:Double,val speed:Double,val heading:Double,val eta:Int?) {
    val dist get()=hypot(dx,dy)
    val bearing get()=(atan2(dx,dy)*180/PI+360)%360
}
internal data class NcStrike(val dx:Double,val dy:Double) { val dist get()=hypot(dx,dy) }
internal data class NcStorm(val cells:List<NcCell>?,val strikes:List<NcStrike>?) {
    val active:Boolean get()=(cells?.isNotEmpty()==true) || (strikes?.any { it.dist<=25 }==true)
}
internal data class NcNow(val points:List<NcPoint>,val source:String,val fallback:Boolean,val base:Instant,val storm:NcStorm) {
    val wet:Boolean get()=points.any { it.rate>=0.1 }
    val peak:NcPoint get()=points.maxBy { it.rate }
    val headline:String get() {
        val first=points.firstOrNull { it.rate>=0.1 } ?: return uiText(R.string.ui_bez_zrazok_cf1104e5)
        if(points.first().rate<0.1) return uiText(R.string.ui_dazd_zacne_o_value_min_1d0cabf6, first.min)
        val gap=points.indices.firstOrNull { i->i>0 && points[i].rate<0.1 } ?: return uiText(R.string.nc_headline_lasting, points.last().min)
        return if(points.drop(gap).all { it.rate<0.1 }) uiText(R.string.ui_dazd_skonci_o_value_min_25bffcd9, points[gap].min) else uiText(R.string.nc_headline_breaks)
    }
}

private fun ncDbz(pixel:Int):Int {
    if((pixel ushr 24)<128) return 0
    val r=(pixel ushr 16) and 255;val g=(pixel ushr 8) and 255;val b=pixel and 255
    var best=0;var distance=Int.MAX_VALUE
    NcPalette.forEachIndexed { i,c->
        val dr=r-((c ushr 16) and 255);val dg=g-((c ushr 8) and 255);val db=b-(c and 255)
        val d=dr*dr+dg*dg+db*db
        if(d<distance) { distance=d;best=i }
    }
    return if(distance<=4800) 10+best else 0
}

private fun ncSample(path:String,l:Location):Int {
    require(path.startsWith("/v2/") && !path.contains("..") && !path.contains('?'))
    val x=(l.longitude+180)/360*128
    val rad=l.latitude.coerceIn(-85.05112878,85.05112878)*PI/180
    val y=(1-ln(tan(rad)+1/cos(rad))/PI)/2*128
    val tx=floor(x).toInt().coerceIn(0,127);val ty=floor(y).toInt().coerceIn(0,127)
    val px=floor((x-floor(x))*256).toInt().coerceIn(0,255);val py=floor((y-floor(y))*256).toInt().coerceIn(0,255)
    val bytes=fetchBytes("$NcBase$path/256/7/$tx/$ty/7/0_0.png")
    val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Invalid radar tile")
    try {
        require(image.width==256 && image.height==256)
        var best=0
        for(a in max(0,px-1)..min(255,px+1)) for(b in max(0,py-1)..min(255,py+1)) best=max(best,ncDbz(image.getPixel(a,b)))
        return best
    } finally { image.recycle() }
}

private suspend fun ncRadar(l:Location):Pair<Instant,List<NcPoint>> = coroutineScope {
    val radar=ncJson("$NcBase/public/weather-maps.json").getJSONObject("radar")
    val past=radar.getJSONArray("past");val future=radar.getJSONArray("nowcast")
    check(past.length()>0 && future.length()>0)
    val last=past.getJSONObject(past.length()-1);val base=last.getLong("time");val now=Instant.now();check(now.epochSecond-base<=1200) { "Stale radar" }
    val frames=listOf(last)+(0 until min(6,future.length())).map { future.getJSONObject(it) }
    val points=frames.map { f->
        async {
            val d=ncSample(f.getString("path"),l)
            NcPoint(if(f===last) 0 else max(0,((f.getLong("time")-now.epochSecond)/60.0).roundToInt()),if(d>=10) (10.0.pow(d/10.0)/200).pow(1/1.6) else 0.0,d)
        }
    }.awaitAll().sortedBy { it.min }
    check(points.last().min>=30) { "Incomplete radar horizon" }
    now to points
}

private fun ncModel(l:Location):Pair<Instant,List<NcPoint>> {
    val m=ncJson("https://api.open-meteo.com/v1/forecast?latitude=${l.latitude}&longitude=${l.longitude}&minutely_15=precipitation&forecast_minutely_15=8&past_minutely_15=1&timeformat=unixtime&timezone=GMT").getJSONObject("minutely_15")
    val times=m.getJSONArray("time");val values=m.getJSONArray("precipitation");val base=Instant.now()
    return base to (0..60 step 10).map { min->
        val stamp=base.epochSecond+min*60
        val i=(0 until times.length()).firstOrNull { stamp>times.getLong(it)-900 && stamp<=times.getLong(it) } ?: error("Missing nowcast interval")
        val amount=values.optDouble(i,Double.NaN)
        check(amount.isFinite() && amount>=0)
        NcPoint(min,amount*4)
    }
}

private fun ncXY(l:Location,c:JSONArray):Pair<Double,Double>? {
    val lon=c.optDouble(0,Double.NaN);val lat=c.optDouble(1,Double.NaN)
    return if(lon.isFinite() && lat.isFinite()) (lon-l.longitude)*111.32*cos(l.latitude*PI/180) to (lat-l.latitude)*110.57 else null
}

private suspend fun ncStorm(l:Location):NcStorm = coroutineScope {
    val q="lat=${l.latitude}&lon=${l.longitude}&radius_km=100"
    val cells=async {
        ncTry {
            val a=ncJson("$NcBase/v2/storm-cells?$q").getJSONArray("features")
            (0 until a.length()).mapNotNull { i->
                val f=a.getJSONObject(i)
                val c=f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                val (dx,dy)=ncXY(l,c) ?: return@mapNotNull null
                val p=f.optJSONObject("properties") ?: JSONObject()
                val speed=p.optDouble("motion_speed_kmh",Double.NaN);val heading=p.optDouble("motion_heading_deg",Double.NaN)
                val eta=if(speed.isFinite() && heading.isFinite()) (0..60 step 5).firstOrNull { t->hypot(dx+speed*sin(heading*PI/180)/60*t,dy+speed*cos(heading*PI/180)/60*t)<8 } else null
                NcCell(dx,dy,p.optDouble("max_dbz",Double.NaN),speed,heading,eta)
            }.sortedWith(compareBy<NcCell> { it.eta ?: 1000000 }.thenBy { it.dist })
        }
    }
    val strikes=async {
        ncTry {
            val a=ncJson("$NcBase/v2/lightning?$q&limit=500").getJSONArray("features")
            (0 until a.length()).mapNotNull { i->
                val c=a.getJSONObject(i).optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                ncXY(l,c)?.let { (dx,dy)->NcStrike(dx,dy) }
            }
        }
    }
    NcStorm(cells.await(),strikes.await())
}

// source "libre": LibreWXR radar tiles, Open-Meteo minutely_15 only as fallback; "openmeteo": the model directly (as Breezy Weather).
internal suspend fun ncLoad(l:Location,source:String="libre"):NcNow = withContext(Dispatchers.IO) {
    coroutineScope {
        val storm=async { ncStorm(l) }
        val radar=if(source=="openmeteo") null else ncTry { ncRadar(l) }
        val result=radar ?: ncTry { ncModel(l) }
        NcNow(result?.second ?: emptyList(),if(radar!=null) "radar" else "openmeteo",radar==null && source!="openmeteo",result?.first ?: Instant.now(),storm.await())
    }
}

@Composable private fun NcNowHelp() {
    HelpText(uiText(R.string.ui_odhad_intenzity_zrazok_v_nasledujucej_hodine_po_10_minutac_091a8693))
    HelpRow(Color(0xFFA5D6A7),uiText(R.string.ui_velmi_slabe_zrazky_3cd2f435),uiText(R.string.ui_od_0_1_mm_h_57493f20))
    HelpRow(Color(0xFF66BB6A),uiText(R.string.ui_slabe_zrazky_32fb6955),uiText(R.string.ui_od_0_5_mm_h_d85adb17))
    HelpRow(Color(0xFFFFCA28),uiText(R.string.ui_mierne_zrazky_066689b5),uiText(R.string.ui_od_2_5_mm_h_7ca50a7e))
    HelpRow(Color(0xFFEF5350),uiText(R.string.ui_silne_zrazky_2630cc03),uiText(R.string.ui_od_7_6_mm_h_0d6cb6d1))
    HelpText(uiText(R.string.ui_ide_o_orientacny_odhad_intenzity_z_radarovej_odrazivosti_n_def8da11))
}

@Composable internal fun NowcastTile(n:NcNow,z:ZoneId,onHelp:(HelpSpec)->Unit) {
    val units=rememberForecastPreferences().units
    val peak=n.peak
    var selected by remember(n) { mutableIntStateOf(-1) }
    val shown=n.points.getOrNull(selected) ?: peak
    val decimals=if(units.precipitation==PrecipitationUnit.Imperial) 2 else 1
    val unit=" "+units.precipitation.liquidSymbol+"/h"
    val amount=overviewNumber(units.precipitation.fromMillimetres(shown.rate),unit,decimals)
    val detail=if(selected>=0) ncClock(n.base.plusSeconds(shown.min*60L),z)+" · "+amount+" · "+ncIntensity(shown.rate) else uiText(R.string.ui_najsilnejsie_o_235015eb)+ncClock(n.base.plusSeconds(peak.min*60L),z)+" · "+amount
    OutlinedCard(
        Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,NcEdge),
        colors=CardDefaults.outlinedCardColors(containerColor=NcBg,contentColor=Color(0xFFE8EAF0)),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom=10.dp)) {
            Row(Modifier.padding(start=2.dp),verticalAlignment=Alignment.CenterVertically) {
                TileDot(ncColor(peak.rate),uiText(R.string.ui_nowcast_zrazok_753057c8),false,ncIntensity(peak.rate)) { onHelp(HelpSpec(uiText(R.string.ui_nowcast_zrazok_753057c8)) { NcNowHelp() }) }
                TileTitle(uiText(R.string.ui_nowcast_zrazok_753057c8))
            }
            Column(Modifier.padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(n.headline,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription=n.points.joinToString { ncClock(n.base.plusSeconds(it.min*60L),z)+": "+ncIntensity(it.rate) } },
                    horizontalArrangement=Arrangement.spacedBy(4.dp),
                ) {
                    n.points.forEachIndexed { i,p->
                        val barHeight=(if(p.rate>=.1) max(8.0,min(54.0,sqrt(p.rate/10)*54)) else 4.0).dp
                        val barShape=RoundedCornerShape(3.dp)
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { selected=if(selected==i) -1 else i },contentAlignment=Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height(barHeight).background(ncColor(p.rate),barShape).then(if(i==selected) Modifier.border(1.5.dp,Color.White,barShape) else Modifier))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    n.points.forEachIndexed { i,p ->
                        Text(ncClock(n.base.plusSeconds(p.min*60L),z),Modifier.weight(1f),fontSize=11.sp,color=if(i==selected) Color(0xFFE8EAF0) else ForecastMuted,fontWeight=if(i==selected) FontWeight.SemiBold else FontWeight.Normal,textAlign=TextAlign.Center,maxLines=1,softWrap=false)
                    }
                }
                Text(detail,fontSize=13.sp,color=if(selected>=0) ncColor(shown.rate).let { if(shown.rate>=.1) it else ForecastMuted } else ForecastMuted)
            }
        }
    }
}

private fun DrawScope.ncBolt(p:Offset,k:Float,color:Color) {
    val bolt=Path().apply { moveTo(p.x+.6f*k,p.y-4.5f*k);lineTo(p.x-2.6f*k,p.y+.8f*k);lineTo(p.x-.4f*k,p.y+.8f*k);lineTo(p.x-1.2f*k,p.y+4.5f*k);lineTo(p.x+2.6f*k,p.y-k);lineTo(p.x+.4f*k,p.y-k);close() }
    drawPath(bolt,color)
}

@Composable private fun NcRadarCanvas(cells:List<NcCell>,strikes:List<NcStrike>,dimension:Dp,phase:Float,flash:Float,sweep:Float,animate:Boolean) {
    val measurer=rememberTextMeasurer()
    val main=cells.firstOrNull();val shown=strikes.take(80)
    val maxD=max(main?.dist ?: 0.0,shown.maxOfOrNull { it.dist } ?: 0.0)
    val range=listOf(25.0,50.0,100.0).firstOrNull { maxD*1.2<=it } ?: 100.0
    Canvas(Modifier.size(dimension).semantics { contentDescription=uiText(R.string.ui_burkove_bunky_a_blesky_voci_lokalite_sever_je_hore_mierka_62f9f97f, range.toInt()) }) {
        val center=Offset(size.width/2,size.height/2);val radius=size.width*.40f
        val dash=PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),3.dp.toPx()))
        fun xy(dx:Double,dy:Double)=Offset(center.x+(dx/range*radius).toFloat(),center.y-(dy/range*radius).toFloat())
        fun color(c:NcCell)=if(c.dbz.isFinite()) Color(0xFF000000 or NcPalette[(c.dbz.roundToInt()-10).coerceIn(0,NcPalette.lastIndex)].toLong()) else ForecastMuted
        drawCircle(Color(0xFF141821),radius,center)
        if(animate) {
            for(k in 0..8) {
                val a=(sweep-k*5f-90f)*(PI.toFloat()/180f)
                drawLine(Color(0xFF66BB6A).copy(alpha=.28f*(1f-k/9f)),center,Offset(center.x+cos(a)*radius,center.y+sin(a)*radius),1.5.dp.toPx())
            }
        }
        drawCircle(NcEdge,radius,center,style=Stroke(1.dp.toPx()))
        drawCircle(NcEdge,radius/2,center,style=Stroke(1.dp.toPx(),pathEffect=dash))
        val north=measurer.measure(uiText(R.string.ui_s_8de0b3c4),TextStyle(color=Color(0xFFE8EAF0),fontSize=12.sp,fontWeight=FontWeight.Bold))
        drawText(north,topLeft=Offset(center.x-north.size.width/2f,center.y-radius-north.size.height-1.dp.toPx()))
        val scale=measurer.measure(uiText(R.string.ui_value_km_d66a9de4, range.toInt()),TextStyle(color=ForecastMuted,fontSize=11.sp))
        drawText(scale,topLeft=Offset(0f,center.y+radius-scale.size.height))
        cells.take(6).filter { it.dist<=range*1.05 }.forEach { drawCircle(color(it),if(it===main) 7.dp.toPx() else 4.dp.toPx(),xy(it.dx,it.dy)) }
        if(main!=null && main.speed.isFinite() && main.heading.isFinite() && main.speed>=3) {
            var ex=main.dx+main.speed*.5*sin(main.heading*PI/180);var ey=main.dy+main.speed*.5*cos(main.heading*PI/180)
            val len=hypot(ex,ey)
            if(len>range*1.02) { ex*=range*1.02/len;ey*=range*1.02/len }
            val a=xy(main.dx,main.dy);val b=xy(ex,ey)
            drawLine(ForecastMuted.copy(alpha=.5f),a,b,1.dp.toPx(),pathEffect=dash)
            val angle=atan2((b.y-a.y).toDouble(),(b.x-a.x).toDouble())
            val head=5.dp.toPx()
            for(side in listOf(-0.5,0.5)) drawLine(ForecastMuted.copy(alpha=.7f),b,Offset(b.x-(head*cos(angle+side)).toFloat(),b.y-(head*sin(angle+side)).toFloat()),1.4.dp.toPx())
            if(animate) {
                val fade=min(phase/.15f,(1f-phase)/.25f).coerceIn(0f,1f)
                drawCircle(color(main).copy(alpha=fade),5.dp.toPx(),Offset(a.x+(b.x-a.x)*phase,a.y+(b.y-a.y)*phase))
            }
        }
        shown.filter { it.dist<=range*1.05 }.forEachIndexed { i,s->
            val p=xy(s.dx,s.dy);val k=1.9.dp.toPx()
            val age=(flash+i*0.173f)%1f
            val alpha=if(animate) .25f+.75f*(1f-age)*(1f-age) else 1f
            if(animate && age<.2f) drawCircle(Color(0xFFFFE14D).copy(alpha=.28f*(1f-age/.2f)),11.dp.toPx(),p)
            ncBolt(p,k,Color(0xFFFFE14D).copy(alpha=alpha))
        }
        drawCircle(Color.White,4.5.dp.toPx(),center)
        drawCircle(Color(0xFFE53935),3.dp.toPx(),center)
    }
}

@Composable private fun NcRadar(cells:List<NcCell>,strikes:List<NcStrike>,dimension:Dp) {
    val resolver=LocalContext.current.contentResolver
    val animate=remember(resolver) { Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f }
    if(animate) {
        val main=cells.firstOrNull()
        val duration=main?.speed?.takeIf { it.isFinite() && it>=3 }?.let { (120/it).coerceIn(1.2,8.0) } ?: 8.0
        val transition=rememberInfiniteTransition(label="stormRadar")
        val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween((duration*1000).toInt(),easing=LinearEasing)),label="cellMotion")
        val flash by transition.animateFloat(0f,1f,infiniteRepeatable(tween(2400,easing=LinearEasing)),label="lightning")
        val sweep by transition.animateFloat(0f,360f,infiniteRepeatable(tween(6000,easing=LinearEasing)),label="sweep")
        NcRadarCanvas(cells,strikes,dimension,phase,flash,sweep,true)
    } else NcRadarCanvas(cells,strikes,dimension,.5f,0f,0f,false)
}

@Composable internal fun StormTile(s:NcStorm,onHelp:(HelpSpec)->Unit) {
    val main=s.cells?.firstOrNull()
    val near=s.strikes?.count { it.dist<=25 } ?: 0
    val dot=if(near>0) Color(0xFFFF7043) else Color(0xFFFFB74D)
    OutlinedCard(
        Modifier.fillMaxWidth().height(218.dp),shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,NcEdge),
        colors=CardDefaults.outlinedCardColors(containerColor=NcBg,contentColor=Color(0xFFE8EAF0)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start=2.dp),verticalAlignment=Alignment.CenterVertically) {
                TileDot(dot,uiText(R.string.ui_burky_v_okoli_60c0371d),false,if(near>0) uiText(R.string.ui_blesky_do_25_km_ce2c4ebd) else uiText(R.string.ui_burkova_bunka_v_okoli_ed6921f4)) {
                    onHelp(HelpSpec(uiText(R.string.ui_burky_v_okoli_60c0371d)) {
                        HelpText(uiText(R.string.ui_burkove_bunky_a_blesky_v_okoli_100_km_pismeno_s_oznacuje_s_70b50c90))
                        HelpRow(Color(0xFFFFB74D),uiText(R.string.ui_burkova_bunka_v_okoli_ed6921f4),uiText(R.string.ui_pozor_na_vyvoj_situacie_256af0fc))
                        HelpRow(Color(0xFFFF7043),uiText(R.string.ui_blesky_do_25_km_ce2c4ebd),uiText(R.string.ui_burka_je_pri_lokalite_17b2f702))
                        // The dots of the storm cells on the radar take the colour of the radar palette for their highest reflectivity.
                        HelpText(uiText(R.string.storm_help_cells))
                        HelpRow(Color(0xFF10E81D),uiText(R.string.storm_cell_weak),uiText(R.string.storm_cell_weak_note))
                        HelpRow(Color(0xFFFFE80D),uiText(R.string.storm_cell_moderate),uiText(R.string.storm_cell_moderate_note))
                        HelpRow(Color(0xFFFF8B00),uiText(R.string.storm_cell_heavy),uiText(R.string.storm_cell_heavy_note))
                        HelpRow(Color(0xFFEF0F02),uiText(R.string.storm_cell_intense),uiText(R.string.storm_cell_intense_note))
                        HelpRow(Color(0xFFE400B1),uiText(R.string.storm_cell_extreme),uiText(R.string.storm_cell_extreme_note))
                        HelpText(uiText(R.string.storm_help_direction))
                    })
                }
                TileTitle(uiText(R.string.ui_burky_v_okoli_60c0371d))
            }
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(start=12.dp,end=10.dp,bottom=6.dp),
                verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f).align(Alignment.CenterVertically),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(main!=null) Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(main.dist.roundToInt().toString(),fontSize=24.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                        Text(uiText(R.string.unit_km),color=ForecastMuted,fontSize=13.sp,maxLines=1)
                    } else Text(if(s.cells!=null) uiText(R.string.ui_bez_burkovych_buniek_07125c80) else "—",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                    if(main!=null) Text(uiText(R.string.storm_cell_bearing,cardinal(main.bearing)),fontSize=13.sp,color=ForecastMuted)
                    if(main!=null && main.speed.isFinite() && main.heading.isFinite() && main.speed>=3) Text(uiText(R.string.ui_pohyb_na_920fce15)+cardinal(main.heading),fontSize=13.sp,color=ForecastMuted)
                    if(main?.eta!=null) Text(if(main.eta==0) uiText(R.string.ui_uz_je_pri_vas_f67eeb25) else uiText(R.string.ui_pri_vas_o_value_min_84402b5d, main.eta),fontSize=13.sp,color=ForecastMuted)
                    Text(uiText(R.string.ui_blesky_do_25_km_62a7ba5c)+(s.strikes?.count { it.dist<=25 }?.toString() ?: "—"),fontSize=13.sp,color=ForecastMuted)
                }
                if(main!=null || !s.strikes.isNullOrEmpty()) NcRadar(s.cells.orEmpty(),s.strikes.orEmpty(),176.dp)
            }
        }
    }
}
