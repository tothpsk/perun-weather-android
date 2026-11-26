import re
from pathlib import Path
ui=Path('app/src/main/java/sk/perun/weather/ui')
res=Path('app/src/main/res')
def sub(s,pattern,repl,name,count=1):
    out,n=re.subn(pattern,repl,s,flags=re.M)
    if n!=count: raise SystemExit(f'{name}: expected {count} match(es), got {n}')
    return out
def lit(s,old,new,name,count=1):
    if s.count(old)!=count: raise SystemExit(f'{name}: expected {count} match(es), got {s.count(old)}')
    return s.replace(old,new)

RULES='''        // The rule notifications of the place come first, so its group knows how many lines it has besides the warnings.
        val ruleIds=HashSet<Int>();var alertLines=0
        ruleIds+=groupIds(c,key).filter { it>=RuleIdBase }
        val rules=s.rules.filter { locMatch(it.loc,key,active) }
        if(rules.isNotEmpty()) {
            val air=if(rules.any { spec(it.metric).group==G_AIR || spec(it.metric).group==G_POLLEN }) loadAirNow(l) else emptyMap()
            val ed=p.edit();val showing=ruleIds.toSet();val nm=NotificationManagerCompat.from(c);val at=Instant.now()
            var series:RuleSeries?=null
            rules.forEach { r ->
                val m=spec(r.metric);val flag="rule_"+r.id+"_"+key;val id=ruleId(key,r)
                val was=p.getBoolean(flag,false)
                if(m.kind==K_EVENT) {
                    val codes=EventCodes[r.metric] ?: emptySet();val h=r.threshold.toInt().coerceIn(1,6)
                    val now=d.code in codes || d.hours.take(h).any { it.code in codes }
                    if(now!=was) ed.putBoolean(flag,now)
                    // A new event is announced; one still shown is redrawn silently with its end by the forecast; one that is over goes away.
                    if(now && (!was || id in showing)) { postEvent(c,l,r,m,h,id,eventEnd(d,codes,h),d.zone,was);ruleIds+=id }
                    else if(!now) { nm.cancel(id);ruleIds-=id }
                    return@forEach
                }
                val v=metricValue(d,ex,air,r.metric,scale);if(!v.isFinite()) return@forEach
                // Hysteresis: a rule re-arms only after the value moves back by 10 % of the threshold (at least the metric's own minimum band).
                val band=maxOf(abs(r.threshold)*0.1,m.band)
                val now=if(r.above) (if(was) v>r.threshold-band else v>=r.threshold) else (if(was) v<r.threshold+band else v<=r.threshold)
                if(now!=was) ed.putBoolean(flag,now)
                if(now && (!was || id in showing)) {
                    // The rule holds until the first hour of the forecast that no longer fulfils it.
                    val all=series ?: loadRuleSeries(l,LocationSourceStore(c).read(l).weatherModel,rules.filter { !isEvent(it.metric) }.map { it.metric }.toSet(),scale.apiVariable).also { series=it }
                    val end=all.end(r.metric,at) { x -> if(r.above) x>r.threshold-band else x<r.threshold+band }
                    postRule(c,l,r,m,v,u,gas,id,end,d.zone,was);ruleIds+=id
                }
                else if(!now) { nm.cancel(id);ruleIds-=id }
            }
            ed.apply()
        }
'''
TAIL='''            if(s.alertsOngoing) {
                if(shown.isEmpty()) cancelAlertSummary(c,key) else postOngoingAlerts(c,l,d,shown,loud,ruleIds.size)
                alertLines=shown.map { alertKind(it) }.distinct().size
            }
            else if(fresh.isNotEmpty()) {
                // A place with one notification has the place in its line; with more of them (warnings and rules) the lines are
                // grouped under the header of the place and the ones already shown are redrawn silently in the short form.
                val shownIds=groupIds(c,key)
                val earlier=d.alerts.filter { it !in fresh && announceId(key,it) in shownIds }
                val all=earlier+fresh;val alone=all.size+ruleIds.size==1
                earlier.forEach { postAlert(c,l,d,it,announceId(key,it),alone,silent=true) }
                fresh.forEach { postAlert(c,l,d,it,announceId(key,it),alone) }
                if(alone) NotificationManagerCompat.from(c).cancel(alertSummaryId(key))
                else postAlertSummary(c,l,d,valid.ifEmpty { all },false)
                alertLines=all.size
            }
            else {
                // Nothing new: the lines change their form when the group grows to two (a rule came) or shrinks to one
                // (a notification was swiped away or has ended), so that a single line always names its place.
                val shownIds=groupIds(c,key)
                val left=d.alerts.filter { announceId(key,it) in shownIds }
                val alone=left.size+ruleIds.size==1
                if(left.isNotEmpty() && alone==(alertSummaryId(key) in shownIds)) {
                    left.forEach { postAlert(c,l,d,it,announceId(key,it),alone,silent=true) }
                    if(alone) NotificationManagerCompat.from(c).cancel(alertSummaryId(key))
                    else postAlertSummary(c,l,d,valid.ifEmpty { left },false)
                }
                alertLines=left.size
            }
'''
FINAL='''        // A place without warning notifications: two or more rule notifications are grouped under the header of the place.
        if(alertLines==0) { if(ruleIds.size>=2) postRuleSummary(c,l,ruleIds.size) else NotificationManagerCompat.from(c).cancel(alertSummaryId(key)) }
'''
DOC='''    /**
     * Persistent notifications of a place: the header (summary of the group) and under it one notification for each
     * warning, as the cards of the Alerts screen. Only a new warning makes a sound; the announcement of a warning gives
     * way to its persistent notification. [others] is the number of rule notifications in the group of the place.
     */
'''
HELPERS='''    // Rule notifications belong to the group of their place and stand below its warnings.
    private const val RuleIdBase=200000
    private fun ruleId(key:String,r:NotifyRule)=RuleIdBase+((r.id+"|"+key).hashCode() and 0xFFFF)
    private fun ruleLeft(until:Instant?):Long = until?.let { Duration.between(Instant.now(),it).toMillis() }?.takeIf { it>0L } ?: 0L
    private fun ruleDetail(text:String,until:Instant?,zone:java.time.ZoneId):String =
        if(until!=null && ruleLeft(until)>0L) text+"\\n"+uiText(R.string.nt_alert_until,until.atZone(zone).format(DayDm)) else text
    /** The end of a forecast phenomenon: the hour after the last hour of its run; null when the forecast does not show it. */
    private fun eventEnd(d:OverviewData,codes:Set<Int>,h:Int):Instant? {
        val hours=d.series;var j=-1
        if(d.code !in codes) { j=hours.take(h).indexOfFirst { it.code in codes };if(j<0) return null }
        while(j+1<hours.size && hours[j+1].code in codes) j++
        if(j<0) return hours.firstOrNull()?.time?.atZone(d.zone)?.toInstant()
        return hours[j].time.plusHours(1).atZone(d.zone).toInstant()
    }
    /** The header of a place that has only rule notifications; two or more of them are grouped under it. */
    private fun postRuleSummary(c:Context,l:Location,count:Int) {
        val key=BackgroundRefresh.locationKey(l)
        show(c,ChRules,alertSummaryId(key),l.name,uiText(R.string.nt_rules_count,count),null,null,location=key,group=alertGroup(key),summary=true,silent=true,subText=l.name)
    }
'''
SERIES='''package sk.perun.weather.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/** Hourly forecast of the metrics of the notification rules: it tells how long a fulfilled rule will hold. */
internal class RuleSeries(private val data:Map<String,Pair<LongArray,DoubleArray>>) {
    /** The start of the first coming hour that is not [fulfilled]; null when the metric is missing or the rule holds to the end. */
    fun end(metric:String,now:Instant,fulfilled:(Double)->Boolean):Instant? {
        val pair=data[metric] ?: return null
        val times=pair.first;val values=pair.second
        for(i in times.indices) { if(times[i]<=now.epochSecond) continue;val x=values[i];if(x.isFinite() && !fulfilled(x)) return Instant.ofEpochSecond(times[i]) }
        return null
    }
}

// Open-Meteo hourly variables of the weather metrics, in the canonical units of the rules; the other metrics are air quality variables.
private val RuleWeatherHourly=mapOf("temp" to "temperature_2m","feels" to "apparent_temperature","precip" to "precipitation","snow" to "snowfall","prob" to "precipitation_probability",
    "wind" to "wind_speed_10m","gust" to "wind_gusts_10m","cloud" to "cloud_cover","uv" to "uv_index","humidity" to "relative_humidity_2m","dew" to "dew_point_2m","visibility" to "visibility","pressure" to "pressure_msl")

private fun ruleHourly(url:String,names:Map<String,String>):Map<String,Pair<LongArray,DoubleArray>> = try {
    val h=JSONObject(String(fetchBytes(url),Charsets.UTF_8)).getJSONObject("hourly");val t=h.getJSONArray("time")
    val times=LongArray(t.length()) { t.optLong(it) }
    val out=HashMap<String,Pair<LongArray,DoubleArray>>()
    names.forEach { (metric,name) -> val a=h.optJSONArray(name);if(a!=null) out[metric]=Pair(times,DoubleArray(times.size) { a.optDouble(it,Double.NaN) }) }
    out
} catch(e:CancellationException) { throw e } catch(e:Exception) { emptyMap() }

/** [metrics] are keys of rule metrics without the events; [aqi] is the variable of the chosen air quality index. */
internal suspend fun loadRuleSeries(l:Location,model:String,metrics:Set<String>,aqi:String):RuleSeries = withContext(Dispatchers.IO) {
    val where="latitude=${l.latitude}&longitude=${l.longitude}&timeformat=unixtime&forecast_days=3"
    val weather=metrics.filter { it in RuleWeatherHourly }.associateWith { RuleWeatherHourly.getValue(it) }
    val air=metrics.filter { it !in RuleWeatherHourly }.associateWith { if(it=="aqi") aqi else it }
    val elevation=l.elevation?.let { "&elevation=$it" } ?: ""
    val modelQuery=if(model=="best_match" || model.isBlank()) "" else "&models="+URLEncoder.encode(model,"UTF-8")
    val out=HashMap<String,Pair<LongArray,DoubleArray>>()
    if(weather.isNotEmpty()) out.putAll(ruleHourly("https://api.open-meteo.com/v1/forecast?$where$elevation$modelQuery&hourly="+weather.values.distinct().joinToString(","),weather))
    if(air.isNotEmpty()) out.putAll(ruleHourly("https://air-quality-api.open-meteo.com/v1/air-quality?$where&hourly="+air.values.distinct().joinToString(","),air))
    RuleSeries(out)
}
'''

