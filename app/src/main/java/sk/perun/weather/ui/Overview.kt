package sk.perun.weather.ui

import sk.perun.weather.R

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val TileBackground=Color(0xFF202833)
private val OverviewBackground=Color(0xFF323E4F)
private val TileBorder=Color(0xFF4B586C)
private val OverviewText=Color(0xFFE8EAF0)
private val Grey=Color(0xFF5B6577)
private val SK=appLocale()
// Returning to the app reloads the screen when the shown data is older than this; the background worker has usually refreshed the cache by then.
private const val ResumeRefreshMillis=5*60_000L
// Sea tiles get this long to arrive, so that the first view of a location appears at once.
private const val MarineWaitMillis=3000L
internal val AlertColors=mapOf("neutral" to Grey,"yellow" to Color(0xFFFDD835),"orange" to Color(0xFFFB8C00),"red" to Color(0xFFE53935))
internal val AlertRank=mapOf("neutral" to 0,"yellow" to 1,"orange" to 2,"red" to 3)
private val AlertLevels=listOf("neutral","yellow","orange","red")
private val AlertSurface=mapOf("yellow" to (Color(0xFF302C25) to Color(0xFF806C42)),"orange" to (Color(0xFF33271C) to Color(0xFFA0622A)),"red" to (Color(0xFF341F22) to Color(0xFFA8393D)))
private val AlertLocalNames=mapOf("Thunderstorm risk" to uiText(R.string.ui_riziko_burok_8fa6514a),"Strong wind gusts" to uiText(R.string.ui_silne_narazy_vetra_7b7b006f),"Heavy precipitation" to uiText(R.string.ui_silne_zrazky_2630cc03),"Extreme heat" to uiText(R.string.ui_extremne_teplo_cdcd5041),"Extreme cold" to uiText(R.string.ui_extremny_chlad_d4aeb237))
private fun alertLevelName(level:String)=when(level) { "red"->uiText(R.string.ui_cervena_f1d8f5c8);"orange"->uiText(R.string.ui_oranzova_ab35bb41);"yellow"->uiText(R.string.ui_zlta_64273c3a);else->uiText(R.string.ui_informacia_b277d884) }
private fun finite0(v:Double)=if(v.isFinite()) v else 0.0
private fun Dp.inset(units:Float,box:Float=24f):Dp=this*(units/box)
private fun Instant?.clock(z:ZoneId)=this?.atZone(z)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--"
private fun dayState(w:OverviewData):Boolean? {
    val now=w.time.atZone(w.zone).toInstant();val r=w.sunrise;val s=w.sunset
    return if(r!=null && s!=null && s.isAfter(r)) !now.isBefore(r) && now.isBefore(s) else null
}
private fun cloudName(v:Double)=when {
    !v.isFinite() || v !in 0.0..100.0 -> ""
    v<12.5 -> uiText(R.string.ui_jasno_e78eee64)
    v<37.5 -> uiText(R.string.ui_prevazne_jasno_7331ba47)
    v<62.5 -> uiText(R.string.ui_polooblacno_83f2c354)
    v<87.5 -> uiText(R.string.ui_prevazne_oblacno_e5122573)
    else -> uiText(R.string.ui_zamracene_829159bb)
}
private fun moonPhase(date:Instant):String {
    val t=(date.toEpochMilli()/86400000.0+2440587.5-2451545.0)/36525.0
    fun norm(x:Double)=((x%360.0)+360.0)%360.0
    val lp=norm(218.3164477+481267.88123421*t)
    val d=norm(297.8501921+445267.1114034*t)*PI/180
    val m=norm(357.5291092+35999.0502909*t)*PI/180
    val p=norm(134.9633964+477198.8675055*t)*PI/180
    val lon=lp+6.289*sin(p)-1.274*sin(2*d-p)+0.658*sin(2*d)-0.186*sin(m)-0.059*sin(2*p-2*d)-0.057*sin(p-2*d+m)+0.053*sin(p+2*d)+0.046*sin(2*d-m)+0.041*sin(p-m)-0.035*sin(d)-0.031*sin(p+m)
    val i=(norm(lon-norm(280.4665+36000.7698*t))/45).roundToInt()%8
    return listOf(uiText(R.string.ui_nov_3e630d29),uiText(R.string.ui_dorastajuci_kosacik_8e90f090),uiText(R.string.ui_prva_stvrt_4ecd8721),uiText(R.string.ui_dorastajuci_mesiac_0d9af424),uiText(R.string.ui_spln_cc87a236),uiText(R.string.ui_cuvajuci_mesiac_4a6a004c),uiText(R.string.ui_posledna_stvrt_7a12e0f9),uiText(R.string.ui_cuvajuci_kosacik_03e67d5f))[i]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Overview(location:Location) {
    val preferences=rememberForecastPreferences()
    val units=preferences.units
    val alertSource=preferences.alertSource
    val model=preferences.model
    val selected=rememberOverviewTiles()
    var data by remember(location,model,alertSource) { mutableStateOf<OverviewData?>(null) }
    var extra by remember(location,model,alertSource) { mutableStateOf<OverviewExtra?>(null) }
    var nowcast by remember(location) { mutableStateOf<NcNow?>(null) }
    var loading by remember(location,model,alertSource) { mutableStateOf(true) }
    var failed by remember(location,model,alertSource) { mutableStateOf(false) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var help by remember(location,model,alertSource) { mutableStateOf<HelpSpec?>(null) }
    var loadedAt by remember(location,model,alertSource) { mutableLongStateOf(0L) }
    val marineState=rememberMarineOverviewState(location,refresh)
    val marine=marineState.data
    val marinePending by rememberUpdatedState(marineState.pending)
    val resumedAt=rememberOnResume { System.currentTimeMillis() }
    LaunchedEffect(resumedAt) { if(!loading && loadedAt>0L && resumedAt-loadedAt>ResumeRefreshMillis) refresh++ }
    LaunchedEffect(location,refresh,alertSource,model) {
        loading=true
        failed=false
        try {
            coroutineScope {
                val more=async { loadOverviewExtra(location,model=model) }
                val weather=loadOverview(location,alertSource=alertSource,model=model)
                val details=more.await()
                if(data==null) withTimeoutOrNull(MarineWaitMillis) { snapshotFlow { marinePending }.first { !it } }
                data=weather
                extra=details
                loadedAt=System.currentTimeMillis()
                failed=false
            }
        } catch(e:CancellationException) { throw e } catch(e:Exception) { failed=true }
        finally { loading=false }
    }
    val nowcastSource=rememberLocationSourcePreferences(location).nowcastSource
    LaunchedEffect(location,refresh,nowcastSource) {
        while(isActive) {
            nowcast=ncTry { ncLoad(location,nowcastSource) }
            delay(600000)
        }
    }
    help?.let { spec ->
        ModalBottomSheet(onDismissRequest={ help=null },containerColor=TileBackground,contentColor=OverviewText) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(spec.title,style=MaterialTheme.typography.titleLarge)
                spec.body(this)
            }
        }
    }
    CompositionLocalProvider(LocalContentColor provides OverviewText) {
        PullToRefreshBox(isRefreshing=loading,onRefresh={ if(!loading) { NetCache.invalidate();loading=true;refresh++ } },modifier=Modifier.fillMaxSize().background(OverviewBackground)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(failed) Text(uiText(R.string.ui_udaje_sa_nepodarilo_obnovit_potiahni_nadol_a_skus_znova_zo_cd3e0669),color=Color(0xFFFFB74D))
                if(data==null && !loading) Text(uiText(R.string.ui_predpoved_nie_je_dostupna_obnovis_ju_potiahnutim_nadol_b6b1e2f8))
                val marineCells=rememberMarineCells(marine,units) { help=it }
                data?.let { w ->
                    // The alerts have a fixed place in the header and are always shown.
                    Hero(w,units,true) { help=it }
                    DaySummary(w,units)
                    val n=nowcast
                    if(n!=null && n.wet) NowcastTile(n,w.zone) { help=it }
                    if(n!=null && n.storm.active) StormTile(n.storm) { help=it }
                    Tiles(w,extra,units,selected,marineCells) { help=it }
                }
                if(data==null && !loading) TileRows(marineCells)
                if(data!=null || marine!=null) AttributionFooter(fallbackNote(data,extra),"meteo" in alertSourceSet(alertSource) && hasMeteoAlarm(location.countryCode))
            }
        }
    }
}

