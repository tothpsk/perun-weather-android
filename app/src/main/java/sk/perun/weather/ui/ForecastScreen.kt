package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

internal data class ForecastHour(val time:Long,val values:Map<String,Double>) { fun v(k:String)=values[k] ?: Double.NaN }
internal data class ForecastDay(val date:LocalDate,val start:Long,val end:Long,val hours:List<ForecastHour>) {
    fun values(k:String)=hours.map { it.v(k) }.filter { it.isFinite() }
    fun min(k:String)=values(k).minOrNull() ?: Double.NaN
    fun max(k:String)=values(k).maxOrNull() ?: Double.NaN
    fun mean(k:String)=values(k).takeIf { it.isNotEmpty() }?.average() ?: Double.NaN
    fun sum(k:String)=values(k).takeIf { it.isNotEmpty() }?.sum() ?: Double.NaN
    val partial get()=hours.count { it.v("temperature_2m").isFinite() }.toLong()<(end-start)/3600
}
internal fun forecastTrend(h:ForecastHour,index:Map<Long,ForecastHour>):Double {
    val a=h.v("pressure_msl");val b=index[h.time-10800]?.v("pressure_msl") ?: Double.NaN
    return if(a.isFinite() && b.isFinite()) a-b else Double.NaN
}
internal fun forecastWindParts(w:Double,g:Double,max:Double):Pair<Double,Double> {
    if(max<=0 || !w.isFinite()) return 0.0 to 0.0
    val a=(w/max).coerceIn(0.0,1.0)
    return a to if(g.isFinite()) ((g/max).coerceIn(a,1.0)-a) else 0.0
}
private data class ForecastBundle(val days:List<ForecastDay>,val zone:ZoneId,val note:String)
private val FText=Color(0xFFE8EAF0)
private val FPanel=Color(0xFF202833)
private val FEdge=Color(0xFF4B586C)
private val FBft=listOf(0xFF6270B7,0xFF39619F,0xFF4994A9,0xFF4D8D7C,0xFF53A553,0xFF359E35,0xFFA69D50,0xFFA0645C,0xFFAF5088,0xFF754A92,0xFF45698D,0xFF5B9098,0xFF7D44A5).map { Color(it) }
private fun fw(v:Double)=beaufortIndex(v).takeIf { it>=0 }?.let { FBft[it] } ?: ForecastMuted
internal fun windBeaufortColor(speedKmh:Double):Color=fw(speedKmh)
private enum class FV(val label:String,val key:String) {
    Temp(uiText(R.string.ui_teplota_4b720579),"temperature_2m"),Feels(uiText(R.string.ui_pocitova_acd4f8bb),"apparent_temperature"),Rain(uiText(R.string.ui_zrazky_66d17319),"precipitation"),Prob(uiText(R.string.ui_pravdepodobnost_zrazok_c9b0a84a),"precipitation_probability"),
    Wind(uiText(R.string.ui_vietor_8a3ebf26),"wind_speed_10m"),Gust(uiText(R.string.ui_narazovy_vietor_ca701315),"wind_gusts_10m"),Direction(uiText(R.string.ui_smer_vetra_f7a54eb7),"wind_direction_10m"),Cloud(uiText(R.string.ui_oblacnost_b5a6e70c),"cloud_cover"),Uv(uiText(R.string.ui_uv_027fcbbc),"uv_index"),
    Humidity(uiText(R.string.ui_vlhkost_e06c751e),"relative_humidity_2m"),Dew(uiText(R.string.ui_rosny_bod_6caea4c6),"dew_point_2m"),Visibility(uiText(R.string.ui_viditelnost_7f90db41),"visibility"),Pressure(uiText(R.string.ui_tlak_vzduchu_cc4c1846),"pressure_msl"),Trend(uiText(R.string.ui_trend_tlaku_1e853b71),"pressure_trend")
}
private val ForecastModes=listOf(FV.Temp,FV.Rain,FV.Wind,FV.Cloud,FV.Uv,FV.Humidity,FV.Visibility,FV.Pressure)
private fun unit(v:FV,u:WeatherUnits)=when(v) {
    FV.Temp,FV.Feels,FV.Dew->u.temperature.symbol;FV.Rain->u.precipitation.liquidSymbol;FV.Wind,FV.Gust->u.wind.symbol
    FV.Prob,FV.Cloud,FV.Humidity->"%";FV.Direction->"°";FV.Visibility->"km";FV.Pressure->u.pressure.symbol;FV.Trend->u.pressure.symbol+uiText(R.string.ui_3_h_a8c2ce7c);else->""
}
private fun convert(v:FV,x:Double,u:WeatherUnits)=when(v) {
    FV.Temp,FV.Feels,FV.Dew->u.temperature.fromCelsius(x);FV.Rain->u.precipitation.fromMillimetres(x);FV.Wind,FV.Gust->u.wind.fromKmh(x)
    FV.Visibility->x/1000;FV.Pressure->u.pressure.fromHpa(x);FV.Trend->if(x==0.0) 0.0 else sign(x)*u.pressure.fromHpa(abs(x));else->x
}
private fun decimals(v:FV,u:WeatherUnits)=when(v) { FV.Rain->if(u.precipitation==PrecipitationUnit.Imperial) 2 else 1;FV.Visibility,FV.Trend->1;FV.Pressure->if(u.pressure==PressureUnit.InchesMercury) 2 else 0;else->0 }
private fun value(v:FV,x:Double,u:WeatherUnits)=weatherUnitNumber(convert(v,x,u),decimals(v,u))+" "+unit(v,u)
private fun degrees(x:Double,u:WeatherUnits)=weatherUnitNumber(u.temperature.fromCelsius(x),0)+"°"
private fun tint(v:FV,x:Double,dew:Double=Double.NaN):Color=if(!x.isFinite()) ForecastMuted else when(v) {
    FV.Temp,FV.Feels,FV.Dew->tempDotColor(x);FV.Rain,FV.Prob->RainBlue;FV.Wind,FV.Gust->fw(x);FV.Uv->uvColor(x)
    FV.Humidity->humidityBand(x,dew)?.color ?: ForecastMuted;FV.Visibility->visibilityBand(x)?.color ?: ForecastMuted;FV.Trend->pressureBand(x).color;FV.Cloud->Color(0xFF8294AD);FV.Pressure->Color(0xFFBA9EDB);else->ForecastMuted
}
private fun aggregate(day:ForecastDay,v:FV)=when(v) {
    FV.Rain->day.sum(v.key);FV.Wind,FV.Gust,FV.Prob,FV.Uv->day.max(v.key)
    FV.Direction->day.hours.filter { it.v("wind_speed_10m").isFinite() }.maxByOrNull { it.v("wind_speed_10m") }?.v(v.key) ?: Double.NaN
    else->day.mean(v.key)
}
private fun feelsPeriod(day:ForecastDay,daytime:Boolean):Double {
    val xs=day.hours.filter { it.v("is_day")==if(daytime) 1.0 else 0.0 }.map { it.v("apparent_temperature") }.filter(Double::isFinite)
    return (if(daytime) xs.maxOrNull() else xs.minOrNull()) ?: Double.NaN
}
private suspend fun forecastLoad(l:Location,model:String):ForecastBundle=withContext(Dispatchers.IO) {
    val keys=FV.entries.filter { it!=FV.Trend }.map { it.key }+listOf("snowfall","rain","showers","weather_code","is_day")
    val q="latitude=${l.latitude}&longitude=${l.longitude}&timezone="+URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8")+"&timeformat=unixtime&forecast_days=16&past_days=1&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"+(l.elevation?.let { "&elevation=$it" } ?: "")
    fun request(extra:String)=JSONObject(String(fetchBytes("https://api.open-meteo.com/v1/forecast?$q$extra"),Charsets.UTF_8))
    val root=request((if(model=="best_match") "" else "&models="+URLEncoder.encode(model,"UTF-8"))+"&hourly="+keys.joinToString(","))
    val z=ZoneId.of(root.getString("timezone"));val h=root.getJSONObject("hourly");val t=h.getJSONArray("time")
    var hours=List(t.length()) { i->ForecastHour(t.getLong(i),keys.associateWith { k->val a=h.optJSONArray(k);if(a==null || a.isNull(i)) Double.NaN else a.optDouble(i,Double.NaN) }) }
    require(hours.isNotEmpty() && hours.zipWithNext().all { (a,b)->b.time>a.time }) { uiText(R.string.ui_neplatna_casova_os_a0ba71bc) }
    val today=Instant.now().atZone(z).toLocalDate();val start=today.atStartOfDay(z).toEpochSecond()
    val last=hours.lastOrNull { it.time>=start && it.v("temperature_2m").isFinite() }?.time ?: error(uiText(R.string.ui_model_nevratil_predpoved_pre_lokalitu_f9977c57))
    hours=hours.filter { it.time<=last }
    var note=uiText(R.string.ui_rozsah_podla_dostupnej_teplotnej_predpovede_zvoleneho_mode_b54cf840)
    if(hours.any { it.time>=start && !it.v("uv_index").isFinite() }) try {
        val uv=request("&models=gfs_seamless&hourly=uv_index").getJSONObject("hourly");val ut=uv.getJSONArray("time");val ua=uv.getJSONArray("uv_index")
        val ix=(0 until ut.length()).associate { i->ut.getLong(i) to if(ua.isNull(i)) Double.NaN else ua.optDouble(i,Double.NaN) }
        var filled=0
        hours=hours.map { row->val a=ix[row.time] ?: Double.NaN;if(!row.v("uv_index").isFinite() && a.isFinite()) { filled++;row.copy(values=row.values+("uv_index" to a)) } else row }
        if(filled>0) note+=uiText(R.string.ui_chybajuce_uv_doplnene_z_gfs_bez_predlzenia_hlavneho_horizo_649c0e6f)
    } catch(e:CancellationException) { throw e } catch(e:Exception) { note+=uiText(R.string.ui_nahradne_uv_nie_je_dostupne_d90ceb69) }
    if(model!="best_match" && hours.any { it.time>=start && !it.v("precipitation_probability").isFinite() }) try {
        val pr=request("&hourly=precipitation_probability").getJSONObject("hourly");val pt=pr.getJSONArray("time");val pa=pr.getJSONArray("precipitation_probability")
        val px=(0 until pt.length()).associate { i->pt.getLong(i) to if(pa.isNull(i)) Double.NaN else pa.optDouble(i,Double.NaN) }
        var filledProb=0
        hours=hours.map { row->val a=px[row.time] ?: Double.NaN;if(!row.v("precipitation_probability").isFinite() && a.isFinite()) { filledProb++;row.copy(values=row.values+("precipitation_probability" to a)) } else row }
        if(filledProb>0) note+=uiText(R.string.forecast_prob_filled)
    } catch(e:CancellationException) { throw e } catch(e:Exception) { }
    val ix=hours.associateBy { it.time }
    hours=hours.map { it.copy(values=it.values+("pressure_trend" to forecastTrend(it,ix))) }
    val days=hours.filter { it.time>=start }.groupBy { Instant.ofEpochSecond(it.time).atZone(z).toLocalDate() }.toSortedMap().map { (date,rows)->ForecastDay(date,date.atStartOfDay(z).toEpochSecond(),date.plusDays(1).atStartOfDay(z).toEpochSecond(),rows) }
    ForecastBundle(days,z,note)
}