# ---- Notifications.kt
p=ui/'Notifications.kt'; s=p.read_text(encoding='utf-8')
s=lit(s,'''    // One persistent notification for each kind of warning of a place: the same set of hazards, official or local.
    private fun alertKind(a:UnifiedAlert)=(if(a.local) "L:" else "O:")+a.hazards.map { it.name }.sorted().joinToString(",")
''','''    // One persistent notification for each warning of a place, as the cards of the Alerts screen.
    private fun alertKind(a:UnifiedAlert)=alertRef(a)+"|"+a.level.key+"|"+a.start.epochSecond
''','alertKind')
s=lit(s,'it.id>=80000','it.id in 80000 until RuleIdBase','kind id range',2)
s=lit(s,'announced by the notification of its kind.','announced by its persistent notification.','comment')
s=sub(s,r'^            if\(s\.alertsOngoing\) \{ if\(shown\.isEmpty\(\)\) cancelAlertSummary\(c,key\) else postOngoingAlerts\(c,l,d,shown,loud\) \}\n(?:.*\n)*?(?=            if\(changed\) p\.edit\(\)\.putStringSet\("sentAlerts")',lambda m:TAIL,'alert tail')
s=sub(s,r'^        val rules=s\.rules\.filter \{ locMatch\(it\.loc,key,active\) \}\n        if\(rules\.isNotEmpty\(\)\) \{\n(?:.*\n)*?            ed\.apply\(\)\n        \}\n(?=    \}\n)',lambda m:FINAL,'old rules block')
s=sub(s,r'^        val alertsHere=',lambda m:RULES+m.group(0),'alertsHere')
s=sub(s,r'^    /\*\*\n     \* Persistent notifications of a place:(?:.*\n)*?     \*/\n(?=    private fun postOngoingAlerts\()',lambda m:DOC,'ongoing doc')
s=lit(s,'valid:List<UnifiedAlert>,loud:Set<String>) {','valid:List<UnifiedAlert>,loud:Set<String>,others:Int) {','postOngoingAlerts signature')
s=lit(s,';val alone=kinds.size==1\n',';val alone=kinds.size+others==1\n','ongoing alone')
SHOW=r'        show\(c,ChRules,3000\+.*?,title,l\.name,null,null,(target=.*?),detail=(.*)\)\n'
EXTRA=',id:Int,until:Instant?,zone:java.time.ZoneId,silent:Boolean) {\n'
def post(before):
    def f(m):
        return (before+m.group(1)+EXTRA+m.group(2)+'        show(c,ChRules,id,title,l.name,null,null,'+m.group(3)+',detail=ruleDetail('+m.group(4)+',until,zone),\n'
            '            group=alertGroup(BackgroundRefresh.locationKey(l)),sortKey="9"+title,timeout=ruleLeft(until),silent=silent,subText=l.name)\n')
    return f