// Variables the selected model did not provide and best_match supplied instead, named as on the long forecast charts.
private fun fallbackNote(w:OverviewData?,ex:OverviewExtra?):String? {
    val labels=(w?.fallback.orEmpty()+ex?.fallback.orEmpty()).mapNotNull { key->
        when(key) {
            "rain","showers","snowfall" -> LongMetric.Rain.label
            "wind_direction_10m" -> LongMetric.Wind.label
            "weather_code","is_day" -> null
            else -> LongMetric.entries.firstOrNull { it.key==key }?.let { if(it==LongMetric.Prob) uiText(R.string.ui_pravdepodobnost_zrazok_c9b0a84a) else it.label }
        }
    }.distinct()
    // The same wording and order as in the footer of Ensemble.
    val order=LongMetric.entries.map { if(it==LongMetric.Prob) uiText(R.string.ui_pravdepodobnost_zrazok_c9b0a84a) else it.label }
    return if(labels.isEmpty()) null else uiText(R.string.long_fallback_note,labels.sortedBy { label->order.indexOf(label).let { i->if(i<0) order.size else i } }.joinToString(", "))
}

@Composable private fun Attribution() {
    val dim=ForecastMuted.copy(alpha=.75f)
    val year=LocalDate.now().year
    val uri=LocalUriHandler.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top=6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Open-Meteo.com · CC BY 4.0",Modifier.weight(1f,fill=false).clickable { runCatching { uri.openUri("https://open-meteo.com/") } },fontSize=12.sp,color=ForecastMuted,textDecoration=TextDecoration.Underline)
            Spacer(Modifier.weight(1f))
            Text(uiText(R.string.ui_dalsie_zdroje_0e84da7e)+(if(open) "▾" else "▸"),Modifier.clickable { open=!open }.padding(start=12.dp,top=6.dp,bottom=6.dp),fontSize=12.sp,color=ForecastMuted)
        }
        if(open) Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf(
                uiText(R.string.ui_predpoved_20fdfd9d) to "Open-Meteo.com · CC BY 4.0",
                uiText(R.string.ui_ovzdusie_a_pel_89f8f42d) to "Contains modified Copernicus Atmosphere Monitoring Service information $year",
                uiText(R.string.ui_vystrahy_d88f15ec) to "MeteoAlarm · KDE FOSS Public Alert Server",
                uiText(R.string.ui_radar_a_burky_4218b3cf) to "LibreWXR",
            ).forEach { (label,source) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label,Modifier.width(100.dp),fontSize=11.sp,color=dim)
                    Text(source,Modifier.weight(1f),fontSize=11.sp,color=ForecastMuted)
                }
            }
            Text(uiText(R.string.ui_europska_komisia_ani_ecmwf_nezodpovedaju_za_pouzitie_infor_0e8269e6),fontSize=11.sp,color=dim,modifier=Modifier.padding(top=2.dp))
        }
    }
}

