package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val SkyText=Color(0xFFE8EAF0)
private val SkyMuted=Color(0xFFBBC4D2)
private val SkyGrid=Color(0xFF4B586C)
private val SkyPanel=Color(0xFF202833)
private val SkySun=Color(0xFFFFD166)
private val SkyMoon=Color(0xFFAEB4C0)
private val SkyBlue=Color(0xFF4FC3F7)
private val SkyRise=Color(0xFF66D17A)
private val SkySet=Color(0xFFFF8F6B)
private val SkyLocale=appLocale()
private val SkyMin=LocalDate.of(1950,1,1)
private val SkyMax=LocalDate.of(2100,12,31)
private fun skyPath(d:String):Path=PathParser().parsePathString(d).toNodes().toPath()
private fun skyNumber(v:Double)=weatherUnitNumber(v,1)

private data class SkySunDay(val rise:Instant?,val set:Instant?,val seconds:Long)
private fun skySunDay(date:LocalDate,zone:ZoneId,lat:Double,lon:Double):SkySunDay {
    val start=date.atStartOfDay(zone).toInstant();val end=date.plusDays(1).atStartOfDay(zone).toInstant()
    var previousTime=start;var previous=SunMoonMath.position(start,lat,lon).sun+0.833
    var anyUp=previous>=0;var anyDown=previous<0;var rise:Instant?=null;var set:Instant?=null
    var t=start.plusSeconds(300)
    while(t<=end) {
        val v=SunMoonMath.position(t,lat,lon).sun+0.833
        if(v>=0) anyUp=true else anyDown=true
        val cross={ previousTime.plusSeconds((-previous/(v-previous)*(t.epochSecond-previousTime.epochSecond)).toLong()) }
        if(previous<0 && v>=0 && rise==null) rise=cross()
        if(previous>=0 && v<0 && set==null) set=cross()
        previous=v;previousTime=t;t=t.plusSeconds(300)
    }
    val seconds=when {
        rise!=null && set!=null && set>rise -> set.epochSecond-rise.epochSecond
        !anyDown -> 86400L
        !anyUp -> 0L
        else -> -1L
    }
    return SkySunDay(rise,set,seconds)
}
// Time the Moon spends above the horizon between local midnight and the next midnight, in seconds. Both parts are added when the Moon sets and rises again within the day.
private fun skyMoonUp(date:LocalDate,zone:ZoneId,lat:Double,lon:Double):Long {
    val start=date.atStartOfDay(zone).toInstant();val end=date.plusDays(1).atStartOfDay(zone).toInstant()
    var previousTime=start;var previous=SunMoonMath.position(start,lat,lon).moon;var total=0.0
    var t=start.plusSeconds(600)
    while(t<=end) {
        val v=SunMoonMath.position(t,lat,lon).moon;val step=(t.epochSecond-previousTime.epochSecond).toDouble()
        total+=when {
            previous>=0 && v>=0 -> step
            previous<0 && v<0 -> 0.0
            previous>=0 -> previous/(previous-v)*step
            else -> v/(v-previous)*step
        }
        previous=v;previousTime=t;t=t.plusSeconds(600)
    }
    return total.roundToLong()
}
private fun skyWindow(base:Long,span:Long,lat:Double,lon:Double):List<SkyPoint> {
    val out=ArrayList<SkyPoint>();var t=base-3600
    while(t<=base+span+7200) { out+=SunMoonMath.position(Instant.ofEpochSecond(t),lat,lon);t+=3600 }
    return out
}
private data class SkyMain(val time:Long,val kind:Int)
private fun skyMain(p:List<SkyPoint>):List<SkyMain> {
    val out=ArrayList<SkyMain>()
    for(i in 1 until p.size) {
        val a=p[i-1].illumination;val b=p[i].illumination
        if(i<p.size-1) {
            val c=p[i+1].illumination
            if(b<a && b<=c && b<30) out+=SkyMain(p[i].time.epochSecond,0)
            if(b>a && b>=c && b>70) out+=SkyMain(p[i].time.epochSecond,2)
        }
        if((a-50)*(b-50)<0) out+=SkyMain(p[i-1].time.epochSecond+((50-a)/(b-a)*3600).toLong(),if(b>a) 1 else 3)
    }
    return out
}
private fun skyMainName(kind:Int)=when(kind) { 0->uiText(R.string.ui_nov_3e630d29);1->uiText(R.string.ui_prva_stvrt_4ecd8721);2->uiText(R.string.ui_spln_cc87a236);else->uiText(R.string.ui_posledna_stvrt_7a12e0f9) }