s=sub(s,r'^(    private fun postRule\(c:Context,l:Location,r:NotifyRule,m:RuleMetric,v:Double,u:WeatherUnits,gas:AirGasUnit)\) \{\n((?:(?!    \}\n).*\n)*?)'+SHOW,post(HELPERS),'postRule')
s=sub(s,r'^(    private fun postEvent\(c:Context,l:Location,r:NotifyRule,m:RuleMetric,h:Int)\) \{\n((?:(?!    \}\n).*\n)*?)'+SHOW,post(''),'postEvent')
p.write_text(s,encoding='utf-8')
(ui/'RuleForecast.kt').write_text(SERIES,encoding='utf-8')

# ---- AlertsScreen.kt: a tap on the header of the detail closes it
p=ui/'AlertsScreen.kt'; s=p.read_text(encoding='utf-8')
s=lit(s,'''        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            a.hazards.ifEmpty { listOf(AlertHazard.Generic) }.take(MaxIcons).forEach { AlertGlyph(it,inkOf(a),22.dp) }''','''        // A tap on the header closes the detail, as the cross does.
        Row(Modifier.fillMaxWidth().clickable(onClick=onClose),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            a.hazards.ifEmpty { listOf(AlertHazard.Generic) }.take(MaxIcons).forEach { AlertGlyph(it,inkOf(a),22.dp) }''','detail header')
if 'import androidx.compose.foundation.clickable\n' not in s and 'import androidx.compose.foundation.*\n' not in s:
    s=sub(s,r'\A((?:.*\n)*?)(import )',lambda m:m.group(1)+'import androidx.compose.foundation.clickable\n'+m.group(2),'clickable import')
p.write_text(s,encoding='utf-8')

# ---- the note of the persistent notification
for path,old,new in [('values/strings_alert_detail.xml','jedno oznámenie pre každý druh výstrahy','jedno oznámenie pre každú výstrahu'),
                     ('values-en/strings_alert_detail.xml','one notification for each kind of warning','one notification for each warning')]:
    p=res/path; p.write_text(lit(p.read_text(encoding='utf-8'),old,new,path),encoding='utf-8')
print('applied p01_notifications_141: Notifications.kt, RuleForecast.kt, AlertsScreen.kt and 2 string files')