@Composable internal fun InfoDot(color:Color,label:String,ring:Boolean=false,onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=Modifier.size(36.dp)) {
        Canvas(Modifier.size(11.dp).semantics { contentDescription=uiText(R.string.ui_informacie_value_f076eb88, label) }) {
            if(ring) drawCircle(color,style=Stroke(1.6.dp.toPx())) else drawCircle(color)
        }
    }
}

private fun heroGlow(code:Int,day:Boolean):Color = when {
    code<0 -> Color(0xFF8294AD)
    code==0 || code in 1..2 -> if(day) Color(0xFFFFD166) else Color(0xFF8FA8FF)
    code>=95 -> Color(0xFFB388FF)
    code in setOf(71,73,75,77,85,86) -> Color(0xFFE8EAF0)
    code>=51 -> Color(0xFF4FC3F7)
    else -> Color(0xFF9FB0C7)
}

@Composable private fun HeroArt(w:OverviewData,drift:Float,pulse:Float,animated:Boolean) {
    val day=dayState(w) ?: w.isDayFlag
    val glow=heroGlow(w.code,day)
    Box(Modifier.size(136.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r=size.minDimension/2f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha=.20f+.14f*pulse),glow.copy(alpha=.07f),Color.Transparent),center,r),r,center)
        }
        Box(Modifier.graphicsLayer { translationX=drift*density;translationY=-drift*.6f*density;rotationZ=drift*.3f }) {
            WeatherSymbol(w.code,104.dp,day,animated=animated)
        }
    }
}