private enum class SkyIcon { Noon,DayLength,TrendUp,TrendDown,MoonUp }
@Composable private fun SkyGlyph(icon:SkyIcon,color:Color,dimension:Dp=22.dp) {
    Canvas(Modifier.size(dimension)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            val s=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            when(icon) {
                SkyIcon.Noon->{ drawPath(skyPath("M2.5 19h19 M4 19a8 8 0 0 1 16 0 M12 3.5v2.5"),color,style=s);drawCircle(color,3f,Offset(12f,11f)) }
                SkyIcon.DayLength->{ drawCircle(color,9f,Offset(12f,12f),style=s);drawPath(skyPath("M12 7v5l3 2"),color,style=s) }
                SkyIcon.TrendUp->drawPath(skyPath("M3 17l6-6 4 4 8-8 M15 7h6v6"),color,style=s)
                SkyIcon.TrendDown->drawPath(skyPath("M3 7l6 6 4-4 8 8 M15 17h6v-6"),color,style=s)
                SkyIcon.MoonUp->{ drawPath(skyPath("M2.5 19h19"),color,style=s);drawCircle(color,4.5f,Offset(12f,10f),style=s) }
            }
        }
    }
}

private val SkyTipPosition=object:PopupPositionProvider {
    override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
        val x=(anchorBounds.right-popupContentSize.width).coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0))
        val y=(anchorBounds.top-popupContentSize.height-6).coerceAtLeast(0)
        return IntOffset(x,y)
    }
}
@OptIn(ExperimentalFoundationApi::class,ExperimentalMaterial3Api::class)
@Composable private fun SkyInfoButton(tip:String,title:String,modifier:Modifier,help:@Composable ()->Unit) {
    var showTip by remember { mutableStateOf(false) };var showHelp by remember { mutableStateOf(false) }
    Box(modifier.size(28.dp).combinedClickable(role=Role.Button,onClickLabel=uiText(R.string.ui_strucny_popis_e8d7e5f7),onLongClickLabel=uiText(R.string.ui_pomocnik_c68b7b84),onLongClick={ showHelp=true },onClick={ showTip=true }).semantics { contentDescription=uiText(R.string.ui_informacie_bc63e043)+title },contentAlignment=Alignment.Center) {
        Text("ⓘ",color=ForecastMuted,fontSize=16.sp)
        if(showTip) {
            LaunchedEffect(showTip) { delay(2600);showTip=false }
            Popup(popupPositionProvider=SkyTipPosition,onDismissRequest={ showTip=false },properties=PopupProperties(focusable=true)) {
                Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF10131A),contentColor=SkyText,border=BorderStroke(1.dp,SkyBlue),shadowElevation=4.dp) {
                    Text(tip,Modifier.widthIn(max=240.dp).padding(horizontal=10.dp,vertical=6.dp),fontSize=13.sp)
                }
            }
        }
    }
    if(showHelp) ModalBottomSheet(onDismissRequest={ showHelp=false },containerColor=SkyPanel,contentColor=SkyText) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            CompositionLocalProvider(LocalContentColor provides SkyText) { help() }
        }
    }
}

