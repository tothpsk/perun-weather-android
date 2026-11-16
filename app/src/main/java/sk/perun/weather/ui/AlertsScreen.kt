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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
private val LevelColors=mapOf("neutral" to Color(0xFF5B6577),"yellow" to Color(0xFFFDD835),"orange" to Color(0xFFFB8C00),"red" to Color(0xFFE53935))
private val LevelRank=mapOf("neutral" to 0,"yellow" to 1,"orange" to 2,"red" to 3)
private val LevelNames=mapOf("neutral" to uiText(R.string.ui_neutralna_85457835),"yellow" to uiText(R.string.ui_zlta_f4e40449),"orange" to uiText(R.string.ui_oranzova_240b737c),"red" to uiText(R.string.ui_cervena_b7b3d767))
private val LevelSurface=mapOf(
    "yellow" to (Color(0xFF302C25) to Color(0xFF806C42)),
    "orange" to (Color(0xFF33271C) to Color(0xFFA0622A)),
    "red" to (Color(0xFF341F22) to Color(0xFFA8393D)),
)
private val LocalIssuer get()=uiText(R.string.ui_lokalny_odhad_72c32ba1)
private val SkLocale=appLocale()
private val ClockFormat=DateTimeFormatter.ofPattern(appPattern("d. M. HH:mm"),appLocale())
private val DayDate=DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale())
private fun levelOf(a: AlertItem)=a.level.takeIf { it in LevelRank } ?: "neutral"
private fun rankOf(a: AlertItem)=LevelRank[levelOf(a)] ?: 0
private fun colorOf(a: AlertItem)=LevelColors[levelOf(a)] ?: Color(0xFF5B6577)
private fun labelOf(a: AlertItem)=a.event.ifBlank { uiText(R.string.ui_vystraha_a0ff184b) }
private fun issuerOf(a: AlertItem)=if (a.local) LocalIssuer else a.issuer.ifBlank { uiText(R.string.ui_oficialna_vystraha_95c0276a) }
private fun timeRange(a: AlertItem,z: ZoneId): String {
    val s=a.start; val e=a.end
    return when {
        s!=null && e!=null ->ClockFormat.format(s.atZone(z))+" – "+ClockFormat.format(e.atZone(z))
        e!=null ->uiText(R.string.ui_plati_do_cea9ccd2)+ClockFormat.format(e.atZone(z))+uiText(R.string.ui_zaciatok_nie_je_uvedeny_3c3b11e6)
        s!=null ->uiText(R.string.ui_plati_od_97113b0d)+ClockFormat.format(s.atZone(z))+uiText(R.string.ui_koniec_nie_je_uvedeny_aa6efcff)
        else ->uiText(R.string.ui_cas_nie_je_uvedeny_3aa85dd3)
    }
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
private fun localEstimates(h: AlertHourly,now: Instant): List<AlertItem> = localAlertHits(h.times,h.code,h.gust,h.precip,h.temp,now).map { AlertItem(it.event,it.level,true,it.start,it.end,LocalIssuer,"") }
private fun alertSourceTitle(name: String)=if (name=="KDE") "FOSS Public Alert Server" else name
private class AlertsData(val items: List<AlertItem>,val reports: List<SourceReport>,val localOk: Boolean,val zone: ZoneId,val labels: List<String>)
private suspend fun loadAlerts(l: Location,source: String,model: String): AlertsData=withContext(Dispatchers.IO) { coroutineScope {
    val tasks=mutableListOf<Deferred<SourceReport>>()
    val chosen=alertSourceSet(source)
    if ("meteo" in chosen && hasMeteoAlarm(l.countryCode)) tasks+=async { meteoReport(l) }
    if ("libre" in chosen) tasks+=async { libreReport(l) }
    if ("kde" in chosen) tasks+=async { kdeReport(l) }
    val hourlyTask=async { if ("local" !in chosen) null else try { loadHourly(l,model) } catch (e: CancellationException) { throw e } catch (e: Exception) { null } }
    val raw=tasks.awaitAll(); val h=hourlyTask.await(); val now=Instant.now()
    val reports=raw.map { it.copy(items=it.items.filter { a ->a.end?.isAfter(now)!=false }) }
    val order=compareByDescending<AlertItem> { rankOf(it) }.thenBy { it.start ?: Instant.MAX }
    val local=if (h!=null) localEstimates(h,now) else emptyList()
    val groups=reports.map { alertSourceTitle(it.name) to it.items.sortedWith(order) }+listOf(LocalIssuer to local.sortedWith(order))
    val items=groups.flatMap { it.second }; val labels=groups.flatMap { g ->g.second.map { g.first } }
    val z=h?.zone ?: runCatching { ZoneId.of(l.timezone) }.getOrNull() ?: ZoneId.systemDefault()
    AlertsData(items,reports,h!=null || "local" !in chosen,z,labels)
} }
private fun dayMarks(from: Instant,to: Instant,z: ZoneId): List<Instant> {
    val out=mutableListOf<Instant>(); var date=from.atZone(z).toLocalDate(); var t=date.atStartOfDay(z).toInstant()
    if (!t.isAfter(from)) { date=date.plusDays(1); t=date.atStartOfDay(z).toInstant() }
    while (t.isBefore(to) && out.size<20) { out+=t; date=date.plusDays(1); t=date.atStartOfDay(z).toInstant() }
    return out
}
// Whole local days only, so today is as wide as every other day column; at most five days.
private fun alertRange(items: List<AlertItem>,now: Instant,z: ZoneId): Pair<Instant,Instant> {
    val today=now.atZone(z).toLocalDate()
    var lo=((items.mapNotNull { it.start }+now).minOrNull() ?: now).atZone(z).toLocalDate()
    var hi=((items.mapNotNull { it.end }+now.plus(Duration.ofHours(6))).maxOrNull() ?: now).atZone(z).toLocalDate()
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
@Composable internal fun AlertsScreen(location: Location) {
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
            if (alertSourceSet(source).isEmpty()) Text(uiText(R.string.alert_sources_none),color=MainText,fontSize=16.sp,fontWeight=FontWeight.SemiBold) else data?.let { AlertsContent(it) }
            AttributionFooter(meteoAlarm="meteo" in alertSourceSet(source) && hasMeteoAlarm(location.countryCode))
        }
    }
}
@Composable private fun AlertPanel(container: Color=PanelBg,border: Color=EdgeColor,modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    OutlinedCard(modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),colors=CardDefaults.outlinedCardColors(containerColor=container,contentColor=MainText),border=BorderStroke(1.dp,border)) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) }
}
@Composable private fun AlertCounts(items: List<AlertItem>) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        listOf("red","orange","yellow","neutral").forEach { lvl ->
            val n=items.count { levelOf(it)==lvl }
            if (n>0) Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(LevelColors[lvl] ?: Color(0xFF5B6577)) }
                Text("×$n",fontSize=14.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun AlertsContent(d: AlertsData) {
    val ok=d.reports.all { it.error==null && !it.incomplete }
    var selected by remember(d.items) { mutableIntStateOf(-1) }
    val scope=rememberCoroutineScope()
    val requesters=remember(d.items) { List(d.items.size) { BringIntoViewRequester() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        d.reports.forEach { r ->
            if (r.error!=null) Text(r.name+uiText(R.string.ui_nepodarilo_sa_nacitat_7b748049)+r.error+uiText(R.string.ui_to_neznamena_ze_ziadne_vystrahy_nie_su_ac01ebd6),color=WarnText,fontSize=13.sp)
            if (r.incomplete) Text(r.name+uiText(R.string.ui_niektore_spravy_sa_nenacitali_zoznam_nemusi_byt_uplny_f75f0167),color=WarnText,fontSize=13.sp)
        }
        if (!d.localOk) Text(uiText(R.string.ui_lokalne_odhady_z_predpovede_sa_nepodarilo_vypocitat_db7210d0),color=WarnText,fontSize=13.sp)
        if (d.items.isEmpty()) {
            if (ok && d.localOk) Text(uiText(R.string.ui_ziadne_aktivne_vystrahy_4252fcf1),color=MainText,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        } else {
            AlertPanel {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(uiText(R.string.ui_aktualne_vystrahy_a783f8f8),Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Bold)
                    AlertCounts(d.items)
                }
                AlertGantt(d.items,d.zone) { i -> selected=i; scope.launch { requesters[i].bringIntoView() } }
            }
            val grouped=d.labels.distinct().size>1
            d.items.forEachIndexed { i,a ->
                if (grouped && (i==0 || d.labels[i]!=d.labels[i-1])) Text(d.labels[i],modifier=Modifier.padding(top=4.dp),color=ForecastMuted,fontSize=13.sp,fontWeight=FontWeight.Bold)
                key(i,a) { AlertCard(a,d.zone,selected==i,Modifier.bringIntoViewRequester(requesters[i])) }
            }
        }
    }
}
@Composable private fun AlertGantt(items: List<AlertItem>,z: ZoneId,onSelect: (Int)->Unit) {
    val measurer=rememberTextMeasurer(); val now=remember(items) { Instant.now() }
    val range=remember(items,z) { alertRange(items,now,z) }; val from=range.first; val to=range.second
    val today=remember(now,z) { now.atZone(z).toLocalDate() }; val days=remember(from,to,z) { dayMarks(from,to,z) }
    val total=Duration.between(from,to).toMillis().coerceAtLeast(1).toDouble()
    val clock=remember(now,z) { DateTimeFormatter.ofPattern("HH:mm").format(now.atZone(z)) }
    fun frac(t: Instant)=(Duration.between(from,t).toMillis()/total).coerceIn(0.0,1.0).toFloat()
    val axisHeight=26.dp
    var cursor by remember(items) { mutableStateOf<Instant?>(null) }
    // Same markers as the other charts: white "now" line, blue cursor while the finger is down, badges on top.
    Box(Modifier.fillMaxWidth().pointerInput(from,to) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            fun pick(px: Float) { cursor=from.plusMillis(((px/size.width).coerceIn(0f,1f)*total).toLong()) }
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
        val axisPx=axisHeight.toPx(); val pad=3.dp.toPx()
        fun marker(t: Instant,color: Color,label: String,badgeY: Float) {
            if (t.isBefore(from) || !t.isBefore(to)) return
            val x=frac(t)*size.width
            val m=measurer.measure(label,TextStyle(color=color,fontSize=9.sp)); val badgeH=m.size.height+2*pad
            val bx=(x-m.size.width/2).coerceIn(pad,(size.width-m.size.width-pad).coerceAtLeast(pad))
            drawLine(color,Offset(x,badgeY+badgeH),Offset(x,size.height-axisPx),1.dp.toPx())
            drawRoundRect(Color(0xFF111A24),Offset(bx-pad,badgeY),Size(m.size.width+2*pad,badgeH),CornerRadius(3.dp.toPx()))
            drawText(m,topLeft=Offset(bx,badgeY+pad))
        }
        marker(now,MainText,uiText(R.string.ui_teraz_e6989b57)+clock,0f)
        cursor?.let { t ->
            marker(t,SelectedEdge,ClockFormat.format(t.atZone(z)),20.dp.toPx())
            val active=items.filter { a ->val s=a.start; val e=a.end; s!=null && e!=null && !t.isBefore(s) && t.isBefore(e) }
            val lines=if (active.isEmpty()) listOf(uiText(R.string.alert_cursor_none) to ForecastMuted) else active.take(4).map { labelOf(it) to (if (levelOf(it)=="neutral") MainText else colorOf(it)) }+(if (active.size>4) listOf("+"+(active.size-4) to MainText) else emptyList())
            val limit=Constraints(maxWidth=(size.width*.6f).toInt().coerceAtLeast(1))
            val measured=lines.map { (s,c) ->measurer.measure(s,TextStyle(color=c,fontSize=10.sp),overflow=TextOverflow.Ellipsis,maxLines=1,constraints=limit) }
            val boxW=measured.maxOf { it.size.width }+2*pad; val boxH=measured.sumOf { it.size.height }+2*pad
            val cx=frac(t)*size.width; val gap=6.dp.toPx()
            val bx=if (cx+gap+boxW<=size.width) cx+gap else (cx-gap-boxW).coerceAtLeast(0f)
            var ty=44.dp.toPx()
            drawRoundRect(Color(0xFF111A24),Offset(bx,ty),Size(boxW,boxH),CornerRadius(3.dp.toPx()))
            drawRoundRect(SelectedEdge,Offset(bx,ty),Size(boxW,boxH),CornerRadius(3.dp.toPx()),style=Stroke(1.dp.toPx()))
            ty+=pad
            measured.forEach { m ->drawText(m,topLeft=Offset(bx+pad,ty)); ty+=m.size.height }
        }
    }) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(32.dp))
            items.forEachIndexed { idx,a ->
                Column(Modifier.fillMaxWidth().clickable { onSelect(idx) }.padding(vertical=2.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(labelOf(a),fontSize=12.sp,color=MainText,maxLines=2)
                    val s=a.start; val e=a.end
                    if (s==null || e==null) Text(uiText(R.string.ui_interval_nie_je_cely_uvedeny_pozri_detail_c84fdda4),fontSize=11.sp,color=ForecastMuted)
                    else if (!e.isAfter(s)) Text(uiText(R.string.ui_neplatny_interval_v_podklade_b9f0925c),fontSize=11.sp,color=WarnText)
                    else if (!e.isAfter(from) || !s.isBefore(to)) Text(uiText(R.string.ui_mimo_zobrazeneho_rozsahu_59a94038),fontSize=11.sp,color=ForecastMuted)
                    else Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val radius=CornerRadius(7.dp.toPx()); drawRoundRect(TrackColor,cornerRadius=radius)
                        days.forEach { day ->val dx=frac(day)*size.width; drawLine(EdgeColor.copy(alpha=.6f),Offset(dx,0f),Offset(dx,size.height),1.dp.toPx()) }
                        val x0=frac(s)*size.width; val x1=frac(e)*size.width
                        drawRoundRect(colorOf(a),Offset(x0,0f),Size((x1-x0).coerceAtLeast(1.dp.toPx()).coerceAtMost((size.width-x0).coerceAtLeast(0f)),size.height),radius)
                    }
                }
            }
            Canvas(Modifier.fillMaxWidth().height(axisHeight)) {
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
@Composable private fun AlertCard(a: AlertItem,z: ZoneId,selected: Boolean,modifier: Modifier) {
    val surface=LevelSurface[levelOf(a)]
    AlertPanel(surface?.first ?: PanelBg,if (selected) SelectedEdge else (surface?.second ?: EdgeColor),modifier) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.padding(top=5.dp).size(12.dp)) { drawCircle(colorOf(a)) }
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(labelOf(a),fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text((LevelNames[levelOf(a)] ?: "")+" · "+issuerOf(a),fontSize=13.sp,color=ForecastMuted)
                if (a.title.isNotBlank() && a.title!=a.event) Text(a.title,fontSize=13.sp,color=ForecastMuted)
                Text(timeRange(a,z),fontSize=13.sp)
            }
        }
    }
}