// targetDay is an ISO date such as 2026-11-20: that day opens expanded (the tap on the Day by hours widget); empty opens the list.
@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable internal fun ForecastScreen(location:Location,targetGraph:String="",targetDay:String="") {
    val prefs=rememberForecastPreferences();val u=prefs.units
    var data by remember(location,prefs.model) { mutableStateOf<ForecastBundle?>(null) }
    var error by remember(location,prefs.model) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) };var refresh by remember { mutableIntStateOf(0) }
    var open by remember(location,prefs.model,targetDay) { mutableStateOf<LocalDate?>(runCatching { LocalDate.parse(targetDay) }.getOrNull()) }
    val listState=rememberLazyListState()
    var mode by remember(targetGraph) { mutableStateOf(when(targetGraph) { "wind"->FV.Wind;"uv"->FV.Uv;"precipitation","probability"->FV.Rain;"cloud"->FV.Cloud;"pressure"->FV.Pressure;"humidity"->FV.Humidity;"visibility"->FV.Visibility;else->FV.Temp }) }
    LaunchedEffect(location,prefs.model,refresh) {
        loading=true;error=null
        try { data=forecastLoad(location,prefs.model) } catch(e:CancellationException) { throw e } catch(e:Exception) { error=e.message ?: uiText(R.string.ui_chyba_b622e8d6) } finally { loading=false }
    }
    val bundle=data
    val expanded=bundle?.days?.firstOrNull { it.date==open }
    BackHandler(enabled=expanded!=null) { open=null }
    if(expanded!=null && bundle!=null) {
        ForecastExpandedDay(expanded,bundle,u,{ step -> bundle.days.getOrNull(bundle.days.indexOf(expanded)+step)?.let { open=it.date } }) { open=null }
        return
    }
    PullToRefreshBox(isRefreshing=loading,onRefresh={if(!loading) { NetCache.invalidate();loading=true;refresh++ }},modifier=Modifier.fillMaxSize().background(Color(0xFF323E4F))) {
    LazyColumn(Modifier.fillMaxSize().horizontalSwipe(Unit) { step -> mode=ForecastModes[(ForecastModes.indexOf(mode)+step).coerceIn(0,ForecastModes.lastIndex)] }.padding(horizontal=12.dp),state=listState,contentPadding=PaddingValues(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            error?.let { Text(uiText(R.string.ui_nacitanie_zlyhalo_value_pripadne_zobrazene_udaje_su_zo_sko_1395c1e9, it),color=Color(0xFFFFB74D)) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                ForecastModes.forEach { v->
                    val active=mode==v;val shape=RoundedCornerShape(50)
                    Box(Modifier.size(48.dp).clip(shape).background(if(active) Color(0xFF4FC3F7) else FPanel).border(1.dp,if(active) Color(0xFF4FC3F7) else FEdge,shape)
                        .selectable(selected=active,role=Role.RadioButton,onClick={mode=v}).semantics { contentDescription=v.label },contentAlignment=Alignment.Center) {
                        ForecastModeGlyph(v,if(active) Color(0xFF101F39) else FText)
                    }
                }
            }
        }
        item { Text(mode.label,modifier=Modifier.fillMaxWidth().padding(top=6.dp,bottom=2.dp),color=FText,fontSize=20.sp,fontWeight=FontWeight.Bold) }
        if(bundle!=null) items(bundle.days,key={it.date.toString()}) { day->Surface(color=FPanel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FEdge)) {
            ForecastDayRow(day,bundle,mode,u,false) { open=day.date }
        } }
        item { AttributionFooter() }
    }
    }
}
@Composable private fun ForecastModeGlyph(v:FV,color:Color) {
    when(v) {
        FV.Temp->SummaryGlyph(SummaryIcon.Temp,color,22.dp)
        FV.Rain->SummaryGlyph(SummaryIcon.Rain,color,22.dp)
        FV.Wind->SummaryGlyph(SummaryIcon.Wind,color,22.dp)
        FV.Uv->SummaryGlyph(SummaryIcon.Uv,color,22.dp)
        FV.Humidity->HumidityGlyph(color,22.dp)
        FV.Cloud->Canvas(Modifier.size(24.dp)) {
            val k=size.width/24f;scale(k,k,Offset.Zero) {
                val p=Path().apply { moveTo(5f,19f);cubicTo(0f,19f,0f,11f,5f,11f);cubicTo(5f,3f,16f,3f,17f,10f);cubicTo(24f,9f,25f,19f,19f,19f);close() };drawPath(p,color,style=Stroke(1.8f,join=StrokeJoin.Round))
            }
        }
        FV.Visibility,FV.Pressure->Canvas(Modifier.size(22.dp)) {
            val k=size.width/24f;scale(k,k,Offset.Zero) {
                val s=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round)
                if(v==FV.Visibility) {
                    val p=Path().apply { moveTo(2f,12f);cubicTo(7f,2f,17f,2f,22f,12f);cubicTo(17f,22f,7f,22f,2f,12f);close() };drawPath(p,color,style=s);drawCircle(color,3.5f,Offset(12f,12f))
                } else {
                    drawCircle(color,10f,Offset(12f,12f),style=s);drawLine(color,Offset(6f,12f),Offset(18f,12f),1.8f,StrokeCap.Round);drawLine(color,Offset(14f,8f),Offset(18f,12f),1.8f,StrokeCap.Round);drawLine(color,Offset(14f,16f),Offset(18f,12f),1.8f,StrokeCap.Round)
                }
            }
        }
        else->Unit
    }
}
private fun forecastCloudName(x:Double)=when { !x.isFinite() || x !in 0.0..100.0->uiText(R.string.ui_udaj_nedostupny_8bbc9c94);x<12.5->uiText(R.string.ui_jasno_e78eee64);x<37.5->uiText(R.string.ui_prevazne_jasno_7331ba47);x<62.5->uiText(R.string.ui_polooblacno_83f2c354);x<87.5->uiText(R.string.ui_prevazne_oblacno_e5122573);else->uiText(R.string.ui_zamracene_829159bb) }
@Composable private fun ForecastChevron(open:Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val path=Path().apply { moveTo(size.width*.25f,size.height*(if(open) .6f else .4f));lineTo(size.width*.5f,size.height*(if(open) .4f else .6f));lineTo(size.width*.75f,size.height*(if(open) .6f else .4f)) }
        drawPath(path,RainBlue,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
    }
}
@Composable private fun ForecastCompactBody(top:@Composable ()->Unit,scaleBar:@Composable ()->Unit,bottom:@Composable ()->Unit,graphic:@Composable ()->Unit) {
    Layout(content={ Box(Modifier.heightIn(min=40.dp),contentAlignment=Alignment.CenterStart) { top() };Box { scaleBar() };Box(Modifier.heightIn(min=24.dp),contentAlignment=Alignment.CenterStart) { bottom() };Box(contentAlignment=Alignment.Center) { graphic() } },modifier=Modifier.fillMaxWidth()) { ms,c->
        val graphicSize=52.dp.roundToPx();val gap=12.dp.roundToPx();val space=6.dp.roundToPx();val barHeight=8.dp.roundToPx()
        val left=(c.maxWidth-graphicSize-gap).coerceAtLeast(1)
        val textConstraints=Constraints(minWidth=left,maxWidth=left)
        val t=ms[0].measure(textConstraints);val s=ms[1].measure(Constraints.fixed(left,barHeight));val b=ms[2].measure(textConstraints)
        val g=ms[3].measure(Constraints.fixed(graphicSize,graphicSize))
        val center=t.height+space+s.height/2;val inset=(g.height/2-center).coerceAtLeast(0)
        val barY=inset+t.height+space;val bottomY=barY+s.height+space;val graphicY=inset+center-g.height/2
        layout(c.maxWidth,max(bottomY+b.height,graphicY+g.height)) { t.placeRelative(0,inset);s.placeRelative(0,barY);b.placeRelative(0,bottomY);g.placeRelative(left+gap,graphicY) }
    }
}
@Composable private fun ForecastLine(text:String,color:Color=ForecastMuted) { Text(text,color=color,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis) }
private fun forecastDayName(date:LocalDate,zone:ZoneId):String {
    val today=Instant.now().atZone(zone).toLocalDate()
    return when(date) { today->uiText(R.string.ui_dnes_ce1bed34);today.plusDays(1)->uiText(R.string.ui_zajtra_c3e86eb2);else->date.format(DateTimeFormatter.ofPattern("EEEE",appLocale())) }
}
@Composable private fun ForecastDayTitle(date:LocalDate,zone:ZoneId,dateBold:Boolean,modifier:Modifier,nameSize:TextUnit=14.sp,dateSize:TextUnit=11.sp) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight=FontWeight.Bold)) { append(forecastDayName(date,zone)) }
        withStyle(SpanStyle(color=ForecastMuted,fontSize=dateSize,fontWeight=if(dateBold) FontWeight.Bold else FontWeight.Normal)) {
            append(" · "+date.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale())))
        }
    },color=FText,fontSize=nameSize,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=modifier)
}
@Composable private fun ForecastDayRow(day:ForecastDay,bundle:ForecastBundle,v:FV,u:WeatherUnits,open:Boolean,onClick:()->Unit) {
    val a=aggregate(day,v);val dew=day.mean("dew_point_2m");val trend=day.mean("pressure_trend");val wind=day.max("wind_speed_10m");val gust=day.max("wind_gusts_10m")
    val direction=aggregate(day,FV.Direction);val color=tint(v,a,dew)
    val extra=when(v) { FV.Temp->listOf(FV.Feels);FV.Rain->listOf(FV.Prob);FV.Wind->listOf(FV.Gust,FV.Direction);FV.Humidity->listOf(FV.Dew);FV.Pressure->listOf(FV.Trend);else->emptyList() }
    val incomplete=day.partial || (listOf(v)+extra).any { day.values(it.key).size<day.hours.size }
    Column(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel=if(open) uiText(R.string.ui_zbalit_den_f8207c5b) else uiText(R.string.ui_rozbalit_den_9601108c),onClick=onClick).padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=28.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            ForecastDayTitle(day.date,bundle.zone,false,Modifier.weight(1f))
            if(incomplete) Text("•",color=Color(0xFFFFB74D),modifier=Modifier.semantics { contentDescription=uiText(R.string.ui_neuplne_udaje_b649939a) })
            val code=day.hours.minByOrNull { abs(Instant.ofEpochSecond(it.time).atZone(bundle.zone).hour-12) }?.v("weather_code") ?: Double.NaN
            WeatherSymbol(if(code.isFinite()) code.toInt() else -1,26.dp);ForecastChevron(open)
        }
        ForecastCompactBody(top={
            if(v==FV.Temp) Text(buildAnnotatedString {
                withStyle(SpanStyle(color=tint(v,day.max(v.key)),fontSize=28.sp,fontWeight=FontWeight.Bold)) { append(degrees(day.max(v.key),u)) }
                withStyle(SpanStyle(color=FText,fontSize=19.sp)) { append(" / "+degrees(day.min(v.key),u)) }
            },maxLines=1,overflow=TextOverflow.Ellipsis)
            else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(value(v,a,u),fontSize=23.sp,fontWeight=FontWeight.Bold,color=color,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                if(v==FV.Humidity) Text(uiText(R.string.ui_rosny_bod_754737bc)+degrees(dew,u),color=ForecastMuted,fontSize=11.sp,maxLines=1)
            }
        },scaleBar={ ForecastDayScale(day,bundle,v) },bottom={
            when(v) {
                FV.Temp->ForecastLine(uiText(R.string.ui_pocitovo_b1afb67c)+degrees(feelsPeriod(day,true),u)+" / "+degrees(feelsPeriod(day,false),u))
                FV.Rain->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Umbrella(probabilityColor(day.max("precipitation_probability")))
                    ForecastLine(value(FV.Prob,day.max("precipitation_probability"),u)+(if(day.sum("snowfall")>0) uiText(R.string.ui_sneh_bbd0d4e8)+weatherUnitNumber(u.precipitation.fromSnowCentimetres(day.sum("snowfall")),1)+" "+u.precipitation.snowSymbol else ""),FText)
                }
                FV.Wind->ForecastLine(uiText(R.string.ui_narazy_df599680)+value(FV.Gust,gust,u),fw(gust))
                FV.Visibility->ForecastLine(visibilityBand(a)?.name ?: uiText(R.string.ui_udaj_nedostupny_8bbc9c94),color)
                FV.Humidity->ForecastLine(humidityBand(a,dew)?.name ?: uiText(R.string.ui_udaj_nedostupny_8bbc9c94),color)
                FV.Pressure->ForecastLine(pressureBand(trend).name,tint(FV.Trend,trend))
                FV.Uv->ForecastLine(uvLabel(a),color)
                FV.Cloud->ForecastLine(forecastCloudName(a),FText)
                else->ForecastLine("")
            }
        },graphic={
            if(!a.isFinite()) Text("—",color=ForecastMuted,fontSize=24.sp) else when(v) {
                FV.Wind->TkCompass(direction,ForecastGreen,Modifier.size(52.dp))
                FV.Humidity->HumidityPercentDrop((a/100).coerceIn(0.0,1.0),color,Modifier.size(42.dp))
                FV.Visibility->TkEyeGraphic(color,Modifier.size(44.dp))
                FV.Pressure->if(trend.isFinite()) TkTrendGraphic(trend,tint(FV.Trend,trend),Modifier.size(44.dp)) else Text("—",color=ForecastMuted)
                FV.Uv->SummaryGlyph(SummaryIcon.Uv,uvColor(a),40.dp)
                FV.Cloud->CloudFill((a/100).coerceIn(0.0,1.0),color,null,0,Modifier.width(48.dp))
                FV.Rain->CloudFill(sqrt((a/20).coerceIn(0.0,1.0)),RainBlue,null,if(a>0) 1 else 0,Modifier.width(48.dp))
                else->SummaryGlyph(SummaryIcon.Temp,tint(FV.Temp,day.max("temperature_2m")),40.dp)
            }
        })
    }
}
@Composable private fun ForecastDayScale(day:ForecastDay,bundle:ForecastBundle,v:FV) {
    val all=bundle.days.flatMap { it.values(v.key) }
    val lo=if(v in listOf(FV.Temp,FV.Dew,FV.Pressure)) all.minOrNull() ?: 0.0 else 0.0
    val hi=when(v) {
        FV.Wind->bundle.days.flatMap { it.values("wind_speed_10m")+it.values("wind_gusts_10m") }.maxOrNull() ?: 1.0
        FV.Rain->bundle.days.map { it.sum(v.key) }.filter(Double::isFinite).maxOrNull() ?: 1.0
        FV.Cloud,FV.Humidity->100.0
        FV.Uv->11.0
        else->all.maxOrNull() ?: 1.0
    }
    Canvas(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))) {
        val radius=CornerRadius(size.height/2,size.height/2);val span=(hi-lo).coerceAtLeast(1.0)
        drawRect(FEdge)
        fun extent(x:Double)=(((x-lo)/span).coerceIn(0.0,1.0)*size.width).toFloat()
        fun fill(x:Double,c:Color) { if(x.isFinite() && x>lo) drawRoundRect(c,size=Size(extent(x).coerceAtLeast(size.height).coerceAtMost(size.width),size.height),cornerRadius=radius) }
        when(v) {
            FV.Wind->{
                val w=day.max("wind_speed_10m");val g=day.max("wind_gusts_10m")
                val top=listOf(w,g).filter(Double::isFinite).maxOrNull() ?: Double.NaN
                fill(top,if(g.isFinite() && (!w.isFinite() || g>w)) fw(g) else fw(w))
                if(w.isFinite() && w>0) {
                    val end=extent(w).coerceAtLeast(size.height).coerceAtMost(size.width)
                    if(g.isFinite() && g>w) drawRect(fw(w),size=Size(end,size.height)) else fill(w,fw(w))
                    if(g.isFinite() && g>w) drawLine(FText.copy(alpha=.75f),Offset(end,0f),Offset(end,size.height),1.dp.toPx())
                }
            }
            FV.Temp->{val a=day.min(v.key);val b=day.max(v.key);if(a.isFinite() && b.isFinite()) { val left=extent(a).coerceAtMost((size.width-size.height).coerceAtLeast(0f));val width=(extent(b)-left).coerceAtLeast(size.height).coerceAtMost(size.width-left);drawRoundRect(Brush.horizontalGradient(listOf(tempDotColor(a),tempDotColor(b))),Offset(left,0f),Size(width,size.height),radius) }}
            else->fill(aggregate(day,v),tint(v,aggregate(day,v),day.mean("dew_point_2m")))
        }
    }
}
private data class ForecastPlotRow(val v:FV,val key:String,val label:String)
private val ForecastPlotRows=FV.entries.filter { it !in listOf(FV.Feels,FV.Trend) }.map { ForecastPlotRow(it,it.key,it.label) }
private fun forecastPrecipColor(h:ForecastHour)=precipitationType(h.v("rain"),h.v("showers"),h.v("snowfall")) ?: RainBlue
@Composable private fun ForecastPrecipGlyph(h:ForecastHour?,dimension:androidx.compose.ui.unit.Dp,tint:Color?=null) {
    val snow=h?.v("snowfall") ?: Double.NaN
    val wet=h!=null && (h.v("rain")>0 || h.v("showers")>0)
    val color=tint ?: if(h==null) RainBlue else forecastPrecipColor(h)
    if(snow>0 && wet) Row(verticalAlignment=Alignment.CenterVertically) { SummaryGlyph(SummaryIcon.Rain,color,dimension/2);SummaryGlyph(SummaryIcon.Snow,color,dimension/2) }
    else SummaryGlyph(if(snow>0) SummaryIcon.Snow else SummaryIcon.Rain,color,dimension)
}
// onStep: a swipe over the name of the day; +1 opens the next day and -1 the previous one.
@Composable private fun ForecastExpandedDay(day:ForecastDay,bundle:ForecastBundle,u:WeatherUnits,onStep:(Int)->Unit,onClose:()->Unit) {
    Surface(Modifier.fillMaxSize().padding(horizontal=6.dp,vertical=4.dp),color=FPanel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FEdge)) {
        Column(Modifier.fillMaxSize().padding(horizontal=6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                ForecastDayTitle(day.date,bundle.zone,true,Modifier.weight(1f).horizontalSwipe(day.date,onStep).heightIn(min=48.dp).wrapContentHeight(Alignment.CenterVertically).padding(start=6.dp),17.sp,13.sp)
                TextButton(onClick=onClose) { Text(uiText(R.string.ui_zbalit_7f5c0896),color=RainBlue);ForecastChevron(true) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val rowHeight=((maxHeight-98.dp)/ForecastPlotRows.size).coerceAtLeast(40.dp)
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ForecastMeteogram(day,bundle.zone,u,rowHeight.value) }
            }
        }
    }
}
@Composable private fun ForecastPlotIcon(row:ForecastPlotRow,u:WeatherUnits,h:ForecastHour?) {
    var show by remember(row.v) { mutableStateOf(false) }
    val description=if(row.v==FV.Rain) uiText(R.string.ui_zrazky_value_vodny_ekvivalent_sneh_pri_kurzore_value_c076cdec, u.precipitation.liquidSymbol, u.precipitation.snowSymbol) else row.label+unit(row.v,u).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    Box(Modifier.fillMaxSize().clickable(role=Role.Button,onClickLabel=uiText(R.string.ui_nazov_a_jednotka_28e528ad),onClick={show=true}).semantics { contentDescription=description },contentAlignment=Alignment.Center) {
        when(row.v) {
            FV.Rain->ForecastPrecipGlyph(h,22.dp,Color.White)
            FV.Prob->Umbrella(Color.White)
            FV.Gust->GustGlyph(Color.White,22.dp)
            FV.Direction->TkCompass(0.0,Color.White,Modifier.size(26.dp))
            FV.Dew->DewPointGlyph(Color.White,22.dp)
            else->ForecastModeGlyph(row.v,Color.White)
        }
        DropdownMenu(expanded=show,onDismissRequest={show=false}) { Text(description,modifier=Modifier.widthIn(max=260.dp).padding(12.dp),fontSize=13.sp) }
    }
}
@Composable private fun ForecastMeteogram(day:ForecastDay,zone:ZoneId,u:WeatherUnits,rowDp:Float) {
    val tm=rememberTextMeasurer();val style=TextStyle(color=FText,fontSize=9.sp)
    var press by remember(day.date) { mutableStateOf<Int?>(null) };var drag by remember(day.date) { mutableStateOf<Int?>(null) }
    val selected=drag ?: press
    var now by remember(day.date) { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(day.date) { while (true) { now=Instant.now().epochSecond;delay(30_000L) } }
    val rows=ForecastPlotRows;val graphHeight=(98+rows.size*rowDp).dp
    fun clock(t:Long)=Instant.ofEpochSecond(t).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    BoxWithConstraints(Modifier.fillMaxWidth().height(graphHeight)) {
        val plotWidth=(maxWidth-48.dp).coerceAtLeast(1.dp)
        val duration=day.end-day.start
        Canvas(Modifier.fillMaxSize().semantics { contentDescription=uiText(R.string.ui_hodinovy_meteogram_value_zrazky_zahrnaju_sneh_vo_vodnom_ek_27b999fc, day.date) }
            .pointerInput(day) {
                fun nearest(px:Float):Int { val left=36.dp.toPx();val width=(size.width-left-12.dp.toPx()).coerceAtLeast(1f);val t=day.start+((px-left)/width).toDouble().coerceIn(0.0,1.0)*duration;return day.hours.indices.minByOrNull { abs(day.hours[it].time-t) } ?: 0 }
                detectTapGestures(onPress={ p->press=nearest(p.x);try { tryAwaitRelease() } finally { press=null } })
            }.pointerInput(day) {
                fun nearest(px:Float):Int { val left=36.dp.toPx();val width=(size.width-left-12.dp.toPx()).coerceAtLeast(1f);val t=day.start+((px-left)/width).toDouble().coerceIn(0.0,1.0)*duration;return day.hours.indices.minByOrNull { abs(day.hours[it].time-t) } ?: 0 }
                detectHorizontalDragGestures(onDragStart={drag=nearest(it.x)},onDragEnd={drag=null},onDragCancel={drag=null}) { c,_->c.consume();drag=nearest(c.position.x) }
            }) {
            val left=36.dp.toPx();val right=size.width-12.dp.toPx();val width=(right-left).coerceAtLeast(1f)
            val top=68.dp.toPx();val rh=rowDp.dp.toPx();val bottom=top+rows.size*rh
            fun x(t:Long)=left+((t-day.start).toDouble()/duration*width).toFloat()
            fun slot(h:Int):Long=when { h<=0->day.start;h>=24->day.end;else->day.date.atTime(h,0).atZone(zone).toEpochSecond() }
            fun text(s:String,xx:Float,yy:Float,c:Color=FText) { val m=tm.measure(s,style.copy(color=c));drawText(m,topLeft=Offset(xx.coerceIn(left,(right-m.size.width).coerceAtLeast(left)),yy)) }
            day.hours.forEach { h->drawRect(if(h.v("is_day")==1.0) Color(0xFF25303C) else Color(0xFF202833),Offset(x(h.time),40.dp.toPx()),Size((x(min(h.time+3600,day.end))-x(h.time)).coerceAtLeast(0f),28.dp.toPx())) }
            rows.forEachIndexed { ri,row->
                val y0=top+ri*rh;val y1=y0+rh
                drawRect(if(ri%2==0) Color(0xFF202833) else Color(0xFF2A3543),Offset(0f,y0),Size(size.width,rh))
                day.hours.filter { it.v("is_day")==1.0 }.forEach { h->drawRect(Color(0x06FFFFFF),Offset(x(h.time),y0),Size((x(min(h.time+3600,day.end))-x(h.time)).coerceAtLeast(0f),rh)) }
                drawRect(Color(0xFF18212C),Offset(0f,y0),Size(size.width,1.dp.toPx()))
                val v=row.v
                fun number(h:ForecastHour)=weatherUnitNumber(convert(v,h.v(row.key),u),decimals(v,u))
                fun col(h:ForecastHour)=if(v==FV.Rain) forecastPrecipColor(h) else tint(v,h.v(row.key),h.v("dew_point_2m"))
                val values=day.values(row.key).map { convert(v,it,u) }.filter(Double::isFinite)
                val low=if(v in listOf(FV.Rain,FV.Prob,FV.Wind,FV.Gust,FV.Cloud,FV.Uv,FV.Humidity)) 0.0 else values.minOrNull() ?: 0.0
                val high=(values.maxOrNull() ?: (low+1)).coerceAtLeast(low+1)
                fun y(a:Double)=y1-15.dp.toPx()-((a-low)/(high-low)*(rh-31.dp.toPx()).coerceAtLeast(1f)).toFloat()
                if(v==FV.Rain) {
                    // Precipitation: a bar for every hour, in the colour of its kind.
                    val bw=(x(day.start+3600)-x(day.start))*.7f;val base=y(0.0)
                    day.hours.forEach { h->val a=convert(v,h.v(row.key),u)
                        if(a.isFinite() && a>0) { val l=(x(h.time)-bw/2).coerceAtLeast(left);val r=(x(h.time)+bw/2).coerceAtMost(right);val yy=minOf(y(a),base-1.dp.toPx());drawRect(col(h),Offset(l,yy),Size((r-l).coerceAtLeast(0f),base-yy)) }
                    }
                } else if(v!=FV.Direction) {
                    var previous:ForecastHour?=null
                    day.hours.forEach { h->val a=convert(v,h.v(row.key),u);val p=previous
                        if(a.isFinite()) {
                            if(p!=null && h.time-p.time==3600L) drawLine(col(h),Offset(x(p.time),y(convert(v,p.v(row.key),u))),Offset(x(h.time),y(a)),1.4.dp.toPx())
                            drawCircle(col(h),1.3.dp.toPx(),Offset(x(h.time),y(a)));previous=h
                        } else previous=null
                    }
                }
                val selectedHour=selected?.let { day.hours[it] }
                fun label(h:ForecastHour,chosen:Boolean):String {
                    if(!chosen) return number(h)
                    if(v==FV.Rain) {
                        // At the cursor: snow in snow units, rain in liquid units, both for mixed precipitation.
                        val snow=h.v("snowfall");val wet=(h.v("rain").takeIf { it.isFinite() } ?: 0.0)+(h.v("showers").takeIf { it.isFinite() } ?: 0.0)
                        val snowText=weatherUnitNumber(u.precipitation.fromSnowCentimetres(snow),1)+" "+u.precipitation.snowSymbol
                        return if(snow>0 && wet>0) weatherUnitNumber(convert(v,wet,u),decimals(v,u))+" "+unit(v,u)+uiText(R.string.ui_sneh_bbd0d4e8)+snowText else if(snow>0) snowText else number(h)+" "+unit(v,u)
                    }
                    if(v==FV.Direction) return number(h)+"°"
                    return value(v,h.v(row.key),u).trim()
                }
                val chosenLabel=selectedHour?.let { label(it,true) }
                val chosenWidth=chosenLabel?.let { tm.measure(it,style).size.width } ?: 0
                var lastEnd=left-100f
                day.hours.forEachIndexed { i,h->val xx=x(h.time);val hour=Instant.ofEpochSecond(h.time).atZone(zone).hour
                    if((i==selected || hour%3==0) && (v!=FV.Pressure || i==selected)) {
                        val s=label(h,i==selected);val tw=tm.measure(s,style).size.width
                        val at=xx.coerceAtMost((right-tw).coerceAtLeast(left))
                        val chosenX=selectedHour?.let { x(it.time).coerceAtMost((right-chosenWidth).coerceAtLeast(left)) }
                        val overlap=i!=selected && chosenX!=null && at<chosenX+chosenWidth+3.dp.toPx() && at+tw>chosenX-3.dp.toPx()
                        if(!overlap && (i==selected || at>lastEnd+3.dp.toPx())) {
                            if(v==FV.Direction && h.v(row.key).isFinite()) {
                                val angle=(h.v(row.key)+180)*PI/180;val vec=Offset(sin(angle).toFloat(),-cos(angle).toFloat());val center=Offset(xx.coerceIn(left+6.dp.toPx(),right-6.dp.toPx()),y0+rh/2);val tip=center+vec*7.dp.toPx();val base=tip-vec*4.dp.toPx();val side=Offset(-vec.y,vec.x)*3.dp.toPx()
                                drawLine(ForecastGreen,center-vec*7.dp.toPx(),tip,1.5.dp.toPx());drawLine(ForecastGreen,tip,base+side,1.5.dp.toPx());drawLine(ForecastGreen,tip,base-side,1.5.dp.toPx())
                                if(i==selected) text(s,at,y0+2.dp.toPx(),RainBlue)
                            } else text(s,at,y0+3.dp.toPx(),if(i==selected) RainBlue else col(h))
                            lastEnd=at+tw
                        }
                    }
                }
            }
            for(k in 0..8) { val xx=x(slot(k*3));drawLine(FEdge.copy(alpha=.23f),Offset(xx,40.dp.toPx()),Offset(xx,bottom),.5.dp.toPx()) }
            drawRect(Color(0xFF18212C),Offset(0f,bottom),Size(size.width,1.dp.toPx()))
            for(k in 0..4) { val time=slot(k*6);val s=if(k==4) "24:00" else clock(time);text(s,x(time)-10.dp.toPx(),bottom+7.dp.toPx(),ForecastMuted) }
            fun cursor(time:Long,color:Color,s:String,badgeY:Float) {
                val xx=x(time);drawLine(color,Offset(xx,40.dp.toPx()),Offset(xx,bottom),1.dp.toPx())
                val m=tm.measure(s,style.copy(color=color));val pad=3.dp.toPx();val bx=(xx-m.size.width/2).coerceIn(left,(right-m.size.width).coerceAtLeast(left))
                drawRoundRect(Color(0xFF111A24),Offset(bx-pad,badgeY),Size(m.size.width+2*pad,m.size.height+2*pad),CornerRadius(3.dp.toPx()))
                drawText(m,topLeft=Offset(bx,badgeY+pad))
                drawLine(color,Offset(xx,badgeY+m.size.height+2*pad),Offset(xx,40.dp.toPx()),.7.dp.toPx())
            }
            if(now in day.start until day.end) cursor(now,FText,uiText(R.string.ui_teraz_e6989b57)+clock(now),0f)
            selected?.let { cursor(day.hours[it].time,RainBlue,clock(day.hours[it].time),20.dp.toPx()) }
        }
        var previous=-1000f
        day.hours.forEach { h->
            val fraction=(h.time-day.start).toFloat()/duration
            val dx=36.dp+plotWidth*fraction
            if(fraction in 0f..1f && dx.value-previous>=30f && dx+24.dp<=maxWidth) {
                previous=dx.value
                val wc=h.v("weather_code")
                Box(Modifier.offset(x=dx,y=40.dp).size(24.dp)) { WeatherSymbol(if(wc.isFinite()) wc.toInt() else -1,24.dp,isDay=h.v("is_day")==1.0) }
            }
            // On a day with snow, an hour of rain is marked too, with the drop.
            if((h.v("snowfall")>0 || (h.v(FV.Rain.key)>0 && day.hours.any { it.v("snowfall")>0 })) && (Instant.ofEpochSecond(h.time).atZone(zone).hour%3==0 || selected?.let { day.hours[it].time==h.time }==true)) {
                // Rain with snow: a drop beside a snowflake, each as large as a single mark.
                val mixed=h.v("snowfall")>0 && (h.v("rain")>0 || h.v("showers")>0);val markW=if(mixed) 24.dp else 12.dp
                val snowX=(dx-markW/2).coerceIn(36.dp,(maxWidth-24.dp).coerceAtLeast(36.dp))
                Box(Modifier.offset(x=snowX,y=(68+2*rowDp-13).dp).size(markW,12.dp)) { ForecastPrecipGlyph(h,markW) }
            }
        }
        rows.forEachIndexed { i,row->
            Box(Modifier.offset(y=(68+i*rowDp).dp).width(36.dp).height(rowDp.dp),contentAlignment=Alignment.Center) { ForecastPlotIcon(row,u,selected?.let { day.hours[it] }) }
        }
    }
}