@Composable internal fun SunMoonScreen(location:Location,onBack:()->Unit) {
    val zone=remember(location.timezone) { runCatching { ZoneId.of(location.timezone) }.getOrElse { ZoneId.systemDefault() } }
    Column(Modifier.fillMaxSize().background(Color(0xFF323E4F)).verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SkyContent(zone,location)
        AttributionFooter()
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SkyContent(zone:ZoneId,l:Location) {
    var now by remember { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) { while(true) { now=Instant.now().epochSecond;delay(30_000) } }
    val span=3L*86400
    val minSec=SkyMin.atStartOfDay(zone).toEpochSecond().toDouble()
    val maxSec=(SkyMax.atStartOfDay(zone).toEpochSecond()-span).toDouble()
    fun today():LocalDate=Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    var date by remember(zone) { mutableStateOf(today()) }
    var startSec by remember(zone) { mutableDoubleStateOf(today().atStartOfDay(zone).toEpochSecond().toDouble()) }
    var cursor by remember(zone) { mutableStateOf<Long?>(null) }
    var picker by remember { mutableStateOf(false) }
    fun choose(day:LocalDate) { val v=if(day<SkyMin) SkyMin else if(day>SkyMax) SkyMax else day;date=v;startSec=v.atStartOfDay(zone).toEpochSecond().toDouble().coerceIn(minSec,maxSec);cursor=null }
    SkyChart(zone,l,now,startSec,{ seconds->startSec=(startSec+seconds).coerceIn(minSec,maxSec) },cursor,{ cursor=it },span)
    val pill=RoundedCornerShape(50)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).height(48.dp).clip(pill).background(SkyPanel).border(1.dp,SkyGrid,pill),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).alpha(if(date>SkyMin) 1f else .35f).clickable(enabled=date>SkyMin,role=Role.Button,onClickLabel=uiText(R.string.ui_predchadzajuci_den_e52ce20c),onClick={ choose(date.minusDays(1)) }),contentAlignment=Alignment.Center) { Icon(PerunIcons.Back,contentDescription=null,tint=SkyBlue,modifier=Modifier.size(22.dp)) }
            Text(date.format(DateTimeFormatter.ofPattern(appPattern("EEEE d. M. yyyy"),SkyLocale)).replaceFirstChar { it.titlecase(SkyLocale) },Modifier.weight(1f).clickable(role=Role.Button,onClickLabel=uiText(R.string.ui_vybrat_datum_767dda06),onClick={ picker=true }).padding(vertical=12.dp),color=SkyText,fontSize=14.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            Box(Modifier.size(48.dp).alpha(if(date<SkyMax) 1f else .35f).clickable(enabled=date<SkyMax,role=Role.Button,onClickLabel=uiText(R.string.ui_nasledujuci_den_3872abbe),onClick={ choose(date.plusDays(1)) }),contentAlignment=Alignment.Center) { Icon(PerunIcons.Back,contentDescription=null,tint=SkyBlue,modifier=Modifier.size(22.dp).rotate(180f)) }
        }
        Box(Modifier.height(48.dp).clip(pill).background(SkyPanel).border(1.dp,SkyGrid,pill).clickable(role=Role.Button,onClickLabel=uiText(R.string.ui_dnesok_e2299229),onClick={ choose(today()) }).padding(horizontal=18.dp),contentAlignment=Alignment.Center) { Text(uiText(R.string.ui_teraz_6172392c),color=SkyBlue,fontSize=14.sp,fontWeight=FontWeight.SemiBold) }
    }
    if(picker) {
        fun utc(day:LocalDate)=day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        fun fromUtc(ms:Long)=Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
        val state=rememberDatePickerState(initialSelectedDateMillis=utc(date),selectableDates=object:SelectableDates { override fun isSelectableDate(utcTimeMillis:Long)=fromUtc(utcTimeMillis) in SkyMin..SkyMax })
        DatePickerDialog(onDismissRequest={ picker=false },confirmButton={ TextButton(onClick={ state.selectedDateMillis?.let { choose(fromUtc(it)) };picker=false }) { Text(uiText(R.string.ui_vybrat_99496f1e)) } },dismissButton={ TextButton(onClick={ picker=false }) { Text(uiText(R.string.ui_zrusit_520e257a)) } }) { DatePicker(state=state) }
    }
    val solar=remember(date,zone,l.latitude,l.longitude) { skySunDay(date,zone,l.latitude,l.longitude) }
    fun clock(t:Instant?)=t?.atZone(zone)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"
    val noon=if(solar.rise!=null && solar.set!=null && solar.set>solar.rise) solar.rise.plusSeconds((solar.set.epochSecond-solar.rise.epochSecond)/2) else null
    val dayLength=if(solar.seconds>=0) uiText(R.string.ui_value_h_value_min_49a3b581, solar.seconds/3600, (solar.seconds%3600)/60) else "—"
    // Change of the day length against the day before; unknown when one of the two days has no usable length.
    val previousSolar=remember(date,zone,l.latitude,l.longitude) { skySunDay(date.minusDays(1),zone,l.latitude,l.longitude) }
    val dayKnown=solar.seconds>=0 && previousSolar.seconds>=0
    val dayChange=if(dayKnown) solar.seconds-previousSolar.seconds else 0L
    val dayChangeSpan=uiText(R.string.sky_min_s, abs(dayChange)/60, abs(dayChange)%60)
    val dayChangeValue=when { !dayKnown->"—";dayChange>0->"+"+dayChangeSpan;dayChange<0->"−"+dayChangeSpan;else->dayChangeSpan }
    val dayChangeTip=when { !dayKnown->uiText(R.string.sky_day_change_unknown);dayChange>0->uiText(R.string.sky_day_longer, dayChangeSpan);dayChange<0->uiText(R.string.sky_day_shorter, dayChangeSpan);else->uiText(R.string.sky_day_same) }
    val dayChangeColor=when { !dayKnown || dayChange==0L->SkyText;dayChange>0->SkyRise;else->SkySet }
    val events=remember(date,zone,l.latitude,l.longitude) { SunMoonMath.moonEvents(date,zone,l.latitude,l.longitude) }
    val moonUp=remember(date,zone,l.latitude,l.longitude) { skyMoonUp(date,zone,l.latitude,l.longitude) }
    val moonUpText=uiText(R.string.ui_value_h_value_min_49a3b581, moonUp/3600, (moonUp%3600)/60)
    val midday=date.atTime(12,0).atZone(zone).toInstant()
    val phasePoint=remember(date,l.latitude,l.longitude,zone) { SunMoonMath.position(midday,l.latitude,l.longitude) }
    val change=remember(date,l.latitude,l.longitude,zone) { SunMoonMath.position(midday.plusSeconds(43200),l.latitude,l.longitude).illumination-SunMoonMath.position(midday.minusSeconds(43200),l.latitude,l.longitude).illumination }
    val waxing=change>=0
    val illumination=phasePoint.illumination.roundToInt().coerceIn(0,100)
    val isToday=date==today()
    val suffix=if(isToday) uiText(R.string.ui_dnes_ef517dc4) else " · "+date.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        SkyTile(uiText(R.string.ui_slnko_d4e0d075)+suffix,uiText(R.string.ui_vychod_poludnie_zapad_a_dlzka_dna_slnka_8ab68e9b),uiText(R.string.ui_slnko_d4e0d075),Modifier.weight(1f).fillMaxHeight(),{
            HelpText(uiText(R.string.ui_vychod_zapad_a_dlzka_dna_sa_pocitaju_v_zariadeni_pre_lokal_87ba3c62))
            HelpText(uiText(R.string.ui_slnecne_poludnie_je_stred_medzi_vychodom_a_zapadom_okamih_7db4dbc5))
            HelpText(uiText(R.string.ui_pri_polarnom_dni_alebo_noci_vychod_a_zapad_nemusia_nastat_322de0fe))
            HelpText(uiText(R.string.sky_help_day_change))
        }) {
            SkyTileRow(uiText(R.string.ui_vychod_slnka_230bd00a)) { SkyIconBox { TkRiseArrow(true,SkyRise) };SkyValue(clock(solar.rise)) }
            SkyTileRow(uiText(R.string.ui_slnecne_poludnie_stred_medzi_vychodom_a_zapadom_164006d0)) { SkyIconBox { SkyGlyph(SkyIcon.Noon,SkySun) };SkyValue(clock(noon)) }
            SkyTileRow(uiText(R.string.ui_zapad_slnka_5426053b)) { SkyIconBox { TkRiseArrow(false,SkySet) };SkyValue(clock(solar.set)) }
            SkyTileRow(uiText(R.string.ui_dlzka_dna_bc509f34)) { SkyIconBox { SkyGlyph(SkyIcon.DayLength,SkyMuted) };SkyValue(dayLength) }
            SkyTileRow(dayChangeTip) { SkyIconBox { SkyGlyph(if(dayChange<0) SkyIcon.TrendDown else SkyIcon.TrendUp,if(dayChangeColor==SkyText) SkyMuted else dayChangeColor) };SkyValue(dayChangeValue,dayChangeColor) }
        }
        SkyTile(uiText(R.string.ui_mesiac_3ca578fb)+suffix,uiText(R.string.ui_vychod_zapad_faza_a_trend_mesiaca_424a0465),uiText(R.string.ui_mesiac_3ca578fb),Modifier.weight(1f).fillMaxHeight(),{
            HelpText(uiText(R.string.ui_vychod_a_zapad_mesiaca_sa_pocitaju_v_zariadeni_s_krokom_10_8e726efc))
            HelpText(uiText(R.string.sky_help_moon_up))
            HelpText(uiText(R.string.ui_faza_vychadza_z_uhlovej_vzdialenosti_mesiaca_od_slnka_v_po_6a6b7ba7))
            MoonPhaseHelp(l.latitude<0)
            HelpText(uiText(R.string.ui_trend_ukazuje_ci_osvetlenie_mesiaca_v_priebehu_24_hodin_ok_4b1eaa5d))
        }) {
            SkyTileRow(uiText(R.string.ui_vychod_mesiaca_a17e2d3c)) { SkyIconBox { TkRiseArrow(true,SkyRise) };SkyValue(clock(events.first)) }
            SkyTileRow(uiText(R.string.sky_moon_up, moonUpText)) { SkyIconBox { SkyGlyph(SkyIcon.MoonUp,SkyMoon) };SkyValue(moonUpText) }
            SkyTileRow(uiText(R.string.ui_zapad_mesiaca_c401b9f3)) { SkyIconBox { TkRiseArrow(false,SkySet) };SkyValue(clock(events.second)) }
            SkyTileRow(SunMoonMath.phaseName(phasePoint.phase)+uiText(R.string.ui_osvetlenie_38b6bd7b)+skyNumber(phasePoint.illumination)+" %") { SkyIconBox { MoonGraphic(illumination,waxing,l.latitude<0,Modifier.size(24.dp)) };SkyValue("$illumination %") }
            SkyTileRow(if(waxing) uiText(R.string.ui_mesiac_dorasta_osvetlenie_rastie_a5ae864c) else uiText(R.string.ui_mesiac_cuva_osvetlenie_klesa_d31ace13)) { SkyIconBox { SkyGlyph(if(waxing) SkyIcon.TrendUp else SkyIcon.TrendDown,if(waxing) SkyRise else SkySet) };SkyValue(if(waxing) uiText(R.string.ui_rastie_a5341d12) else uiText(R.string.ui_cuva_c55fd1d0),if(waxing) SkyRise else SkySet) }
        }
    }
}
@Composable private fun SkyIconBox(content:@Composable () -> Unit) { Box(Modifier.width(26.dp),contentAlignment=Alignment.Center) { content() } }
@Composable private fun SkyValue(text:String,color:Color=SkyText) { Text(text,color=color,fontSize=16.sp,fontWeight=FontWeight.Normal,maxLines=1,softWrap=false) }
@Composable private fun SkyTileRow(tip:String,content:@Composable RowScope.()->Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min=26.dp).clickable(role=Role.Button,onClickLabel=uiText(R.string.ui_popis_af1eeac7),onClick={ show=true }).semantics { contentDescription=tip },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp),content=content)
        if(show) {
            LaunchedEffect(tip) { delay(2600);show=false }
            Popup(alignment=Alignment.BottomStart,onDismissRequest={ show=false },properties=PopupProperties(focusable=true)) {
                Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF10131A),contentColor=SkyText,border=BorderStroke(1.dp,SkyBlue),shadowElevation=4.dp) {
                    Text(tip,Modifier.widthIn(max=220.dp).padding(horizontal=10.dp,vertical=6.dp),fontSize=13.sp)
                }
            }
        }
    }
}
@Composable private fun SkyTile(title:String,tip:String,helpTitle:String,modifier:Modifier,help:@Composable ()->Unit,rows:@Composable ColumnScope.()->Unit) {
    OutlinedCard(modifier,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,SkyGrid),colors=CardDefaults.outlinedCardColors(containerColor=SkyPanel,contentColor=SkyText)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min=36.dp).padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),color=SkyText,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                SkyInfoButton(tip,helpTitle,Modifier,help)
            }
            Column(Modifier.padding(start=12.dp,end=12.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(0.dp),content=rows)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SkyChart(zone:ZoneId,l:Location,now:Long,startSec:Double,move:(Double)->Unit,cursor:Long?,select:(Long?)->Unit,span:Long) {
    val tm=rememberTextMeasurer()
    var showSun by remember { mutableStateOf(true) };var showMoon by remember { mutableStateOf(true) }
    val start=startSec.toLong()
    val lat=l.latitude;val lon=l.longitude
    val base=(start/3600)*3600
    val window=remember(base,lat,lon) { skyWindow(base,span,lat,lon) }
    val mains=remember(window) { skyMain(window) }
    val leftMargin=38.dp;val rightMargin=38.dp
    var canvasWidth by remember { mutableFloatStateOf(800f) };val density=LocalDensity.current
    val margins=with(density) { (leftMargin+rightMargin).toPx() };val plotPixels=(canvasWidth-margins).coerceAtLeast(1f)
    val currentMove by rememberUpdatedState(move);val currentStart by rememberUpdatedState(start)
    val scroll=rememberScrollableState { delta->currentMove(-delta.toDouble()*span/plotPixels);delta }
    val selectedPoint=cursor?.let { SunMoonMath.position(Instant.ofEpochSecond(it),lat,lon) }
    Surface(color=SkyPanel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,SkyGrid)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=28.dp).scrollable(scroll,Orientation.Horizontal),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(uiText(R.string.ui_vyska_na_oblohe_df779ae6),Modifier.weight(1f),color=SkyText,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf(Triple(uiText(R.string.ui_slnko_d4e0d075),SkySun,showSun),Triple(uiText(R.string.ui_mesiac_3ca578fb),SkyMoon,showMoon)).forEach { (label,color,on)->
                        Row(Modifier.heightIn(min=28.dp).alpha(if(on) 1f else .4f).clickable(role=Role.Checkbox,onClickLabel=uiText(R.string.ui_prepnut_8ce4129e)+label,onClick={
                            if(label==uiText(R.string.ui_slnko_d4e0d075)) { if(!on || showMoon) showSun=!on } else { if(!on || showSun) showMoon=!on }
                        }),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Canvas(Modifier.size(12.dp,8.dp)) { drawLine(color,Offset(0f,size.height/2),Offset(size.width,size.height/2),2.dp.toPx(),pathEffect=if(label==uiText(R.string.ui_mesiac_3ca578fb)) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx())) else null) }
                            Text(label,fontSize=11.sp,color=color,maxLines=1,softWrap=false)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(234.dp).onSizeChanged { canvasWidth=it.width.toFloat() }.semantics { contentDescription=uiText(R.string.ui_graf_vysky_slnka_a_mesiaca_posun_cez_okraj_dlazdice_odcita_8ce96158) }.pointerInput(span,leftMargin,rightMargin) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false);val left=leftMargin.toPx();val right=(size.width-rightMargin.toPx()).coerceAtLeast(left+1);val top=48.dp.toPx();val bottom=size.height-18.dp.toPx();val w=right-left
                        if(down.position.x in left..right && down.position.y in top..bottom) {
                            fun pick(px:Float) { select(currentStart+(((px-left)/w).coerceIn(0f,1f)*span).toLong()) }
                            down.consume();pick(down.position.x)
                            try { do { val event=awaitPointerEvent();val c=event.changes.firstOrNull { it.id==down.id } ?: break;if(!c.pressed) break;c.consume();pick(c.position.x) } while(true) } finally { select(null) }
                        } else {
                            var previous=down.position.x
                            val drag=awaitHorizontalTouchSlopOrCancellation(down.id) { c,over->currentMove(-over.toDouble()*span/w);previous=c.position.x;c.consume() }
                            if(drag!=null) horizontalDrag(drag.id) { c->currentMove(-(c.position.x-previous).toDouble()*span/w);previous=c.position.x;c.consume() }
                        }
                    }
                }) {
                    val style=TextStyle(color=ForecastMuted,fontSize=9.sp)
                    val left=leftMargin.toPx();val right=(size.width-rightMargin.toPx()).coerceAtLeast(left+1);val width=right-left;val top=48.dp.toPx();val bottom=size.height-18.dp.toPx()
                    fun x(t:Long)=left+((t-start).toDouble()/span*width).toFloat()
                    fun y(v:Double):Float=bottom-(((v+90.0)/180.0)*(bottom-top)).toFloat()
                    fun text(s:String,xx:Float,yy:Float,c:Color=ForecastMuted) { val r=tm.measure(s,style.copy(color=c));drawText(r,topLeft=Offset(xx.coerceIn(0f,(size.width-r.size.width).coerceAtLeast(0f)),yy)) }
                    fun smooth(value:(SkyPoint)->Double):Path {
                        val path=Path()
                        window.forEachIndexed { i,p->
                            val px=x(p.time.epochSecond);val py=y(value(p))
                            if(i==0) path.moveTo(px,py) else { val q=window[i-1];val qx=x(q.time.epochSecond);val qy=y(value(q));path.quadraticBezierTo(qx,qy,(qx+px)/2,(qy+py)/2) }
                        }
                        window.lastOrNull()?.let { path.lineTo(x(it.time.epochSecond),y(value(it))) }
                        return path
                    }
                    clipRect(left,top,right,bottom) {
                        window.forEach { p->if(p.sun>=-0.833) { val a=x(p.time.epochSecond);val b=x(p.time.epochSecond+3600);drawRect(Color(0x0CFFFFFF),Offset(a,top),Size((b-a).coerceAtLeast(0f),bottom-top)) } }
                        for(v in listOf(-90,-45,0,45,90)) drawLine(if(v==0) ForecastMuted.copy(alpha=.8f) else SkyGrid.copy(alpha=.5f),Offset(left,y(v.toDouble())),Offset(right,y(v.toDouble())),if(v==0) .9.dp.toPx() else .5.dp.toPx())
                        if(showSun) {
                            window.zipWithNext().forEach { (a,b)->
                                val area=Path();area.moveTo(x(a.time.epochSecond),y(0.0));area.lineTo(x(a.time.epochSecond),y(a.sun));area.lineTo(x(b.time.epochSecond),y(b.sun));area.lineTo(x(b.time.epochSecond),y(0.0));area.close()
                                drawPath(area,SkySun.copy(alpha=.12f))
                            }
                            drawPath(smooth { it.sun },SkySun,style=Stroke(2.2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                        }
                        if(showMoon) drawPath(smooth { it.moon },SkyMoon,style=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round,pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),3.dp.toPx()))))
                    }
                    drawLine(ForecastMuted.copy(alpha=.7f),Offset(left,top),Offset(left,bottom),.7.dp.toPx())
                    for(v in listOf(-90,-45,0,45,90)) text("$v°",0f,y(v.toDouble())-5.dp.toPx())
                    var day=Instant.ofEpochSecond(start).atZone(zone).toLocalDate();val today=Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
                    repeat(5) {
                        val a=day.atStartOfDay(zone).toEpochSecond();val b=day.plusDays(1).atStartOfDay(zone).toEpochSecond()
                        if(a<start+span && b>start) {
                            val x0=x(a).coerceIn(left,right);val x1=x(b).coerceIn(left,right);drawLine(SkyGrid.copy(alpha=.5f),Offset(x0,top),Offset(x0,bottom),.7.dp.toPx())
                            val name=when(day) { today->uiText(R.string.ui_dnes_ce1bed34);today.plusDays(1)->uiText(R.string.ui_zajtra_c3e86eb2);else->day.format(DateTimeFormatter.ofPattern("EEE",SkyLocale)) }
                            val full=name+" · "+day.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()));val s=if(tm.measure(full,style).size.width<=x1-x0-2.dp.toPx()) full else day.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()))
                            val r=tm.measure(s,style);if(r.size.width<=x1-x0) drawText(r,topLeft=Offset((x0+x1-r.size.width)/2,bottom+5.dp.toPx()))
                        }
                        day=day.plusDays(1)
                    }
                    if(showMoon) mains.filter { it.time in start..start+span }.forEach { m->
                        val p=SunMoonMath.position(Instant.ofEpochSecond(m.time),lat,lon)
                        val cx=x(m.time);val cy=y(p.moon).coerceIn(top,bottom);val r=5.dp.toPx();val tl=Offset(cx-r,cy-r);val sz=Size(2*r,2*r);val south=lat<0
                        drawCircle(SkyPanel,r,Offset(cx,cy))
                        when(m.kind) {
                            0->drawCircle(Color(0xFF2B3550),r,Offset(cx,cy))
                            2->drawCircle(SkyText,r,Offset(cx,cy))
                            1->drawArc(SkyText,if(south) 90f else -90f,180f,true,tl,sz)
                            else->drawArc(SkyText,if(south) -90f else 90f,180f,true,tl,sz)
                        }
                        drawCircle(SkyMoon,r,Offset(cx,cy),style=Stroke(1.3.dp.toPx()))
                        val label=tm.measure(skyMainName(m.kind),style.copy(color=SkyMoon))
                        drawText(label,topLeft=Offset((cx-label.size.width/2).coerceIn(left,(right-label.size.width).coerceAtLeast(left)),(cy-r-label.size.height-1.dp.toPx()).coerceAtLeast(top)))
                    }
                    fun marker(time:Long,c:Color,s:String,badgeY:Float) {
                        if(time in start..start+span) {
                            val xx=x(time);val r=tm.measure(s,style.copy(color=c));val pad=3.dp.toPx();val bx=(xx-r.size.width/2).coerceIn(left,(right-r.size.width).coerceAtLeast(left))
                            drawLine(c,Offset(xx,top),Offset(xx,bottom),1.dp.toPx())
                            drawRoundRect(Color(0xFF111A24),Offset(bx-pad,badgeY),Size(r.size.width+2*pad,r.size.height+2*pad),CornerRadius(3.dp.toPx()))
                            drawText(r,topLeft=Offset(bx,badgeY+pad));drawLine(c,Offset(xx,badgeY+r.size.height+2*pad),Offset(xx,top),.7.dp.toPx())
                        }
                    }
                    marker(now,SkyText,uiText(R.string.ui_teraz_e6989b57)+Instant.ofEpochSecond(now).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),0f)
                    selectedPoint?.let { p->
                        marker(p.time.epochSecond,SkyBlue,p.time.atZone(zone).format(DateTimeFormatter.ofPattern(appPattern("d. M. HH:mm"),appLocale())),20.dp.toPx())
                        val xx=x(p.time.epochSecond);val pad=3.dp.toPx();val gap=2.dp.toPx();var prevBottom=top-1000f
                        listOfNotNull(if(showSun) Pair(p.sun,SkySun) else null,if(showMoon) Pair(p.moon,SkyMoon) else null).sortedBy { y(it.first) }.forEach { (value,c)->
                            val yy=y(value).coerceIn(top,bottom)
                            val r=tm.measure(skyNumber(value)+"°",style.copy(color=c))
                            val w=r.size.width+2*pad;val h=r.size.height+2*pad
                            val cy=maxOf((yy-h/2).coerceIn(top,(bottom-h).coerceAtLeast(top)),prevBottom+gap);prevBottom=cy+h
                            val cx=if(xx+6.dp.toPx()+w<=right) xx+6.dp.toPx() else xx-6.dp.toPx()-w
                            drawCircle(c,3.dp.toPx(),Offset(xx,yy))
                            drawRoundRect(Color(0xFF111A24),Offset(cx,cy),Size(w,h),CornerRadius(3.dp.toPx()))
                            drawRoundRect(c,Offset(cx,cy),Size(w,h),CornerRadius(3.dp.toPx()),style=Stroke(1.dp.toPx()))
                            drawText(r,topLeft=Offset(cx+pad,cy+pad))
                        }
                    }
                }
                SkyInfoButton(uiText(R.string.ui_vyska_slnka_a_mesiaca_nad_obzorom_v_stupnoch_38e23465),uiText(R.string.ui_vyska_na_oblohe_df779ae6),Modifier.align(Alignment.BottomEnd).offset(y=6.dp)) {
                    HelpText(uiText(R.string.ui_graf_ukazuje_vysku_slnka_a_mesiaca_nad_obzorom_v_stupnoch_97656c01))
                    HelpRow(SkySun,uiText(R.string.ui_slnko_d4e0d075),uiText(R.string.ui_plna_zlta_ciara_svetlejsia_vypln_medzi_ciarou_a_obzorom_sv_ad4af0e2))
                    HelpRow(SkyMoon,uiText(R.string.ui_mesiac_3ca578fb),uiText(R.string.ui_bodkovana_siva_ciara_male_disky_na_nej_oznacuju_hlavne_faz_16549c66))
                    HelpText(uiText(R.string.ui_ovladanie_v_grafe_podrz_a_posuvaj_prst_pre_odcitanie_hodno_fa1c527d))
                    HelpText(uiText(R.string.ui_nizko_presne_geometricke_vypocty_v_zariadeni_nie_meranie_g_473d1e10))
                }
            }
        }
    }
}
