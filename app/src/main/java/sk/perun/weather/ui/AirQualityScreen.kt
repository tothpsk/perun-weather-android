package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val AqText=Color(0xFFE8EAF0)
private val AqPanel=Color(0xFF202833)
private val AqEdge=Color(0xFF4B586C)
private val AqBlue=Color(0xFF4FC3F7)
private val AqNeutral=Color(0xFF8294AD)
private val AqColors=listOf(Color(0xFF50D7B7),Color(0xFF9CCC65),Color(0xFFFFCA28),Color(0xFFFF8A65),Color(0xFFEF5350),Color(0xFFAB47BC))
private fun aqColor(value:Double,scale:AirQualityScale)=airQualityLevel(value,scale)?.let { AqColors[it.band] } ?: AqNeutral
private fun aqNum(value:Double,decimals:Int)=weatherUnitNumber(value,decimals)

private enum class AqMode(val label:String,val symbol:String,val key:String) {
    Index(uiText(R.string.ui_index_kvality_ovzdusia_d9bab98d),uiText(R.string.ui_aqi_43cb31d0),""),Pm25(uiText(R.string.ui_jemne_castice_pm2_5_d3f95b57),uiText(R.string.ui_pm2_5_7d02b735),"pm2_5"),Pm10(uiText(R.string.ui_prachove_castice_pm10_7e459420),uiText(R.string.ui_pm10_dca32eb0),"pm10"),
    No2(uiText(R.string.ui_oxid_dusicity_no2_6cae0699),uiText(R.string.ui_no2_13442efb),"nitrogen_dioxide"),O3(uiText(R.string.ui_ozon_o3_fb7c4ac5),uiText(R.string.ui_o3_1903ec79),"ozone"),So2(uiText(R.string.ui_oxid_siricity_so2_7ae55399),uiText(R.string.ui_so2_220bf934),"sulphur_dioxide"),
    Co(uiText(R.string.ui_oxid_uholnaty_co_beacefa6),uiText(R.string.ui_co_fc8842c6),"carbon_monoxide"),Pollen(uiText(R.string.ui_pel_ca4a713f),uiText(R.string.ui_pel_ca4a713f),"")
}
private val AqModes=AqMode.entries.toList()
private enum class AqKind { Aqi,Gas,Pollen }
private data class AqRow(val key:String,val symbol:String,val name:String,val kind:AqKind)
private data class AqDay(val date:LocalDate,val indices:List<Int>,val start:Long,val end:Long)

private fun aqRows(mode:AqMode,scale:AirQualityScale):List<AqRow> =
    if(mode==AqMode.Pollen) AirPollenTypes.map { AqRow(it.key,it.name,it.name,AqKind.Pollen) }
    else listOf(AqRow(scale.apiVariable,uiText(R.string.ui_aqi_43cb31d0),scale.label,AqKind.Aqi))+AirPollutants.map { AqRow(it.key,it.symbol,it.name,AqKind.Gas) }
private fun aqDays(d:AirQualityData):List<AqDay> = d.times.indices.groupBy { Instant.ofEpochSecond(d.times[it]).atZone(d.zone).toLocalDate() }.toSortedMap()
    .map { (date,ix)->AqDay(date,ix,date.atStartOfDay(d.zone).toEpochSecond(),date.plusDays(1).atStartOfDay(d.zone).toEpochSecond()) }
