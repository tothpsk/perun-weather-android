from pathlib import Path
NL = chr(10)
CRLF = chr(13) + NL
ui = Path('app/src/main/java/sk/perun/weather/ui')
res = Path('app/src/main/res')
files = {}
def get(name):
    if name not in files:
        raw = (ui / name).read_bytes().decode('utf-8')
        files[name] = [CRLF in raw, raw.replace(CRLF, NL)]
    return files[name]
def swap(name, old, new, count=1):
    f = get(name)
    if f[1].count(old) != count:
        raise SystemExit(name + ': unexpected text, nothing written: ' + old[:90])
    f[1] = f[1].replace(old, new)
def after(name, anchor, added):
    swap(name, anchor, anchor + added)
def before(name, anchor, added):
    swap(name, anchor, added + anchor)

# 1. Meteogram widget: the bars of the precipitation have the colour of its kind, as in the app.
M = 'MeteogramChart.kt'
after(M, '"uv" to "uv_index","visibility" to "visibility")' + NL, '''/** The parts of the precipitation by the hour: they give the bars of the Meteogram widget the colour of rain, showers, snow or rain with snow. */
internal val MeteoPrecipKinds=listOf("rain","showers","snowfall")
''')
after(M, 'val wanted=keys.filter { it in MeteoExtraKeys }' + NL, '    val kinds="precip" in keys' + NL)
swap(M, 'if(wanted.isEmpty()) return@withContext', 'if(wanted.isEmpty() && !kinds) return@withContext')
swap(M, 'for((key,name) in MeteoExtraKeys) {', 'for((key,name) in MeteoExtraKeys+MeteoPrecipKinds.associateWith { it }) {')
swap(M, '&hourly="+MeteoExtraKeys.values.joinToString(",")', '&hourly="+(MeteoExtraKeys.values+MeteoPrecipKinds).joinToString(",")')
swap(M, 'wanted.any { it !in own }) {', '(wanted.any { it !in own } || (kinds && ("rain" !in own || "snowfall" !in own)))) {')
swap(M, 'p.color=android.graphics.Color.argb((90+chance*1.6).toInt().coerceIn(0,255),79,195,247)', '''// The colour of the kind of precipitation, as in the app. Snow is darker under dark text, where the light colour would not be seen.
                val part={ key:String -> more[key]?.get(h.time) ?: Double.NaN }
                val wet=part("rain")>0.0 || part("showers")>0.0;val snowy=part("snowfall")>0.0
                val rgb=when { wet && snowy->0xB388FF;snowy->if(dark) 0x7F93B0 else 0xE8EAF0;wet && !(part("rain")>0.0)->0x2196C9;else->0x4FC3F7 }
                p.color=android.graphics.Color.argb((90+chance*1.6).toInt().coerceIn(0,255),(rgb shr 16) and 255,(rgb shr 8) and 255,rgb and 255)''')
swap('Widgets.kt', 'cfg.meteo.any { it in MeteoExtraKeys })', 'cfg.meteo.any { it in MeteoExtraKeys || it=="precip" })')

# 2. Now, wave tile: sixteen directions and the degrees in the text.
W = 'MarineOverview.kt'
swap(W, 'uiText(R.string.ui_smer_sirenia_e0efaa7c)+cardinal(bearing)', 'uiText(R.string.ui_smer_sirenia_e0efaa7c)+waveHeading(bearing)', 2)
swap(W, 'Text(sub,color=ForecastMuted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)', '''// The heading of the waves keeps its degrees: it is shown without the words when the row is too narrow for both.
                        var narrow by remember(sub) {mutableStateOf(false)}
                        Text(if(narrow && tile==MarineTile.Waves && bearing.isFinite()) waveHeading(bearing) else sub,color=ForecastMuted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,onTextLayout={if(it.hasVisualOverflow) narrow=true})''')
before(W, 'internal class OverviewCell(', '''// Sixteen directions from the eight of cardinal(): a direction between two of them joins the nearer of north, east, south and west with the nearer of the other four.
private fun cardinal16(degrees:Double):String {
    if(!degrees.isFinite()) return "—"
    val i=((degrees/22.5+0.5).toInt()%16+16)%16
    return if(i%2==0) cardinal(i*22.5) else cardinal(((i+1)/4*4%16)*22.5)+cardinal((i/4*4+2)*22.5)
}
private fun waveHeading(bearing:Double):String = cardinal16(bearing)+" · "+Math.round(bearing)%360+"°"
''')

# 3. Alerts: the rows of the chart start with the icon of the hazard in the colour of the level; the name above the bar is left out.
A = 'AlertsScreen.kt'
after(A, 'import androidx.compose.ui.input.pointer.positionChanged' + NL, '''import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
''')
after(A, 'private val SelectedEdge=Color(0xFF4FC3F7)' + NL, '''// The column of the hazard icons in front of the bars of the chart.
private val GanttGutter=28.dp
''')
swap(A, 'fun pick(px: Float) { cursor=from.plusMillis(((px/size.width).coerceIn(0f,1f)*total).toLong()) }',
     'val gutter=GanttGutter.toPx(); fun pick(px: Float) { cursor=from.plusMillis((((px-gutter)/(size.width-gutter).coerceAtLeast(1f)).coerceIn(0f,1f)*total).toLong()) }')