@Composable private fun HeroWeather(w:OverviewData) {
    val resolver=LocalContext.current.contentResolver
    val enabled=remember(resolver) { Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f }
    if(enabled) {
        val transition=rememberInfiniteTransition(label="overviewWeatherMotion")
        val drift by transition.animateFloat(-4f,4f,infiniteRepeatable(tween(3200,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="weatherDrift")
        val pulse by transition.animateFloat(0f,1f,infiniteRepeatable(tween(4200,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="weatherGlow")
        HeroArt(w,drift,pulse,true)
    } else HeroArt(w,0f,.5f,false)
}

@Composable private fun Hero(w:OverviewData,u:WeatherUnits,showAlerts:Boolean,onHelp:(HelpSpec)->Unit) {
    val navigation=LocalOverviewNavigation.current
    val groups=remember(w.alerts) { groupAlertPresentation(w.alerts.sortedByDescending { AlertRank[it.level] ?: 0 }) }
    Box(Modifier.fillMaxWidth().padding(top=6.dp,bottom=2.dp)) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            val weekday=w.time.format(DateTimeFormatter.ofPattern("EEEE",SK)).replaceFirstChar { it.titlecase(SK) }
            Text(weekday+" "+w.time.format(DateTimeFormatter.ofPattern(appPattern("d. MMMM · HH:mm"),SK)),style=MaterialTheme.typography.bodyMedium,color=ForecastMuted)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(overviewNumber(u.temperature.fromCelsius(w.temperature),"°"),Modifier.clickable { navigation.openForecast("temperature") },fontSize=84.sp,lineHeight=88.sp,fontWeight=FontWeight.ExtraBold)
                HeroWeather(w)
            }
            Column(Modifier.fillMaxWidth(.5f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(overviewCondition(w.code),Modifier.clickable { navigation.openForecast("temperature") },style=MaterialTheme.typography.headlineSmall)
                FeelsRow(w.apparent,w.temperature,u,{ navigation.openForecast("temperature") },onHelp)
            }
        }
        if(showAlerts && groups.isNotEmpty()) AlertChips(groups,{ navigation.openAlerts() },Modifier.align(Alignment.BottomEnd).fillMaxWidth(.5f).padding(bottom=12.5.dp))
    }
}

@Composable private fun AlertChips(groups:List<AlertDisplayGroup>,onOpen:()->Unit,modifier:Modifier) {
    val rest=groups.drop(1)
    val restRank=if(rest.isEmpty()) 0 else rest.maxOf { g -> g.items.maxOf { AlertRank[it.level] ?: 0 } }
    Column(modifier) {
        AlertChip(groups.first(),onOpen,rest.size,AlertColors[AlertLevels[restRank.coerceIn(0,3)]] ?: Grey)
    }
}

@Composable private fun AlertChip(g:AlertDisplayGroup,onOpen:()->Unit,more:Int,moreColor:Color) {
    val first=g.items.first()
    val level=g.items.maxByOrNull { AlertRank[it.level] ?: 0 }?.level ?: first.level
    val color=AlertColors[level] ?: Grey
    val surface=AlertSurface[level]
    val shape=RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(surface?.first ?: TileBackground).border(1.dp,surface?.second ?: TileBorder,shape).clickable(onClick=onOpen).padding(horizontal=10.dp,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp),
    ) {
        AlertGlyph(alertKind(first.event+" "+g.label),color,20.dp)
        Column(Modifier.weight(1f)) {
            Text(AlertLocalNames[g.label] ?: g.label,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=16.sp)
            Text(alertLevelName(level)+if(first.local) uiText(R.string.ui_odhad_5dde2d79) else "",fontSize=11.sp,color=color,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(more>0) Box(Modifier.border(1.dp,moreColor,RoundedCornerShape(8.dp)).padding(horizontal=6.dp,vertical=2.dp)) {
            Text("+$more",fontSize=12.sp,fontWeight=FontWeight.Bold,color=moreColor)
        }
    }
}

@Composable private fun SummaryCell(parts:List<Triple<SummaryIcon,String,Color>>) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        parts.forEachIndexed { i,(icon,text) ->
            if(i>0) Spacer(Modifier.width(8.dp))
            SummaryGlyph(icon,Color.White,18.dp)
            Spacer(Modifier.width(4.dp))
            Text(text,fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Color.White,maxLines=1,softWrap=false)
        }
    }
}

@Composable private fun SummaryDivider() { Box(Modifier.width(1.dp).height(22.dp).background(TileBorder)) }

@Composable private fun DaySummary(w:OverviewData,u:WeatherUnits) {
    val d=w.days.firstOrNull() ?: return
    val hasLiquid=d.liquid>0
    val hasSnow=d.snow>0
    val liquidTint=precipitationType(d.rain,d.showers,0.0) ?: RainBlue
    val windDecimals=if(u.wind==WindSpeedUnit.MetresPerSecond) 1 else 0
    val precipParts=ArrayList<Triple<SummaryIcon,String,Color>>()
    if(hasLiquid) precipParts.add(Triple(SummaryIcon.Rain,overviewNumber(u.precipitation.fromMillimetres(d.liquid)," "+u.precipitation.liquidSymbol,if(u.precipitation==PrecipitationUnit.Imperial || d.liquid<.1) 2 else 1),liquidTint))
    if(hasSnow) precipParts.add(Triple(SummaryIcon.Snow,overviewNumber(u.precipitation.fromSnowCentimetres(d.snow)," "+u.precipitation.snowSymbol,if(u.precipitation==PrecipitationUnit.Imperial || d.snow<.1) 2 else 1),OverviewText))
    if(precipParts.isEmpty()) precipParts.add(Triple(SummaryIcon.Rain,"0 "+u.precipitation.liquidSymbol,OverviewText))
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,TileBorder),colors=CardDefaults.outlinedCardColors(containerColor=Color.Transparent,contentColor=OverviewText)) {
        Column(Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF2A3647),Color(0xFF202833)))).padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TileTitle(uiText(R.string.ui_suhrn_dna_12a3ae07))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                SummaryCell(listOf(Triple(SummaryIcon.Temp,overviewNumber(u.temperature.fromCelsius(d.tmin),"°")+"/"+overviewNumber(u.temperature.fromCelsius(d.tmax),"°"),OverviewText)))
                SummaryDivider()
                SummaryCell(precipParts)
                SummaryDivider()
                SummaryCell(listOf(Triple(SummaryIcon.Wind,overviewNumber(u.wind.fromKmh(d.wind)," "+u.wind.symbol,windDecimals),windAlert(d.wind) ?: OverviewText)))
                SummaryDivider()
                SummaryCell(listOf(Triple(SummaryIcon.Uv,overviewNumber(d.uv),uvColor(d.uv))))
            }
        }
    }
}