private fun aqValue(d:AirQualityData,row:AqRow,i:Int,gas:AirGasUnit):Double = if(row.kind==AqKind.Gas) airConvert(row.key,d.at(row.key,i),gas) else d.at(row.key,i)
private fun aqColorAt(d:AirQualityData,row:AqRow,i:Int,scale:AirQualityScale):Color = when(row.kind) {
    AqKind.Aqi->aqColor(d.at(row.key,i),scale)
    AqKind.Gas->{ val sub=d.at("${scale.apiVariable}_${row.key}",i);if(sub.isFinite()) aqColor(sub,scale) else AqNeutral }
    AqKind.Pollen->{ val level=airPollenLevel(AirPollenTypes.first { it.key==row.key },d.at(row.key,i));if(level<0) AqNeutral else PollenLevelColors[level] }
}
private fun aqMinTop(row:AqRow,scale:AirQualityScale,gas:AirGasUnit):Double = when(row.kind) {
    AqKind.Aqi->if(scale==AirQualityScale.European) 60.0 else 150.0
    AqKind.Pollen->AirPollenTypes.first { it.key==row.key }.elevated*2
    AqKind.Gas->airConvert(row.key,when(row.key) { "pm2_5"->25.0;"pm10"->50.0;"nitrogen_dioxide"->40.0;"ozone"->120.0;"sulphur_dioxide"->100.0;else->4000.0 },gas)
}
private fun aqDecimals(row:AqRow,gas:AirGasUnit,value:Double):Int = if(row.kind==AqKind.Gas) (if(gas==AirGasUnit.Ppb && row.key=="carbon_monoxide") 2 else if(value<10.0) 1 else 0) else 0
private fun aqUnit(row:AqRow,gas:AirGasUnit):String = when(row.kind) { AqKind.Aqi->"";AqKind.Pollen->uiText(R.string.ui_zrn_m3_2736b71a);AqKind.Gas->airUnitLabel(row.key,gas) }
private fun aqDominantPollen(day:AqDay,d:AirQualityData):Pair<AirPollenType,Double>? = AirPollenTypes.mapNotNull { type->
    day.indices.map { d.at(type.key,it) }.filter { it.isFinite() }.maxOrNull()?.takeIf { it>0 }?.let { type to it }
}.maxByOrNull { it.second }

private data class AqSummary(val ok:Boolean,val text:String,val unit:String,val color:Color,val fraction:Double,val lines:List<Pair<String,Color>>,val complete:Boolean)
private fun aqSummary(day:AqDay,d:AirQualityData,mode:AqMode,scale:AirQualityScale,gas:AirGasUnit,weekMax:Double):AqSummary {
    val none=AqSummary(false,"—","",AqNeutral,0.0,listOf(uiText(R.string.ui_udaj_nie_je_dostupny_f3105a65) to ForecastMuted),false)
    if(mode==AqMode.Pollen) {
        val dominant=aqDominantPollen(day,d)
        val known=AirPollenTypes.flatMap { type->day.indices.map { d.at(type.key,it) } }.filter { it.isFinite() }
        val complete=AirPollenTypes.all { type->day.indices.all { d.at(type.key,it).isFinite() } }
        if(dominant==null) return if(known.isEmpty()) none else AqSummary(true,"0",uiText(R.string.ui_zrn_m3_2736b71a),PollenLevelColors[0],0.0,listOf(uiText(R.string.ui_ziadny_pel_v_dostupnych_udajoch_3edaedc5) to ForecastMuted),complete)
        val (type,value)=dominant;val level=airPollenLevel(type,value).coerceAtLeast(0);val color=PollenLevelColors[level]
        return AqSummary(true,aqNum(value,0),uiText(R.string.ui_zrn_m3_2736b71a),color,value/type.high,listOf((type.name+" · "+AirPollenLevelNames[level]) to color),complete)
    }
    val row=if(mode==AqMode.Index) AqRow(scale.apiVariable,uiText(R.string.ui_aqi_43cb31d0),scale.label,AqKind.Aqi) else AqRow(mode.key,mode.symbol,mode.label,AqKind.Gas)
    val values=day.indices.map { aqValue(d,row,it,gas) }
    val peak=values.indices.filter { values[it].isFinite() }.maxByOrNull { values[it] } ?: return none
    val value=values[peak];val hour=day.indices[peak];val complete=values.all { it.isFinite() }
    if(row.kind==AqKind.Aqi) {
        val level=airQualityLevel(value,scale);val color=aqColor(value,scale)
        val lines=listOf((level?.label ?: uiText(R.string.ui_udaj_nedostupny_8bbc9c94)) to color)
        return AqSummary(true,aqNum(value,0),"",color,value/(if(scale==AirQualityScale.European) 100.0 else 300.0),lines,complete)
    }
    val sub=d.at("${scale.apiVariable}_${row.key}",hour);val color=if(sub.isFinite()) aqColor(sub,scale) else AqNeutral
    val unit=aqUnit(row,gas)
    val lines=listOf((if(sub.isFinite()) airQualityLevel(sub,scale)?.label ?: "" else uiText(R.string.ui_bez_ciastkoveho_indexu_eb453b41)) to color)
    return AqSummary(true,aqNum(value,aqDecimals(row,gas,value)),unit,color,if(weekMax>0) value/weekMax else 0.0,lines,complete)
}