swap(A, 'val axisPx=axisHeight.toPx(); val pad=3.dp.toPx()', 'val axisPx=axisHeight.toPx(); val pad=3.dp.toPx(); val gutter=GanttGutter.toPx()')
swap(A, 'val x=frac(t)*size.width' + NL, 'val x=gutter+frac(t)*(size.width-gutter)' + NL)
swap(A, 'val cx=frac(t)*size.width; val gap=6.dp.toPx()', 'val cx=gutter+frac(t)*(size.width-gutter); val gap=6.dp.toPx()')
swap(A, 'Column(Modifier.fillMaxWidth().clickable { onSelect(idx) }.padding(vertical=2.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {',
     'Row(Modifier.fillMaxWidth().heightIn(min=28.dp).clickable(onClickLabel=labelOf(a)) { onSelect(idx) }.semantics { contentDescription=labelOf(a) },verticalAlignment=Alignment.CenterVertically) {')
swap(A, 'Text(labelOf(a),fontSize=12.sp,color=MainText,maxLines=2)',
     '''// The icon stands for the name; the name is in the card below and beside the cursor.
                    Box(Modifier.width(GanttGutter)) { AlertGlyph(alertHazard(a.event+" "+a.title,a.awareness),if (levelOf(a)=="neutral") MainText else colorOf(a),20.dp) }''')
swap(A, 'Canvas(Modifier.fillMaxWidth().height(axisHeight)) {', 'Canvas(Modifier.fillMaxWidth().padding(start=GanttGutter).height(axisHeight)) {')

# 4. Alerts: a warning passed on by MeteoAlarm names the national service that issued it.
swap(A, 'val surface=LevelSurface[levelOf(a)]', 'val uri=LocalUriHandler.current; val surface=LevelSurface[levelOf(a)]')
after(A, 'Text(timeRange(a,z),fontSize=13.sp)', '''
                // MeteoAlarm passes on the warnings of the national services: the service that issued the warning and its page.
                if (a.origin.isNotBlank()) Text(uiText(R.string.alert_origin,a.origin),fontSize=13.sp,color=ForecastMuted)
                val page=a.originUri.takeIf { a.origin.isNotBlank() && (it.startsWith("https://") || it.startsWith("http://")) }
                if (page!=null) Text(page,Modifier.clickable(onClickLabel=uiText(R.string.alert_origin_open)) { runCatching { uri.openUri(page) } },fontSize=13.sp,color=SelectedEdge,maxLines=2,overflow=TextOverflow.Ellipsis)''')
O = 'OverviewAlerts.kt'
after(O, 'val awareness: String = "",', '''
    // The national service that issued a warning passed on by MeteoAlarm (senderName of its CAP message), when the message could be read.
    val origin: String = "",''')
after(O, 'val result = mutableListOf<AlertItem>()', '; val origins = HashMap<String, MeteoOrigin?>()')
after(O, 'e.text(ATOM_NS, "id")?.let { "atom:$it" }.orEmpty()', NL + '        val origin = meteoOrigin(e, origins)')
after(O, 'via = setOf("MeteoAlarm"),', ' originUri = origin?.web.orEmpty(), awareness = origin?.awareness.orEmpty(), origin = origin?.sender.orEmpty(),')
before(O, '// A place gets the entries of the warning areas it lies in.', '''private class MeteoOrigin(val sender: String, val web: String, val awareness: String)
// The CAP message behind a feed entry names the national service that issued the warning. One message is read for a warning
// and at most eight for a place; a message that cannot be read leaves the warning without its origin.
private fun meteoOrigin(e: Element, seen: HashMap<String, MeteoOrigin?>): MeteoOrigin? {
    val links = e.getElementsByTagNameNS(ATOM_NS, "link"); var url: String? = null
    for (i in 0 until links.length) {
        val link = links.item(i) as? Element ?: continue
        if (link.getAttribute("type") == "application/cap+xml") { url = link.getAttribute("href"); break }
    }
    val href = url?.takeIf { it.startsWith("https://") } ?: return null
    val key = href.substringBefore('?')
    if (seen.containsKey(key)) return seen[key]
    if (seen.size >= 8) return null
    val origin = try {
        val infos = parseXml(fetchBytes(href)).documentElement.children(CAP_NS, "info")
        val index = href.substringAfter("index_info=", "").takeWhile { it.isDigit() }.toIntOrNull()
        val info = index?.let { infos.getOrNull(it) } ?: infos.firstOrNull()
        if (info == null) null else MeteoOrigin(info.childText("senderName").orEmpty(), info.childText("web").orEmpty(), info.capParameter("awareness_type").orEmpty())
    } catch (x: Exception) { null }
    seen[key] = origin
    return origin
}

''')

strings = {
    'values': ('Vydavateľ: %1$s', 'Otvoriť stránku vydavateľa'),
    'values-en': ('Issued by: %1$s', 'Open the page of the issuer'),
}
new = {}
for folder, (origin, opening) in strings.items():
    path = res / folder / 'strings_alert_origin.xml'
    if path.exists():
        raise SystemExit(str(path) + ': exists, nothing written')
    new[path] = ('<?xml version="1.0" encoding="utf-8"?>' + NL + '<resources>' + NL
                 + '    <string name="alert_origin">"' + origin + '"</string>' + NL
                 + '    <string name="alert_origin_open">"' + opening + '"</string>' + NL + '</resources>' + NL)

for name, (crlf, text) in files.items():
    (ui / name).write_bytes((text.replace(NL, CRLF) if crlf else text).encode('utf-8'))
for path, text in new.items():
    path.write_bytes(text.encode('utf-8'))
print('applied p01_release_131: ' + ', '.join(sorted(files)) + ' and 2 string files')