@Composable private fun ValueUnit(value:Double,unit:String,color:Color=OverviewText,decimals:Int=0,size:TextUnit=24.sp) {
    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(weatherUnitNumber(value,decimals),fontSize=size,fontWeight=FontWeight.SemiBold,color=color)
        if(unit.isNotEmpty()) Text(unit,fontSize=13.sp,color=ForecastMuted)
    }
}

@Composable private fun Sub(text:String,color:Color=ForecastMuted) {
    Text(text,fontSize=12.sp,color=color,maxLines=1,overflow=TextOverflow.Ellipsis)
}

@Composable private fun ProbRow(prob:Double,small:Boolean) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Umbrella(probabilityColor(prob),if(small) 14.dp else 18.dp)
        Text(overviewNumber(prob," %"),fontSize=if(small) 12.sp else 14.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable private fun RiseLine(up:Boolean,color:Color,time:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        TkRiseArrow(up,color)
        Text(time,fontSize=20.sp,fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false)
    }
}

// Graphic: 12 dp from the right edge (as the text from the left) and its bottom on the measured baseline of the last text row,
// i.e. the same distance from the tile bottom as the visible bottom of that text.
@Composable private fun TileBody(graphic:@Composable ()->Unit={},lastSize:TextUnit=12.sp,lastWeight:FontWeight=FontWeight.Normal,text:@Composable ColumnScope.()->Unit) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val base=LocalTextStyle.current
    val descent=remember(lastSize,lastWeight,base,density) {
        val r=measurer.measure("0",base.merge(TextStyle(fontSize=lastSize,fontWeight=lastWeight)))
        with(density) { (r.size.height-r.firstBaseline).toDp() }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) { text() }
        Box(Modifier.padding(end=2.dp,bottom=descent)) { graphic() }
    }
}

private fun pressureDelta(u:PressureUnit,change:Double):String {
    if(!change.isFinite()) return "—"
    val a=abs(change)
    val v=if(a<0.05) 0.0 else u.fromHpa(a)
    val sign=if(change>=0.05) "+" else if(change<=-0.05) "−" else ""
    return sign+weatherUnitNumber(v,if(u==PressureUnit.InchesMercury) 2 else 1)+uiText(R.string.ui_3_h_195c553b)
}

private class TkSpec(val id:OverviewTileId,val title:String,val dot:Color,val tooltip:String,val chart:String?,val ring:Boolean=false,val content:@Composable ColumnScope.()->Unit)