@Composable private fun AqModeGlyph(mode:AqMode,color:Color,dimension:Dp) {
    GlyphIcon(when(mode) { AqMode.Index->Glyph.AqIndex;AqMode.Pm25->Glyph.AqPm25;AqMode.Pm10->Glyph.AqPm10;AqMode.No2->Glyph.AqNo2;AqMode.O3->Glyph.AqO3;AqMode.So2->Glyph.AqSo2;AqMode.Co->Glyph.AqCo;AqMode.Pollen->Glyph.AqPollen },color,dimension)
}
@Composable private fun AqPlantGlyph(key:String,color:Color,dimension:Dp) {
    GlyphIcon(when(key) { "alder_pollen"->Glyph.PlantAlder;"birch_pollen"->Glyph.PlantBirch;"grass_pollen"->Glyph.PlantGrass;"mugwort_pollen"->Glyph.PlantMugwort;"olive_pollen"->Glyph.PlantOlive;else->Glyph.PlantRagweed },color,dimension)
}
@Composable private fun AqChevron(open:Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val path=Path().apply { moveTo(size.width*.25f,size.height*(if(open) .6f else .4f));lineTo(size.width*.5f,size.height*(if(open) .4f else .6f));lineTo(size.width*.75f,size.height*(if(open) .6f else .4f)) }
        drawPath(path,AqBlue,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
    }
}
private fun aqDayName(date:LocalDate,zone:ZoneId):String {
    val today=Instant.now().atZone(zone).toLocalDate()
    return when(date) { today->uiText(R.string.ui_dnes_ce1bed34);today.plusDays(1)->uiText(R.string.ui_zajtra_c3e86eb2);else->date.format(DateTimeFormatter.ofPattern("EEEE",appLocale())) }
}
@Composable private fun AqDayTitle(date:LocalDate,zone:ZoneId,dateBold:Boolean,modifier:Modifier,nameSize:TextUnit=14.sp,dateSize:TextUnit=11.sp) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight=FontWeight.Bold)) { append(aqDayName(date,zone)) }
        withStyle(SpanStyle(color=ForecastMuted,fontSize=dateSize,fontWeight=if(dateBold) FontWeight.Bold else FontWeight.Normal)) { append(" · "+date.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()))) }
    },color=AqText,fontSize=nameSize,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=modifier)
}
@Composable private fun AqCompactBody(top:@Composable ()->Unit,scaleBar:@Composable ()->Unit,bottom:@Composable ()->Unit,graphic:@Composable ()->Unit) {
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
@Composable private fun AqDayScale(fraction:Double,color:Color) {
    Canvas(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))) {
        drawRect(AqEdge)
        val width=(fraction.coerceIn(0.0,1.0)*size.width).toFloat().coerceAtLeast(size.height).coerceAtMost(size.width)
        if(fraction>0) drawRoundRect(color,size=Size(width,size.height),cornerRadius=CornerRadius(size.height/2,size.height/2))
    }
}
@Composable private fun AqDayRow(day:AqDay,d:AirQualityData,mode:AqMode,air:AirPreferences,weekMax:Double,onClick:()->Unit) {
    val summary=aqSummary(day,d,mode,air.scale,air.gas,weekMax)
    val plant=if(mode==AqMode.Pollen) aqDominantPollen(day,d)?.first else null
    Column(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel=uiText(R.string.ui_rozbalit_den_9601108c),onClick=onClick).padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=28.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            AqDayTitle(day.date,d.zone,false,Modifier.weight(1f))
            if(!summary.complete) Text("•",color=Color(0xFFFFB74D),modifier=Modifier.semantics { contentDescription=uiText(R.string.ui_neuplne_udaje_b649939a) })
            AqChevron(false)
        }
        AqCompactBody(top={
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color=summary.color,fontSize=28.sp,fontWeight=FontWeight.Bold)) { append(summary.text) }
                if(summary.unit.isNotEmpty()) withStyle(SpanStyle(color=ForecastMuted,fontSize=13.sp)) { append(" "+summary.unit) }
            },maxLines=1,overflow=TextOverflow.Ellipsis)
        },scaleBar={ AqDayScale(summary.fraction,summary.color) },bottom={
            Column { summary.lines.take(1).forEach { (text,color)->Text(text,color=color,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis) } }
        },graphic={
            if(plant!=null) Box(Modifier.semantics { contentDescription=uiText(R.string.ui_najvyssia_denna_koncentracia_pelu_8c321cba)+plant.name }) { AqPlantGlyph(plant.key,summary.color,40.dp) }
            else AqModeGlyph(mode,if(summary.ok) summary.color else ForecastMuted,40.dp)
        })
    }
}
@Composable private fun AqPlotIcon(row:AqRow,gas:AirGasUnit) {
    var show by remember(row.key) { mutableStateOf(false) }
    val unit=aqUnit(row,gas)
    val description=row.name+(if(unit.isNotEmpty()) " · "+unit else "")
    Box(Modifier.fillMaxSize().clickable(role=Role.Button,onClickLabel=uiText(R.string.ui_nazov_a_jednotka_28e528ad),onClick={ show=true }).semantics { contentDescription=description },contentAlignment=Alignment.Center) {
        if(row.kind==AqKind.Pollen) AqPlantGlyph(row.key,AqText,22.dp)
        else AqModeGlyph(AqModes.firstOrNull { it.key==row.key } ?: AqMode.Index,AqText,22.dp)
        DropdownMenu(expanded=show,onDismissRequest={ show=false }) { Text(description,modifier=Modifier.widthIn(max=260.dp).padding(12.dp),fontSize=13.sp) }
    }
}
@Composable private fun AqExpandedDay(day:AqDay,d:AirQualityData,mode:AqMode,air:AirPreferences,location:Location,onStep:(Int)->Unit,onClose:()->Unit) {
    val step by rememberUpdatedState(onStep)
    val rows=aqRows(mode,air.scale)
    val daylight=remember(day,location.latitude,location.longitude) {
        buildList<Pair<Long,Long>> {
            var time=day.start
            while(time<day.end) {
                val end=minOf(time+300,day.end)
                if(SunMoonMath.position(Instant.ofEpochSecond(time+(end-time)/2),location.latitude,location.longitude).sun>=-0.833) add(time to end)
                time=end
            }
        }
    }
    Surface(Modifier.fillMaxSize().padding(horizontal=6.dp,vertical=4.dp),color=AqPanel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,AqEdge)) {
        Column(Modifier.fillMaxSize().padding(horizontal=6.dp)) {
            // A swipe over the header opens the next day (to the left) or the previous one (to the right).
            Row(Modifier.fillMaxWidth().pointerInput(day.date) { var total=0f;detectHorizontalDragGestures(onDragStart={ total=0f },onDragEnd={ if(total<-40.dp.toPx()) step(1) else if(total>40.dp.toPx()) step(-1) }) { c,amount->c.consume();total+=amount } },verticalAlignment=Alignment.CenterVertically) {
                AqDayTitle(day.date,d.zone,true,Modifier.weight(1f).padding(start=6.dp),17.sp,13.sp)
                TextButton(onClick=onClose) { Text(uiText(R.string.ui_zbalit_7f5c0896),color=AqBlue);AqChevron(true) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val rowHeight=((maxHeight-70.dp)/rows.size).coerceAtLeast(52.dp)
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { AqMeteogram(day,d,rows,air,rowHeight.value,daylight) }
            }
        }
    }
}
@Composable private fun AqMeteogram(day:AqDay,d:AirQualityData,rows:List<AqRow>,air:AirPreferences,rowDp:Float,daylight:List<Pair<Long,Long>>) {
    val tm=rememberTextMeasurer();val style=TextStyle(color=AqText,fontSize=9.sp)
    var press by remember(day.date) { mutableStateOf<Int?>(null) };var drag by remember(day.date) { mutableStateOf<Int?>(null) }
    val selected=drag ?: press
    var now by remember(day.date) { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(day.date) { while(true) { now=Instant.now().epochSecond;delay(30_000L) } }
    val graphHeight=(70+rows.size*rowDp).dp
    val duration=(day.end-day.start).toFloat()
    fun clock(t:Long)=Instant.ofEpochSecond(t).atZone(d.zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    Box(Modifier.fillMaxWidth().height(graphHeight)) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription=uiText(R.string.ui_hodinovy_prehlad_value_svetlejsie_pozadie_oznacuje_den_pod_ca158a37, day.date) }
            .pointerInput(day) {
                fun nearest(px:Float):Int { val left=36.dp.toPx();val width=(size.width-left-12.dp.toPx()).coerceAtLeast(1f);val t=day.start+((px-left)/width).toDouble().coerceIn(0.0,1.0)*duration
                    return day.indices.indices.minByOrNull { kotlin.math.abs(d.times[day.indices[it]]-t) } ?: 0 }
                detectTapGestures(onPress={ p->press=nearest(p.x);try { tryAwaitRelease() } finally { press=null } })
            }
            .pointerInput(day) {
                fun nearest(px:Float):Int { val left=36.dp.toPx();val width=(size.width-left-12.dp.toPx()).coerceAtLeast(1f);val t=day.start+((px-left)/width).toDouble().coerceIn(0.0,1.0)*duration
                    return day.indices.indices.minByOrNull { kotlin.math.abs(d.times[day.indices[it]]-t) } ?: 0 }
                detectHorizontalDragGestures(onDragStart={ drag=nearest(it.x) },onDragEnd={ drag=null },onDragCancel={ drag=null }) { c,_->c.consume();drag=nearest(c.position.x) }
            }) {
            val left=36.dp.toPx();val right=size.width-12.dp.toPx();val width=(right-left).coerceAtLeast(1f)
            val top=40.dp.toPx();val rh=rowDp.dp.toPx();val bottom=top+rows.size*rh
            fun x(t:Long)=left+(t-day.start).toFloat()/duration*width
            fun text(s:String,xx:Float,yy:Float,c:Color=AqText) { val m=tm.measure(s,style.copy(color=c));drawText(m,topLeft=Offset(xx.coerceIn(left,(right-m.size.width).coerceAtLeast(left)),yy)) }
            val selectedIndex=selected?.let { day.indices.getOrNull(it) }
            rows.forEachIndexed { ri,row->
                val y0=top+ri*rh
                drawRect(if(ri%2==0) Color(0xFF202833) else Color(0xFF2A3543),Offset(0f,y0),Size(size.width,rh))
                daylight.forEach { (a,b)->drawRect(Color(0x0CFFFFFF),Offset(x(a),y0),Size((x(b)-x(a)).coerceAtLeast(0f),rh)) }
                drawRect(Color(0xFF18212C),Offset(0f,y0),Size(size.width,1.dp.toPx()))
                val values=day.indices.map { aqValue(d,row,it,air.gas) }
                val high=max(values.filter { it.isFinite() }.maxOrNull() ?: 0.0,aqMinTop(row,air.scale,air.gas))
                // The scale starts at the bottom of the row, as in the expanded day of Forecast.
                val span=(rh-19.dp.toPx()).coerceAtLeast(1f)
                fun ya(v:Double)=y0+rh-3.dp.toPx()-(v/high*span).toFloat()
                var previous:Int?=null
                day.indices.forEachIndexed { pos,i->
                    val v=values[pos]
                    if(v.isFinite()) {
                        val color=aqColorAt(d,row,i,air.scale);val point=Offset(x(d.times[i]),ya(v));val p=previous
                        if(p!=null && d.times[i]-d.times[day.indices[p]]==3600L) drawLine(color,Offset(x(d.times[day.indices[p]]),ya(values[p])),point,1.4.dp.toPx())
                        drawCircle(color,1.3.dp.toPx(),point);previous=pos
                    } else previous=null
                }
                fun label(pos:Int):String { val v=values[pos];return aqNum(v,aqDecimals(row,air.gas,v)) }
                val unit=aqUnit(row,air.gas)
                val chosenLabel=selected?.takeIf { values.getOrNull(it)?.isFinite()==true }?.let { label(it)+(if(unit.isEmpty()) "" else " "+unit) }
                val chosenWidth=chosenLabel?.let { tm.measure(it,style).size.width } ?: 0
                val chosenX=selected?.let { x(d.times[day.indices[it]]).coerceAtMost(right-chosenWidth).coerceAtLeast(left) }
                var lastEnd=left-100f
                day.indices.forEachIndexed { pos,i->
                    val xx=x(d.times[i]);val hour=Instant.ofEpochSecond(d.times[i]).atZone(d.zone).hour
                    if(hour%3==0 && values[pos].isFinite() && pos!=selected) {
                        val s=label(pos);val tw=tm.measure(s,style).size.width;val at=xx.coerceAtMost(right-tw).coerceAtLeast(left)
                        val overlap=chosenX!=null && at<chosenX+chosenWidth+3.dp.toPx() && at+tw>chosenX-3.dp.toPx()
                        if(!overlap && at>=lastEnd+3.dp.toPx()) { text(s,at,y0+3.dp.toPx(),aqColorAt(d,row,i,air.scale));lastEnd=at+tw }
                    }
                }
                if(chosenLabel!=null && chosenX!=null) text(chosenLabel,chosenX,y0+2.dp.toPx(),AqBlue)
            }
            for(k in 0..8) { val xx=x(day.start+k*3*3600L);drawLine(AqEdge.copy(alpha=.23f),Offset(xx,top),Offset(xx,bottom),.5.dp.toPx()) }
            drawRect(Color(0xFF18212C),Offset(0f,bottom),Size(size.width,1.dp.toPx()))
            for(k in 0..4) { val time=day.start+k*6*3600L;val s=if(k==4) "24:00" else clock(time);text(s,x(time)-10.dp.toPx(),bottom+7.dp.toPx(),ForecastMuted) }
            fun cursor(time:Long,color:Color,s:String,badgeY:Float) {
                val xx=x(time);drawLine(color,Offset(xx,top),Offset(xx,bottom),1.dp.toPx())
                val m=tm.measure(s,style.copy(color=color));val pad=3.dp.toPx();val bx=(xx-m.size.width/2).coerceIn(left,(right-m.size.width).coerceAtLeast(left))
                drawRoundRect(Color(0xFF111A24),Offset(bx-pad,badgeY),Size(m.size.width+2*pad,m.size.height+2*pad),CornerRadius(3.dp.toPx()))
                drawText(m,topLeft=Offset(bx,badgeY+pad));drawLine(color,Offset(xx,badgeY+m.size.height+2*pad),Offset(xx,top),.7.dp.toPx())
            }
            if(now in day.start until day.end) cursor(now,AqText,uiText(R.string.ui_teraz_e6989b57)+clock(now),0f)
            selectedIndex?.let { cursor(d.times[it],AqBlue,clock(d.times[it]),20.dp.toPx()) }
        }
        rows.forEachIndexed { i,row->
            Box(Modifier.offset(y=(40f+i*rowDp).dp).width(36.dp).height(rowDp.dp),contentAlignment=Alignment.Center) { AqPlotIcon(row,air.gas) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AirQualityScreen(location:Location,onBack:()->Unit,initialMode:String="") {
    val air=rememberAirPreferences()
    var mode by remember(initialMode) { mutableStateOf(if(initialMode=="pollen") AqMode.Pollen else AqMode.Index) }
    var data by remember(location) { mutableStateOf<AirQualityData?>(null) }
    var error by remember(location) { mutableStateOf<String?>(null) }
    var loading by remember(location) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var open by remember(location) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(location,refresh) {
        loading=true;error=null
        try { data=loadAirQuality(location) } catch(e:CancellationException) { throw e } catch(e:Exception) { error=e.message ?: uiText(R.string.ui_chyba_b9f17311) }
        finally { loading=false }
    }
    val d=data
    val days=remember(d) { d?.let { aqDays(it) }.orEmpty() }
    val expanded=days.firstOrNull { it.date==open }
    BackHandler(enabled=expanded!=null) { open=null }
    if(expanded!=null && d!=null) {
        AqExpandedDay(expanded,d,mode,air,location,{ step->days.getOrNull(days.indexOf(expanded)+step)?.let { open=it.date } }) { open=null }
        return
    }
    val weekMax=if(d==null || mode==AqMode.Index || mode==AqMode.Pollen) 0.0 else days.maxOfOrNull { day->day.indices.map { airConvert(mode.key,d.at(mode.key,it),air.gas) }.filter { it.isFinite() }.maxOrNull() ?: 0.0 } ?: 0.0
    PullToRefreshBox(isRefreshing=loading,onRefresh={ if(!loading) { NetCache.invalidate();loading=true;refresh++ } },modifier=Modifier.fillMaxSize().background(Color(0xFF323E4F))) {
        LazyColumn(Modifier.fillMaxSize().horizontalSwipe(Unit) { step -> mode=AqModes[(AqModes.indexOf(mode)+step).coerceIn(0,AqModes.lastIndex)] }.padding(horizontal=12.dp),contentPadding=PaddingValues(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {
                error?.let { Text(uiText(R.string.ui_udaje_sa_nepodarilo_nacitat_value_to_neznamena_dobru_kvali_7eb3f020, it),color=Color(0xFFFFB74D)) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    AqModes.forEach { m->
                        val active=mode==m;val shape=RoundedCornerShape(50)
                        Box(Modifier.size(48.dp).clip(shape).background(if(active) AqBlue else AqPanel).border(1.dp,if(active) AqBlue else AqEdge,shape)
                            .selectable(selected=active,role=Role.RadioButton,onClick={ mode=m }).semantics { contentDescription=m.label },contentAlignment=Alignment.Center) {
                            AqModeGlyph(m,if(active) Color(0xFF101F39) else AqText,22.dp)
                        }
                    }
                }
            }
            item { Text(mode.label,modifier=Modifier.fillMaxWidth().padding(top=6.dp,bottom=2.dp),color=AqText,fontSize=20.sp,fontWeight=FontWeight.Bold) }
            if(d!=null) items(days,key={ it.date.toString() }) { day->
                Surface(color=AqPanel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,AqEdge)) { AqDayRow(day,d,mode,air,weekMax) { open=day.date } }
            }
            item { AttributionFooter() }
        }
    }
}
