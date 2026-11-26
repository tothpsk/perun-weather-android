package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ScreenBg=Color(0xFF323E4F)
private val PanelBg=Color(0xFF202833)
private val EdgeColor=Color(0xFF4B586C)
private val MainText=Color(0xFFE8EAF0)
private val WarnText=Color(0xFFFFB74D)
private val TrackColor=Color(0xFF2A3446)
private val SelectedEdge=Color(0xFF4FC3F7)
private val BadgeBg=Color(0xFF111A24)
// The column of the hazard icons in front of the bars of the chart: one icon, wider only for a row with more of them.
private val GanttGutter=28.dp
private val IconStep=22.dp
private const val MaxIcons=3
private val LevelColors=mapOf("neutral" to Color(0xFF5B6577),"yellow" to Color(0xFFFDD835),"orange" to Color(0xFFFB8C00),"red" to Color(0xFFE53935))
private val LevelNames=mapOf("neutral" to uiText(R.string.ui_neutralna_85457835),"yellow" to uiText(R.string.ui_zlta_f4e40449),"orange" to uiText(R.string.ui_oranzova_240b737c),"red" to uiText(R.string.ui_cervena_b7b3d767))
private val LevelSurface=mapOf(
    "yellow" to (Color(0xFF302C25) to Color(0xFF806C42)),
    "orange" to (Color(0xFF33271C) to Color(0xFFA0622A)),
    "red" to (Color(0xFF341F22) to Color(0xFFA8393D)),
)
private val SourceOrder=listOf("MeteoAlarm","LibreWXR","KDE")
private val LocalIssuer get()=uiText(R.string.ui_lokalny_odhad_72c32ba1)
private val SkLocale=appLocale()
private val ClockFormat=DateTimeFormatter.ofPattern(appPattern("d. M. HH:mm"),appLocale())
private val DayClockFormat=DateTimeFormatter.ofPattern(appPattern("EEE d. M. HH:mm"),appLocale())
private val DayDate=DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale())
private fun levelOf(a: UnifiedAlert)=a.level.key.takeIf { it in LevelColors } ?: "neutral"
private fun colorOf(a: UnifiedAlert)=LevelColors[levelOf(a)] ?: Color(0xFF5B6577)
private fun inkOf(a: UnifiedAlert)=if (levelOf(a)=="neutral") MainText else colorOf(a)
private fun textOf(a: UnifiedAlert)=alertText(a)
private fun nameOf(a: UnifiedAlert)=alertName(a)
private fun timeRange(a: UnifiedAlert,z: ZoneId,f: DateTimeFormatter=ClockFormat)=f.format(a.start.atZone(z))+" – "+f.format(a.end.atZone(z))
private fun span(d: Duration): String {
    val m=d.toMinutes().coerceAtLeast(1)
    return when {
        m<60 ->uiText(R.string.alert_duration_min,m)
        m<48*60 ->uiText(R.string.alert_duration_hours,m/60,m%60)
        else ->uiText(R.string.alert_duration_days,m/(24*60),(m/60)%24)
    }
}
private fun stateOf(a: UnifiedAlert,now: Instant)=when {
    now.isBefore(a.start) ->uiText(R.string.alert_starts_in,span(Duration.between(now,a.start)))
    now.isBefore(a.end) ->uiText(R.string.alert_ends_in,span(Duration.between(now,a.end)))
    else ->uiText(R.string.alert_ended)
}
private class AlertHourly(val zone: ZoneId,val times: List<Instant>,val code: DoubleArray,val gust: DoubleArray,val precip: DoubleArray,val temp: DoubleArray)
// Same forecast model as the Now screen, so both screens estimate from the same data.
private fun loadHourly(l: Location,model: String): AlertHourly {
    val tz=URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8"); val elevation=l.elevation?.let { "&elevation=$it" } ?: ""
    val url="https://api.open-meteo.com/v1/forecast?latitude=${l.latitude}&longitude=${l.longitude}&timezone=$tz$elevation&timeformat=unixtime&forecast_days=5&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm&hourly=temperature_2m,weather_code,precipitation,wind_gusts_10m"
    fun fetch(models: String)=JSONObject(String(fetchBytes(url+models),Charsets.UTF_8))
    val root=fetch(if (model=="best_match") "" else "&models="+URLEncoder.encode(model,"UTF-8")); val z=ZoneId.of(root.getString("timezone")); val h=root.getJSONObject("hourly"); val t=h.getJSONArray("time"); val n=t.length()
    check(n>0)
    fun missing(k: String): Boolean { val a=h.optJSONArray(k); return a==null || (0 until n).all { a.isNull(it) } }
    val keys=listOf("weather_code","wind_gusts_10m","precipitation","temperature_2m")
    // Values the selected model does not provide are taken from best_match, as on the Now screen.
    val spare=if (model!="best_match" && keys.any { missing(it) }) try { fetch("").getJSONObject("hourly").takeIf { it.getJSONArray("time").toString()==t.toString() } } catch (e: Exception) { null } else null
    fun series(k: String): DoubleArray { val a=(if (missing(k)) spare?.optJSONArray(k) else null) ?: h.optJSONArray(k); return DoubleArray(n) { i ->a?.optDouble(i,Double.NaN) ?: Double.NaN } }
    return AlertHourly(z,List(n) { Instant.ofEpochSecond(t.getLong(it)) },series("weather_code"),series("wind_gusts_10m"),series("precipitation"),series("temperature_2m"))
}
// Rules and validity windows are shared with the Now screen (localAlertHits in OverviewData.kt).
private fun localEstimates(h: AlertHourly,now: Instant): List<UnifiedAlert> = localAlertHits(h.times,h.code,h.gust,h.precip,h.temp,now).map { localAlert(it,now) }
private fun alertSourceTitle(name: String)=if (name=="KDE") "FOSS Public Alert Server" else name
private class AlertsData(val alerts: List<UnifiedAlert>,val reports: List<SourceReport>,val localOk: Boolean,val zone: ZoneId,val now: Instant)
private suspend fun loadAlerts(l: Location,source: String,model: String): AlertsData=withContext(Dispatchers.IO) { coroutineScope {
    val tasks=mutableListOf<Deferred<SourceReport>>()
    val chosen=alertSourceSet(source); val budget=CapBudget()
    if ("meteo" in chosen && hasMeteoAlarm(l.countryCode)) tasks+=async { meteoReport(l,budget) }
    if ("libre" in chosen) tasks+=async { libreReport(l,budget) }
    if ("kde" in chosen) tasks+=async { kdeReport(l,budget) }
    val hourlyTask=async { if ("local" !in chosen) null else try { loadHourly(l,model) } catch (e: CancellationException) { throw e } catch (e: Exception) { null } }
    val reports=tasks.awaitAll(); val h=hourlyTask.await(); val now=Instant.now()
    // One warning for all sources: the same CAP message from MeteoAlarm, LibreWXR and FOSS is one warning.
    val official=mergeUnified(reports.flatMap { it.alerts }).filter { it.end.isAfter(now) }
    val local=if (h!=null) localEstimates(h,now) else emptyList()
    val order=alertOrder(now)
    val z=h?.zone ?: runCatching { ZoneId.of(l.timezone) }.getOrNull() ?: ZoneId.systemDefault()
    AlertsData(official.sortedWith(order)+local.sortedWith(order),reports,h!=null || "local" !in chosen,z,now)
} }
private fun dayMarks(from: Instant,to: Instant,z: ZoneId): List<Instant> {
    val out=mutableListOf<Instant>(); var date=from.atZone(z).toLocalDate(); var t=date.atStartOfDay(z).toInstant()
    if (!t.isAfter(from)) { date=date.plusDays(1); t=date.atStartOfDay(z).toInstant() }
    while (t.isBefore(to) && out.size<20) { out+=t; date=date.plusDays(1); t=date.atStartOfDay(z).toInstant() }
    return out
}
// Whole local days only, so today is as wide as every other day column; at most five days.
private fun alertRange(items: List<UnifiedAlert>,now: Instant,z: ZoneId): Pair<Instant,Instant> {
    val today=now.atZone(z).toLocalDate()
    var lo=((items.map { it.start }+now).minOrNull() ?: now).atZone(z).toLocalDate()
    var hi=((items.map { it.end }+now.plus(Duration.ofHours(6))).maxOrNull() ?: now).atZone(z).toLocalDate()
    if (lo.isBefore(today.minusDays(1))) lo=today.minusDays(1)
    if (hi.isBefore(lo)) hi=lo
    if (hi.isAfter(lo.plusDays(4))) hi=lo.plusDays(4)
    return lo.atStartOfDay(z).toInstant() to hi.plusDays(1).atStartOfDay(z).toInstant()
}
private fun dayName(date: LocalDate,today: LocalDate,short: Boolean): String = when (date) {
    today ->uiText(R.string.ui_dnes_ce1bed34)
    today.plusDays(1) ->uiText(R.string.ui_zajtra_c3e86eb2)
    else ->date.format(DateTimeFormatter.ofPattern(if (short) "EEE" else "EEEE",SkLocale))
}
private fun dayLabel(date: LocalDate,today: LocalDate,style: Int): AnnotatedString = buildAnnotatedString {
    if (style<2) {
        withStyle(SpanStyle(fontWeight=FontWeight.Bold,color=MainText)) { append(dayName(date,today,style==1)) }
        withStyle(SpanStyle(color=ForecastMuted)) { append(" · "+date.format(DayDate)) }
    } else withStyle(SpanStyle(color=ForecastMuted)) { append(date.format(DayDate)) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AlertsScreen(location: Location,focus: String?=null,onFocusUsed: ()->Unit={}) {
    val preferences=rememberForecastPreferences(); val source=preferences.alertSource; val model=preferences.model
    var data by remember(location) { mutableStateOf<AlertsData?>(null) }
    var loading by remember(location) { mutableStateOf(true) }
    var failed by remember(location) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(location,source,model,reload) {
        loading=true
        try { data=loadAlerts(location,source,model); failed=false } catch (e: CancellationException) { throw e } catch (e: Exception) { failed=true } finally { loading=false }
    }
    PullToRefreshBox(isRefreshing=loading,onRefresh={ if (!loading) { NetCache.invalidate(); loading=true; reload++ } },modifier=Modifier.fillMaxSize().background(ScreenBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if (failed) Text(uiText(R.string.ui_vystrahy_sa_nepodarilo_nacitat_to_neznamena_ze_ziadne_nie_a87aa611),color=WarnText)
            if (alertSourceSet(source).isEmpty()) Text(uiText(R.string.alert_sources_none),color=MainText,fontSize=16.sp,fontWeight=FontWeight.SemiBold) else data?.let { AlertsContent(it,focus,onFocusUsed) }
            AttributionFooter(meteoAlarm="meteo" in alertSourceSet(source) && hasMeteoAlarm(location.countryCode))
        }
    }
}
@Composable private fun AlertPanel(container: Color=PanelBg,border: Color=EdgeColor,modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    OutlinedCard(modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),colors=CardDefaults.outlinedCardColors(containerColor=container,contentColor=MainText),border=BorderStroke(1.dp,border)) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) }
}
@Composable private fun AlertCounts(alerts: List<UnifiedAlert>) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        listOf("red","orange","yellow","neutral").forEach { lvl ->
            val n=alerts.count { levelOf(it)==lvl }
            if (n>0) Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(LevelColors[lvl] ?: Color(0xFF5B6577)) }
                Text("×$n",fontSize=14.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun AlertsContent(d: AlertsData,focus: String?,onFocusUsed: ()->Unit) {
    val ok=d.reports.all { it.error==null && !it.incomplete }
    val rows=remember(d) { alertRows(d.alerts,d.now) }
    var selected by remember(d) { mutableStateOf<UnifiedAlert?>(null) }
    // The chosen warning opens in its place in the overview; a tap on the chart scrolls to it.
    val views=remember(d) { d.alerts.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(selected) { selected?.let { delay(60); views[it]?.bringIntoView() } }
    // A notification names its warning: open its detail; a warning that is gone leaves the screen as it is.
    LaunchedEffect(d,focus) { if (focus!=null) { d.alerts.firstOrNull { focus in alertRefs(it) }?.let { selected=it }; onFocusUsed() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        d.reports.forEach { r ->
            if (r.error!=null) Text(alertSourceTitle(r.name)+uiText(R.string.ui_nepodarilo_sa_nacitat_7b748049)+r.error+uiText(R.string.ui_to_neznamena_ze_ziadne_vystrahy_nie_su_ac01ebd6),color=WarnText,fontSize=13.sp)
            if (r.incomplete) Text(alertSourceTitle(r.name)+uiText(R.string.ui_niektore_spravy_sa_nenacitali_zoznam_nemusi_byt_uplny_f75f0167),color=WarnText,fontSize=13.sp)
        }
        if (!d.localOk) Text(uiText(R.string.ui_lokalne_odhady_z_predpovede_sa_nepodarilo_vypocitat_db7210d0),color=WarnText,fontSize=13.sp)
        if (d.alerts.isEmpty()) {
            if (ok && d.localOk) Text(uiText(R.string.ui_ziadne_aktivne_vystrahy_4252fcf1),color=MainText,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        } else {
            AlertPanel {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(uiText(R.string.ui_aktualne_vystrahy_a783f8f8),Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Bold)
                    AlertCounts(d.alerts)
                }
                AlertGantt(rows,d.zone,d.now,selected) { a -> selected=if (selected==a) null else a }
            }
            Text(uiText(R.string.alert_list_title),modifier=Modifier.padding(top=4.dp),color=ForecastMuted,fontSize=13.sp,fontWeight=FontWeight.Bold)
            var group: Boolean?=null
            val grouped=d.alerts.any { it.local } && d.alerts.any { !it.local }
            d.alerts.forEach { a ->
                if (grouped && group!=a.local) { group=a.local; Text(if (a.local) LocalIssuer else uiText(R.string.alert_group_official),color=ForecastMuted,fontSize=12.sp) }
                Box(Modifier.bringIntoViewRequester(views.getValue(a))) {
                    if (selected==a) AlertDetail(a,rows.firstOrNull { a in it.alerts }?.overlapping(a).orEmpty(),d.zone,d.now,{ selected=it }) { selected=null }
                    else AlertCard(a,d.zone,false) { selected=a }
                }
            }
        }
    }
}
// Width of the icon column: the widest row, at most three icons and a "+n".
private fun gutterOf(rows: List<AlertRow>): Dp {
    val icons=rows.maxOfOrNull { it.hazards.size.coerceIn(1,MaxIcons) } ?: 1
    val more=rows.any { it.hazards.size>MaxIcons }
    return if (icons==1 && !more) GanttGutter else IconStep*icons+(if (more) 18.dp else 0.dp)+6.dp
}
@Composable private fun HazardIcons(hazards: List<AlertHazard>,color: Color,width: Dp) {
    var open by remember { mutableStateOf(false) }
    val names=hazards.ifEmpty { listOf(AlertHazard.Generic) }.joinToString(" · ") { hazardName(it) }
    // A short tap names the kinds, in the bubble of the info dots of the Now screen; the name of the warning is in the detail.
    Box(Modifier.width(width).heightIn(min=28.dp).clickable(onClickLabel=names) { open=true }.semantics(mergeDescendants=true) { contentDescription=names },contentAlignment=Alignment.CenterStart) {
        Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            hazards.ifEmpty { listOf(AlertHazard.Generic) }.take(MaxIcons).forEach { AlertGlyph(it,color,20.dp) }
            if (hazards.size>MaxIcons) Text("+"+(hazards.size-MaxIcons),fontSize=10.sp,color=color)
        }
        if (open) {
            LaunchedEffect(names) { delay(2600); open=false }
            Popup(alignment=Alignment.BottomStart,onDismissRequest={ open=false },properties=PopupProperties(focusable=true)) {
                Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF10131A),contentColor=MainText,border=BorderStroke(1.dp,color),shadowElevation=4.dp) {
                    Text(names,Modifier.widthIn(max=200.dp).padding(horizontal=10.dp,vertical=6.dp),fontSize=13.sp)
                }
            }
        }
    }
}
@Composable private fun AlertGantt(rows: List<AlertRow>,z: ZoneId,now: Instant,selected: UnifiedAlert?,onSelect: (UnifiedAlert)->Unit) {
    val measurer=rememberTextMeasurer(); val all=remember(rows) { rows.flatMap { it.alerts } }
    val range=remember(all,z) { alertRange(all,now,z) }; val from=range.first; val to=range.second
    val today=remember(now,z) { now.atZone(z).toLocalDate() }; val days=remember(from,to,z) { dayMarks(from,to,z) }
    val total=Duration.between(from,to).toMillis().coerceAtLeast(1).toDouble()
    val clock=remember(now,z) { DateTimeFormatter.ofPattern("HH:mm").format(now.atZone(z)) }
    val gutterDp=remember(rows) { gutterOf(rows) }
    fun frac(t: Instant)=(Duration.between(from,t).toMillis()/total).coerceIn(0.0,1.0).toFloat()
    val axisHeight=26.dp
    var cursor by remember(rows) { mutableStateOf<Instant?>(null) }
    // Same markers as the other charts: white "now" line, blue cursor while the finger is down, badges on top.
    Box(Modifier.fillMaxWidth().pointerInput(from,to,gutterDp) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val gutter=gutterDp.toPx()
            // The icons have their own tap; the cursor belongs to the bars.
            if (down.position.x<gutter) return@awaitEachGesture
            fun pick(px: Float) { cursor=from.plusMillis((((px-gutter)/(size.width-gutter).coerceAtLeast(1f)).coerceIn(0f,1f)*total).toLong()) }
            pick(down.position.x)
            try {
                do {
                    val event=awaitPointerEvent(); val c=event.changes.firstOrNull { it.id==down.id } ?: break
                    if (!c.pressed) break
                    if (c.positionChanged()) { c.consume(); pick(c.position.x) }
                } while (true)
            } finally { cursor=null }
        }
    }.drawWithContent {
        drawContent()
        val axisPx=axisHeight.toPx(); val pad=3.dp.toPx(); val gutter=gutterDp.toPx()
        fun marker(t: Instant,color: Color,label: String,badgeY: Float) {
            if (t.isBefore(from) || !t.isBefore(to)) return
            val x=gutter+frac(t)*(size.width-gutter)
            val m=measurer.measure(label,TextStyle(color=color,fontSize=9.sp)); val badgeH=m.size.height+2*pad
            val bx=(x-m.size.width/2).coerceIn(pad,(size.width-m.size.width-pad).coerceAtLeast(pad))
            drawLine(color,Offset(x,badgeY+badgeH),Offset(x,size.height-axisPx),1.dp.toPx())
            drawRoundRect(BadgeBg,Offset(bx-pad,badgeY),Size(m.size.width+2*pad,badgeH),CornerRadius(3.dp.toPx()))
            drawText(m,topLeft=Offset(bx,badgeY+pad))
        }
        marker(now,MainText,uiText(R.string.ui_teraz_e6989b57)+clock,0f)
        cursor?.let { t ->
            marker(t,SelectedEdge,ClockFormat.format(t.atZone(z)),20.dp.toPx())
            val active=rows.mapNotNull { it.at(t) }
            val lines=if (active.isEmpty()) listOf(uiText(R.string.alert_cursor_none) to ForecastMuted) else active.take(4).map { nameOf(it) to inkOf(it) }+(if (active.size>4) listOf("+"+(active.size-4) to MainText) else emptyList())
            val limit=Constraints(maxWidth=(size.width*.6f).toInt().coerceAtLeast(1))
            val measured=lines.map { (s,c) ->measurer.measure(s,TextStyle(color=c,fontSize=10.sp),overflow=TextOverflow.Ellipsis,maxLines=1,constraints=limit) }
            val boxW=measured.maxOf { it.size.width }+2*pad; val boxH=measured.sumOf { it.size.height }+2*pad
            val cx=gutter+frac(t)*(size.width-gutter); val gap=6.dp.toPx()
            val bx=if (cx+gap+boxW<=size.width) cx+gap else (cx-gap-boxW).coerceAtLeast(0f)
            var ty=44.dp.toPx()
            drawRoundRect(BadgeBg,Offset(bx,ty),Size(boxW,boxH),CornerRadius(3.dp.toPx()))
            drawRoundRect(SelectedEdge,Offset(bx,ty),Size(boxW,boxH),CornerRadius(3.dp.toPx()),style=Stroke(1.dp.toPx()))
            ty+=pad
            measured.forEach { m ->drawText(m,topLeft=Offset(bx+pad,ty)); ty+=m.size.height }
        }
    }) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(32.dp))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().heightIn(min=28.dp),verticalAlignment=Alignment.CenterVertically) {
                    HazardIcons(row.hazards,inkOf(row.top),gutterDp)
                    val valid=row.alerts.filter { it.end.isAfter(it.start) }
                    val visible=valid.filter { it.end.isAfter(from) && it.start.isBefore(to) }
                    if (valid.isEmpty()) Text(uiText(R.string.ui_neplatny_interval_v_podklade_b9f0925c),fontSize=11.sp,color=WarnText)
                    else if (visible.isEmpty()) Text(uiText(R.string.ui_mimo_zobrazeneho_rozsahu_59a94038),fontSize=11.sp,color=ForecastMuted)
                    else {
                        val label=visible.joinToString(", ") { nameOf(it)+" · "+(LevelNames[levelOf(it)] ?: "")+" · "+timeRange(it,z) }
                        Canvas(Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription=label }.pointerInput(row,from,to) {
                            detectTapGestures { o ->
                                val t=from.plusMillis(((o.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f)*total).toLong())
                                // A tap beside a short bar still finds it: the nearest bar within 16 dp.
                                val hit=row.at(t) ?: visible.minByOrNull { a ->
                                    val x0=frac(a.start)*size.width; val x1=frac(a.end)*size.width
                                    if (o.x<x0) x0-o.x else if (o.x>x1) o.x-x1 else 0f
                                }?.takeIf { a ->
                                    val x0=frac(a.start)*size.width; val x1=frac(a.end)*size.width
                                    (if (o.x<x0) x0-o.x else if (o.x>x1) o.x-x1 else 0f)<=16.dp.toPx()
                                }
                                if (hit!=null) onSelect(hit)
                            }
                        }) {
                            val barH=14.dp.toPx(); val top=(size.height-barH)/2; val radius=CornerRadius(barH/2)
                            drawRoundRect(TrackColor,Offset(0f,top),Size(size.width,barH),radius)
                            days.forEach { day ->val dx=frac(day)*size.width; drawLine(EdgeColor.copy(alpha=.6f),Offset(dx,top),Offset(dx,top+barH),1.dp.toPx()) }
                            visible.forEach { a ->
                                val x0=frac(a.start)*size.width; val x1=frac(a.end)*size.width
                                val w=(x1-x0).coerceAtLeast(1.dp.toPx()).coerceAtMost((size.width-x0).coerceAtLeast(0f))
                                val dim=selected!=null && selected!=a
                                drawRoundRect(colorOf(a).copy(alpha=if (dim) .35f else 1f),Offset(x0,top),Size(w,barH),radius)
                                if (selected==a) drawRoundRect(SelectedEdge,Offset(x0-1.5.dp.toPx(),top-1.5.dp.toPx()),Size(w+3.dp.toPx(),barH+3.dp.toPx()),CornerRadius(barH/2+1.5.dp.toPx()),style=Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(axisHeight)) {
            // The hint of the chart in the corner under the icons, as the (i) of the Forecast charts.
            Box(Modifier.width(gutterDp).fillMaxHeight(),contentAlignment=Alignment.CenterStart) { ChartHint(uiText(R.string.alert_detail_hint)) }
            Canvas(Modifier.weight(1f).height(axisHeight)) {
                val labelTop=7.dp.toPx(); val ts=TextStyle(fontSize=10.sp)
                val bounds=listOf(from)+days+listOf(to)
                val dates=bounds.dropLast(1).map { it.atZone(z).toLocalDate() }
                val fullPx=(size.width*(24*3600000.0/total)).toFloat()
                fun w(date: LocalDate,style: Int)=measurer.measure(dayLabel(date,today,style),ts).size.width
                val base=(0..2).firstOrNull { st ->dates.indices.all { w(dates[it],st)<=fullPx-6.dp.toPx() } } ?: 2
                for (i in dates.indices) {
                    val x0=frac(bounds[i])*size.width; val x1=frac(bounds[i+1])*size.width
                    if (i>0) drawLine(EdgeColor,Offset(x0,0f),Offset(x0,5.dp.toPx()),1.dp.toPx())
                    val st=(base..2).firstOrNull { w(dates[i],it)<=x1-x0-4.dp.toPx() } ?: continue
                    val label=measurer.measure(dayLabel(dates[i],today,st),ts)
                    drawText(label,topLeft=Offset(x0+((x1-x0)-label.size.width)/2f,labelTop))
                }
            }
            }
        }
    }
}
// The (i) of the charts: a short tap shows the hint in a bubble.
@Composable private fun ChartHint(text: String) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(28.dp).offset(x=(-6).dp).clickable(role=androidx.compose.ui.semantics.Role.Button,onClickLabel=uiText(R.string.ui_strucny_popis_e8d7e5f7)) { open=true }.semantics { contentDescription=text },contentAlignment=Alignment.Center) {
        Text("\u24D8",color=ForecastMuted,fontSize=16.sp)
        if (open) {
            LaunchedEffect(text) { delay(5000); open=false }
            Popup(alignment=Alignment.BottomStart,onDismissRequest={ open=false },properties=PopupProperties(focusable=true)) {
                Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF10131A),contentColor=MainText,border=BorderStroke(1.dp,SelectedEdge),shadowElevation=4.dp) {
                    Text(text,Modifier.widthIn(max=240.dp).padding(horizontal=10.dp,vertical=6.dp),fontSize=13.sp)
                }
            }
        }
    }
}
@Composable private fun SectionLabel(text: String) {
    Text(text.uppercase(appLocale()),fontSize=11.sp,fontWeight=FontWeight.Bold,color=ForecastMuted,letterSpacing=.8.sp)
}
@Composable private fun Section(label: String,content: @Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) { SectionLabel(label); content() }
}
@Composable private fun LevelPill(a: UnifiedAlert) {
    val c=colorOf(a); val shape=RoundedCornerShape(50)
    Text(LevelNames[levelOf(a)] ?: "",Modifier.clip(shape).background(c.copy(alpha=.18f)).border(1.dp,c,shape).padding(horizontal=10.dp,vertical=3.dp),fontSize=12.sp,fontWeight=FontWeight.Bold,color=inkOf(a))
}
private fun linkText(label: String,page: String?): AnnotatedString = buildAnnotatedString {
    if (page==null) append(label) else withLink(LinkAnnotation.Url(page,TextLinkStyles(style=SpanStyle(color=SelectedEdge)))) { append(label) }
}
@Composable private fun AlertDetail(a: UnifiedAlert,overlaps: List<UnifiedAlert>,z: ZoneId,now: Instant,onSelect: (UnifiedAlert)->Unit,onClose: ()->Unit) {
    val preferred=appLocale().toLanguageTag()
    val choices=remember(a) { languageChoices(a.texts,preferred) }
    var language by remember(a) { mutableIntStateOf(0) }
    val text=choices.getOrNull(language) ?: textOf(a)
    // The detail stands outside the panels, so it sets the light text colour itself.
    CompositionLocalProvider(LocalContentColor provides MainText) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(LevelSurface[levelOf(a)]?.first ?: TrackColor).border(1.dp,LevelSurface[levelOf(a)]?.second ?: EdgeColor,RoundedCornerShape(15.dp)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        // A tap on the header closes the detail, as the cross does.
        Row(Modifier.fillMaxWidth().clickable(onClick=onClose),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            a.hazards.ifEmpty { listOf(AlertHazard.Generic) }.take(MaxIcons).forEach { AlertGlyph(it,inkOf(a),22.dp) }
            Text(nameOf(a),Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.Bold)
            LevelPill(a)
            val close=uiText(R.string.alert_detail_close)
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable(onClickLabel=close,onClick=onClose).semantics { contentDescription=close },contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(12.dp)) { val s=1.6.dp.toPx(); drawLine(ForecastMuted,Offset.Zero,Offset(size.width,size.height),s); drawLine(ForecastMuted,Offset(size.width,0f),Offset(0f,size.height),s) }
            }
        }
        if (a.local) Text(uiText(R.string.alert_local_note),fontSize=12.sp,color=ForecastMuted)
        if (a.hazardEstimated()) Text(uiText(R.string.alert_hazard_estimated),fontSize=12.sp,color=ForecastMuted)
        Section(uiText(R.string.alert_detail_validity)) {
            val c=colorOf(a)
            Canvas(Modifier.fillMaxWidth().padding(vertical=3.dp).height(6.dp)) {
                val r=CornerRadius(size.height/2); val length=Duration.between(a.start,a.end).toMillis().coerceAtLeast(1).toFloat()
                val done=(Duration.between(a.start,now).toMillis()/length).coerceIn(0f,1f)
                drawRoundRect(c.copy(alpha=.3f),cornerRadius=r)
                if (done>0f) drawRoundRect(c,size=Size(size.width*done,size.height),cornerRadius=r)
                if (done>0f && done<1f) drawLine(MainText,Offset(size.width*done,-2.dp.toPx()),Offset(size.width*done,size.height+2.dp.toPx()),1.5.dp.toPx())
            }
            Text(timeRange(a,z,DayClockFormat),fontSize=14.sp)
            Text(stateOf(a,now),fontSize=13.sp,color=ForecastMuted)
        }
        val event=text?.event.orEmpty(); val headline=text?.headline.orEmpty()
        val names=listOf(event,headline).filterIndexed { i,s -> if (i==0) informative(s,nameOf(a)) else informative(s,event,nameOf(a)) }
        if (names.isNotEmpty()) Section(uiText(R.string.alert_detail_name)) { names.forEach { Text(it,fontSize=14.sp) } }
        text?.description?.takeIf { informative(it,event,headline) }?.let { Section(uiText(R.string.alert_detail_description)) { Text(it.trim(),fontSize=14.sp) } }
        text?.instruction?.takeIf { informative(it) }?.let {
            Section(uiText(R.string.alert_detail_instruction)) {
                val c=colorOf(a)
                Text(it.trim(),Modifier.drawBehind { drawLine(c,Offset(0f,0f),Offset(0f,size.height),3.dp.toPx()) }.padding(start=10.dp),fontSize=14.sp)
            }
        }
        text?.area?.takeIf { informative(it) }?.let { Section(uiText(R.string.alert_detail_area)) { Text(it.trim(),fontSize=14.sp) } }
        if (choices.size>1) Section(uiText(R.string.alert_detail_language)) {
            PerunChips(choices.map { languageName(it.language,appLocale()).ifEmpty { uiText(R.string.alert_detail_original) } },language,{ language=it })
        }
        if (!a.local && a.issuer.isNotBlank()) Section(uiText(R.string.alert_detail_issuer)) {
            Text(linkText(a.issuer,a.web.takeIf { it.startsWith("https://") || it.startsWith("http://") }),fontSize=14.sp)
        }
        val sources=a.sources.map { it.source }.distinct().sortedBy { SourceOrder.indexOf(it).let { i -> if (i<0) SourceOrder.size else i } }
        if (sources.isNotEmpty()) Section(uiText(R.string.alert_detail_sources)) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                sources.forEach { s -> val shape=RoundedCornerShape(50); Text(if (s=="KDE") "FOSS" else s,Modifier.clip(shape).border(1.dp,EdgeColor,shape).padding(horizontal=10.dp,vertical=3.dp),fontSize=12.sp,color=MainText) }
            }
        }
        overlaps.forEach { o ->
            val label=uiText(R.string.alert_overlap,(LevelNames[levelOf(o)] ?: "")+" · "+timeRange(o,z))
            Text(label,Modifier.clickable(onClickLabel=label) { onSelect(o) },fontSize=13.sp,color=SelectedEdge)
        }
        if (!a.local) Text(uiText(R.string.alert_detail_updated,ClockFormat.format(a.sent.atZone(z))),fontSize=12.sp,color=ForecastMuted)
    }
    }
}
@Composable private fun AlertCard(a: UnifiedAlert,z: ZoneId,selected: Boolean,onClick: ()->Unit) {
    val surface=LevelSurface[levelOf(a)]
    AlertPanel(surface?.first ?: PanelBg,if (selected) SelectedEdge else (surface?.second ?: EdgeColor),Modifier.clip(RoundedCornerShape(15.dp)).clickable(onClickLabel=nameOf(a),onClick=onClick)) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) { a.hazards.ifEmpty { listOf(AlertHazard.Generic) }.take(MaxIcons).forEach { AlertGlyph(it,inkOf(a),20.dp) } }
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(nameOf(a),fontSize=15.sp,fontWeight=FontWeight.Bold)
                Text((LevelNames[levelOf(a)] ?: "")+" · "+timeRange(a,z),fontSize=13.sp,color=ForecastMuted)
            }
        }
    }
}