@Composable private fun Tiles(w:OverviewData,ex:OverviewExtra?,u:WeatherUnits,selected:Set<OverviewTileId>,marineCells:List<OverviewCell>,onHelp:(HelpSpec)->Unit) {
    val navigation=LocalOverviewNavigation.current
    val today=w.days.firstOrNull()
    val now=w.time.atZone(w.zone).toInstant();val day=dayState(w);val night=day==false
    val split=w.rain.isFinite() || w.showers.isFinite() || w.snowfall.isFinite()
    val known=split || w.precipitation.isFinite()
    val liquid=if(split) finite0(w.rain)+finite0(w.showers) else finite0(w.precipitation)
    val snow=if(split) finite0(w.snowfall) else 0.0
    val has=known && liquid+snow>0
    val type=if(split) precipitationType(w.rain,w.showers,w.snowfall) else if(w.precipitation>0) RainBlue else null
    val tint=if(liquid>0) (precipitationType(w.rain,w.showers,0.0) ?: RainBlue) else RainBlue
    val liquidFraction=min(1.0,sqrt((liquid*3600.0/w.intervalSeconds)/6.0))
    val snowStrength=min(1.0,sqrt((snow*3600.0/w.intervalSeconds)/3.0))
    val prob=w.probability.takeIf { it.isFinite() && it in 0.0..100.0 } ?: Double.NaN
    val precipName=when {
        !known -> uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61)
        !has -> uiText(R.string.ui_bez_zrazok_cf1104e5)
        split && finite0(w.rain)+finite0(w.showers)>0 && snow>0 -> uiText(R.string.ui_dazd_so_snehom_586507c0)
        snow>0 -> uiText(R.string.ui_sneh_27400f08)
        finite0(w.rain)>0 -> uiText(R.string.ui_dazd_8eda180e)
        finite0(w.showers)>0 -> uiText(R.string.ui_prehanky_ef949e7b)
        else -> uiText(R.string.ui_zrazky_66d17319)
    }
    val windTint=windAlert(w.wind)
    val windDecimals=if(u.wind==WindSpeedUnit.MetresPerSecond) 1 else 0
    val bft=beaufortIndex(w.wind)
    val windName=if(bft<0) uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61) else uiText(R.string.ui_value_value_bft_8f127059, BeaufortNames[bft], bft)
    val uvLow=night && (!w.uv.isFinite() || w.uv<.5)
    val uvTint=if(uvLow) ForecastMuted else uvColor(w.uv)
    val grey=if(w.cloud.isFinite()) (190-w.cloud*.9).roundToInt().coerceIn(0,243) else -1
    val noon=w.time.toLocalDate().atTime(12,0).atZone(w.zone).toInstant()
    val start=w.time.toLocalDate().atStartOfDay(w.zone).toInstant()
    val moon=remember(w.time.toLocalDate(),w.latitude,w.longitude) { Astro.moonDay(start,noon,w.latitude,w.longitude) }
    val air=w.air;val band=air?.let { aqiBand(it.aqi) }
    val pollen=(air?.pollen ?: emptyMap()).filter { it.value>0 }.map { (k,v)->Triple(k,v,pollenLevel(k,v)) }.sortedWith(compareByDescending<Triple<String,Double,Int>> { it.third }.thenByDescending { pollenRatio(it.first,it.second) })
    val pollenKnown=air?.pollen?.isNotEmpty()==true
    val pollenTip=when {
        air==null || !pollenKnown -> uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61)
        pollen.isEmpty() -> uiText(R.string.ui_ziadny_pel_d712b212)
        else -> {
            val top=pollen.maxOf { it.third }
            if(top==0) uiText(R.string.ui_nizka_zataz_pelu_044ea494) else pollen.filter { it.third==top }.take(2).joinToString(uiText(R.string.ui_a_cbf7f300)) { PollenNames[it.first] ?: it.first }+": "+PollenLevelNames[top]
        }
    }
    val humidity=ex?.humidity ?: Double.NaN
    val dew=ex?.dewPoint ?: Double.NaN
    val humBand=humidityBand(humidity,dew)
    val visibility=ex?.visibility ?: Double.NaN
    val visBand=visibilityBand(visibility)
    val pressureChange=ex?.pressureChange ?: Double.NaN
    val pBand=pressureBand(pressureChange)
    val pressureValue=u.pressure.fromHpa(ex?.pressure ?: Double.NaN)
    val visibilityKm=visibility/1000.0
    val liquidDecimals=if(u.precipitation==PrecipitationUnit.Imperial || liquid<.1) 2 else 1
    val snowDecimals=if(u.precipitation==PrecipitationUnit.Imperial || snow<.1) 2 else 1
    val specs=listOf(
        TkSpec(OverviewTileId.Precipitation,uiText(R.string.ui_zrazky_66d17319),if(has) (type ?: tint) else Grey,precipName,"precipitation") {
            TileBody(graphic={
                if(liquid>0 && snow>0) Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.Bottom) {
                    TkFlush(30.dp,30.dp,0.dp,30.dp.inset(1.4f)) { TkDropGraphic(liquidFraction,tint,Modifier.size(30.dp)) }
                    TkFlush(26.dp,26.dp,26.dp.inset(3.2f),26.dp.inset(2f)) { TkFlakeGraphic(snowStrength,OverviewText,Modifier.size(26.dp)) }
                } else if(snow>0) TkFlush(40.dp,40.dp,40.dp.inset(3.2f),40.dp.inset(2f)) { TkFlakeGraphic(snowStrength,OverviewText,Modifier.size(40.dp)) }
                else TkFlush(40.dp,40.dp,40.dp.inset(4.4f),40.dp.inset(1.4f)) { TkDropGraphic(liquidFraction,tint,Modifier.size(40.dp)) }
            },lastSize=if(liquid>0 && snow>0) 12.sp else 14.sp,lastWeight=FontWeight.Bold) {
                if(!known) ValueUnit(Double.NaN,"")
                else if(!has) ValueUnit(0.0,u.precipitation.liquidSymbol,ForecastMuted,0)
                else if(liquid>0 && snow>0) {
                    PrecipPair(weatherUnitNumber(u.precipitation.fromMillimetres(liquid),liquidDecimals),u.precipitation.liquidSymbol,tint,weatherUnitNumber(u.precipitation.fromSnowCentimetres(snow),snowDecimals),u.precipitation.snowSymbol)
                } else if(liquid>0) ValueUnit(u.precipitation.fromMillimetres(liquid),u.precipitation.liquidSymbol,tint,liquidDecimals)
                else ValueUnit(u.precipitation.fromSnowCentimetres(snow),u.precipitation.snowSymbol,OverviewText,snowDecimals)
                ProbRow(prob,liquid>0 && snow>0)
            }
        },
        TkSpec(OverviewTileId.Wind,uiText(R.string.ui_vietor_8a3ebf26),windBeaufortColor(w.wind),windName,"wind") {
            TileBody(graphic={ if(w.wind.isFinite() && w.wind>0 && w.direction.isFinite()) WindArrow(w.direction,windBeaufortColor(w.wind)) }) {
                ValueUnit(u.wind.fromKmh(w.wind),u.wind.symbol,windTint ?: OverviewText,windDecimals)
                Sub(uiText(R.string.ui_narazy_3e1df66b)+overviewNumber(u.wind.fromKmh(w.gust),"",windDecimals),windAlert(w.gust) ?: ForecastMuted)
            }
        },
        TkSpec(OverviewTileId.Uv,uiText(R.string.ui_uv_027fcbbc),if(uvLow) Grey else uvTint,if(uvLow) uiText(R.string.ui_noc_c592606f) else uvLabel(w.uv),"uv") {
            TileBody(graphic={ TkScaleGraphic(false,if(uvLow) 0.0 else w.uv,11.0,{ uvColor(it) },uvTint,Modifier.width(64.dp)) }) {
                Text(overviewNumber(w.uv),fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=uvTint)
                Sub(uiText(R.string.ui_max_1eed76ee)+overviewNumber(today?.uv ?: Double.NaN))
            }
        },
        TkSpec(OverviewTileId.Cloud,uiText(R.string.ui_oblacnost_b5a6e70c),if(grey<0) Grey else Color(grey,grey+4,grey+12),cloudName(w.cloud).ifEmpty { uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61) }+"\n"+uiText(R.string.cloud_median_note),"cloud") {
            TileBody(graphic={ TkFlush(70.dp,70.dp/1.3f,4.3.dp,11.8.dp) { CloudFill(if(w.cloud.isFinite()) w.cloud/100.0 else 0.0,Color(0xFF8294AD),null,0,Modifier.width(70.dp)) } }) {
                ValueUnit(w.cloud,"%")
                Sub(uiText(R.string.ui_dnes_2b1a88a3)+overviewNumber(today?.daylightCloud ?: Double.NaN," %"))
            }
        },
        TkSpec(OverviewTileId.Humidity,uiText(R.string.ui_vlhkost_e06c751e),humBand?.color ?: Grey,humBand?.name ?: uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61),null) {
            TileBody(graphic={ TkFlush(38.dp,38.dp,38.dp.inset(4.4f),38.dp.inset(1.4f)) { HumidityPercentDrop(if(humidity.isFinite()) humidity/100.0 else 0.0,humBand?.color ?: Color(0xFF8294AD),Modifier.size(38.dp)) } }) {
                ValueUnit(humidity,"%")
                Sub(uiText(R.string.ui_rosny_bod_754737bc)+overviewNumber(u.temperature.fromCelsius(dew),"°"))
            }
        },
        TkSpec(OverviewTileId.Pressure,uiText(R.string.ui_tlak_vzduchu_cc4c1846),pBand.color,pBand.name,"pressure") {
            TileBody(graphic={ TkFlush(38.dp,38.dp,38.dp.inset(.8f),38.dp.inset(.8f)) { TkTrendGraphic(pressureChange,pBand.color,Modifier.size(38.dp)) } }) {
                ValueUnit(pressureValue,u.pressure.symbol,OverviewText,if(u.pressure==PressureUnit.InchesMercury) 2 else 0)
                Sub(pressureDelta(u.pressure,pressureChange),pBand.color)
            }
        },
        TkSpec(OverviewTileId.Visibility,uiText(R.string.ui_viditelnost_7f90db41),visBand?.color ?: Grey,visBand?.name ?: uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61),null) {
            TileBody(graphic={ TkFlush(38.dp,38.dp,38.dp.inset(1.1f),38.dp.inset(3.2f)) { TkEyeGraphic(visBand?.color ?: Color(0xFF8294AD),Modifier.size(38.dp)) } }) {
                ValueUnit(visibilityKm,"km",OverviewText,if(visibilityKm<10) 1 else 0)
                Sub(visBand?.name ?: "",visBand?.color ?: ForecastMuted)
            }
        },
        TkSpec(OverviewTileId.Sun,uiText(R.string.ui_slnko_dnes_4d65cb81),when(day) { null->Grey;true->Color(0xFFFFD166);false->Color(0xFF2B3550) },uiText(R.string.ui_teraz_e6989b57)+w.time.format(DateTimeFormatter.ofPattern("HH:mm")),null) {
            TileBody(graphic={ TkSunArc(w.sunrise,w.sunset,now,Modifier.width(58.dp)) },lastSize=20.sp,lastWeight=FontWeight.SemiBold) {
                RiseLine(true,Color(0xFF66D17A),w.sunrise.clock(w.zone))
                RiseLine(false,Color(0xFFFF8F6B),w.sunset.clock(w.zone))
            }
        },
        TkSpec(OverviewTileId.Moon,uiText(R.string.ui_mesiac_dnes_0e15b5c9),Color(0xFF8C9BB2),moonPhase(noon)+" "+moon.illumination+" %",null,ring=true) {
            TileBody(graphic={ TkFlush(46.dp,46.dp,3.7.dp,3.7.dp) { MoonGraphic(moon.illumination,moon.waxing,moon.southern,Modifier.width(46.dp)) } },lastSize=20.sp,lastWeight=FontWeight.SemiBold) {
                RiseLine(true,Color(0xFF66D17A),moon.rise.clock(w.zone))
                RiseLine(false,Color(0xFFFF8F6B),moon.set.clock(w.zone))
            }
        },
        TkSpec(OverviewTileId.Air,uiText(R.string.ui_kvalita_ovzdusia_08946dda),band?.color ?: Grey,band?.name ?: uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61),null) {
            if(air==null || band==null) Text(uiText(R.string.ui_udaje_nie_su_dostupne_f7a6149a),color=ForecastMuted,fontSize=13.sp) else {
                TileBody(graphic={ TkScaleGraphic(true,air.aqi,100.0,{ aqiBand(it)?.color ?: Grey },band.color,Modifier.width(64.dp)) }) {
                    Text(overviewNumber(air.aqi),fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=band.color)
                    Sub(uiText(R.string.ui_max_1eed76ee)+overviewNumber(today?.aqi ?: Double.NaN))
                }
            }
        },
        TkSpec(OverviewTileId.Pollen,uiText(R.string.ui_pel_aktualne_0fd4a0ea),pollen.firstOrNull()?.let { PollenLevelColors[it.third] } ?: Grey,pollenTip,null) {
            if(pollen.isEmpty()) Text(if(pollenKnown) uiText(R.string.ui_ziadny_pel_v_udajoch_061b5fd7) else uiText(R.string.ui_udaje_nie_su_dostupne_f7a6149a),color=ForecastMuted,fontSize=13.sp)
            else TileBody(graphic={ TkFlush(38.dp,38.dp,38.dp.inset(2.1f),38.dp.inset(2.1f)) { TkPollenGraphic(PollenLevelColors[pollen.first().third],Modifier.size(38.dp)) } },lastSize=15.sp,lastWeight=FontWeight.SemiBold) {
                pollen.take(2).forEach { (k,v,level) ->
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(PollenNames[k] ?: k,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                        Text(overviewNumber(v),fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=PollenLevelColors[level])
                    }
                }
            }
        },
    )
    TileRows(specs.filter { it.id in selected }.map { s->OverviewCell("overview:${s.id.name}",tileCell(s,navigation,onHelp)) }+marineCells)
}
private fun tileCell(s:TkSpec,navigation:OverviewNavigation,onHelp:(HelpSpec)->Unit):@Composable (Modifier)->Unit={ modifier->
    val open:(()->Unit)? = when(s.id) {
        OverviewTileId.Sun,OverviewTileId.Moon -> { { navigation.openSunMoon() } }
        OverviewTileId.Air -> { { navigation.openAir("index") } }
        OverviewTileId.Pollen -> { { navigation.openAir("pollen") } }
        OverviewTileId.Humidity -> { { navigation.openForecast("humidity") } }
        OverviewTileId.Visibility -> { { navigation.openForecast("visibility") } }
        else -> s.chart?.let { c -> { navigation.openForecast(c) } }
    }
    TkTile(s.title,s.dot,s.ring,s.tooltip,modifier,{ onHelp(HelpSpec(s.title) { TileHelp(s.id) }) },open) { s.content(this) }
}

// Rain and snow of the precipitation tile on one line, separated by a comma.
// The line starts at the size of a single value and shrinks until it fits the tile.
@Composable private fun PrecipPair(liquid:String,liquidUnit:String,tint:Color,snow:String,snowUnit:String) {
    val size=androidx.compose.runtime.remember(liquid,snow) { androidx.compose.runtime.mutableStateOf(24f) }
    val bold=androidx.compose.ui.text.SpanStyle(fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
    val small=androidx.compose.ui.text.SpanStyle(color=ForecastMuted,fontSize=androidx.compose.ui.unit.TextUnit(.54f,androidx.compose.ui.unit.TextUnitType.Em))
    val text=androidx.compose.ui.text.AnnotatedString.Builder().apply {
        pushStyle(bold.copy(color=tint));append(liquid);pop()
        pushStyle(small);append(" "+liquidUnit);pop()
        append(", ")
        pushStyle(bold);append(snow);pop()
        pushStyle(small);append(" "+snowUnit);pop()
    }.toAnnotatedString()
    Text(text,color=OverviewText,fontSize=size.value.sp,maxLines=1,softWrap=false,onTextLayout={ if(it.hasVisualOverflow && size.value>12f) size.value-=1f })
}

// Arrow of the wind tile: as tall as the two rows of text, it points where the wind blows.
@Composable private fun WindArrow(direction:Double,color:Color) {
    TkDirectionArrow(direction,color,rememberTileTextBox().height,uiText(R.string.ui_vietor_fuka_zo_smeru_value_sipka_ukazuje_kam_fuka_sever_je_983405a4, cardinal(direction)))
}
