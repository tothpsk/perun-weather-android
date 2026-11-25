package sk.perun.weather.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale

/**
 * One set of warning icons for the app, the home-screen widgets and the notifications. Outlines in the 24 x 24 grid,
 * drawn with the line of the other icons (1.75); every drawing fits the circle of diameter 20 around the centre.
 * The kinds follow the MeteoAlarm awareness_type (1-15, 11 is unused) and the other warnings of the KDE public alert
 * server and LibreWXR.
 */
internal enum class AlertHazard(private val data:String) {
    // Vietor (1 Wind)
    Wind("M 4.84,9.61 l 8.75,0 a 2.39,2.39 0 1,0 -2.39,-2.39 M 4.84,14.39 l 11.93,0 a 2.39,2.39 0 1,1 -2.39,2.39 M 4.84,12 l 5.57,0"),
    // Sneh (2 snow-ice)
    Snow("M 12,2.91 l 0,18.17 M 4.1,7.46 l 15.81,9.09 M 4.1,16.54 L 19.9,7.46 M 9.27,4.73 l 2.73,1.82 l 2.73,-1.82 M 9.27,19.27 l 2.73,-1.82 l 2.73,1.82"),
    // Poľadovica, námraza (2 snow-ice)
    Ice("M 4.03,7.57 l 15.94,0 M 6.24,7.57 l 1.33,5.76 L 8.9,7.57 M 11.11,7.57 l 1.33,8.86 L 13.77,7.57 M 15.98,7.57 l 1.33,4.87 L 18.64,7.57"),
    // Búrky (3 Thunderstorm)
    Storm("M 7.89,14.16 l -0.49,0 a 3.93,3.93 0 0,1 0,-7.87 a 5.9,5.9 0 0,1 11.11,1.47 A 3.44,3.44 0 0,1 18.22,14.16 L 17.73,14.16 M 13.79,11.21 l -2.95,4.92 l 3.93,0 l -2.95,4.92"),
    // Hmla (4 Fog)
    Fog("M 8.35,13.56 a 3.19,3.19 0 0,1 0,-6.39 a 4.79,4.79 0 0,1 9.02,1.2 A 2.79,2.79 0 0,1 16.73,13.56 M 5.95,16.35 l 12.77,0 M 7.55,19.54 l 9.58,0"),
    // Horúčava (5 high-temperature)
    Heat("M 6.44,6.05 a 1.65,1.65 0 0,1 3.29,0 l 0,7.41 a 3.29,3.29 0 1,1 -3.29,0 Z M 13.28,8.93 a 1.81,1.81 0 1,0 3.62,0 a 1.81,1.81 0 0,0 -3.62,0 M 15.09,4.81 l 0,0.82 M 15.09,12.23 l 0,0.82 M 10.97,8.93 l 0.82,0 M 18.38,8.93 l 0.82,0 M 12.21,6.05 l 0.58,0.58 M 17.39,11.24 l 0.58,0.58 M 17.97,6.05 l -0.58,0.58 M 12.21,11.81 l 0.58,-0.58"),
    // Chlad, mráz (6 low-temperature)
    Cold("M 6.98,5.69 a 1.75,1.75 0 0,1 3.49,0 l 0,7.86 a 3.49,3.49 0 1,1 -3.49,0 Z M 15.71,4.82 l 0,6.98 M 12.65,6.56 l 6.11,3.49 M 12.65,10.06 l 6.11,-3.49"),
    // Pobrežné javy (7 coastalevent)
    Coastal("M 4.34,13.89 c 2.3,0 3.06,-1.53 3.83,-3.83 c 0.77,-2.68 3.83,-4.21 6.51,-1.91 c -1.91,-0.46 -3.29,0.77 -2.68,2.3 c 0.61,1.84 3.06,2.68 5.74,2.68 M 4.34,16.96 l 15.31,0"),
    // Lesné požiare (8 forest-fire)
    Fire("M 12,2.88 c 1.01,4.05 6.08,6.08 6.08,12.16 a 6.08,6.08 0 0,1 -12.16,0 c 0,-3.04 2.03,-5.07 3.04,-7.09 c 0,2.03 1.01,3.04 2.03,3.04 c 0,-3.04 0,-5.07 1.01,-8.1 Z"),
    // Lavíny (9 avalanches)
    Avalanche("M 5.19,18.07 L 10.34,8.51 l 2.94,4.41 l 1.47,-2.21 l 3.68,7.36 Z M 14.39,7.03 a 1.1,1.1 0 0,0 2.21,0 a 1.1,1.1 0 0,0 -2.21,0 M 17.33,9.24 a 0.74,0.74 0 0,0 1.47,0 a 0.74,0.74 0 0,0 -1.47,0"),
    // Dážď (10 Rain)
    Rain("M 7.76,14.5 a 3.71,3.71 0 0,1 0,-7.42 a 5.56,5.56 0 0,1 10.48,1.39 A 3.25,3.25 0 0,1 17.5,14.5 M 8.68,17.29 l -0.93,2.78 m 4.64,-2.78 l -0.93,2.78 m 4.64,-2.78 l -0.93,2.78"),
    // Povodne (12 flooding)
    Flood("M 5.22,15.39 c 1.36,0 1.36,-1.02 2.71,-1.02 s 1.36,1.02 2.71,1.02 s 1.36,-1.02 2.71,-1.02 s 1.36,1.02 2.71,1.02 s 1.36,-1.02 2.71,-1.02 M 5.22,18.1 c 1.36,0 1.36,-1.02 2.71,-1.02 s 1.36,1.02 2.71,1.02 s 1.36,-1.02 2.71,-1.02 s 1.36,1.02 2.71,1.02 s 1.36,-1.02 2.71,-1.02 M 12,5.9 l 0,6.1 M 9.63,9.63 L 12,12 l 2.37,-2.37"),
    // Prívalové povodne (13 rain-flood)
    RainFlood("M 8.85,12.6 a 2.52,2.52 0 0,1 0,-5.04 a 3.78,3.78 0 0,1 7.12,0.95 A 2.21,2.21 0 0,1 15.47,12.6 M 9.79,14.18 l -0.63,1.58 m 3.15,-1.58 l -0.63,1.58 m 3.15,-1.58 l -0.63,1.58 M 5.69,18.59 c 1.26,0 1.26,-0.95 2.52,-0.95 s 1.26,0.95 2.52,0.95 s 1.26,-0.95 2.52,-0.95 s 1.26,0.95 2.52,0.95 s 1.26,-0.95 2.52,-0.95"),
    // Nebezpečenstvo na mori (14 Marine-Hazard)
    Marine("M 6.58,13.69 l 10.85,0 l -1.69,2.37 l -7.46,0 Z M 12,13.69 L 12,5.9 M 12,5.9 l 3.73,6.1 L 12,12 M 5.22,18.1 c 1.36,0 2.03,-0.68 3.39,-0.68 s 2.03,0.68 3.39,0.68 s 2.03,-0.68 3.39,-0.68 s 2.03,0.68 3.39,0.68"),
    // Sucho (15 Drought)
    Drought("M 9.13,8.95 a 2.87,2.87 0 0,0 5.75,0 a 2.87,2.87 0 0,0 -5.75,0 M 12,3.38 l 0,0.9 M 6.43,8.95 l 0.9,0 M 16.67,8.95 l 0.9,0 M 8.05,5 l 0.63,0.63 M 15.95,5 l -0.63,0.63 M 3.92,15.23 l 16.17,0 M 7.51,15.23 l 1.35,2.25 l -0.9,2.69 M 12,15.23 l -0.9,2.25 l 1.35,3.14 M 16.49,15.23 l -1.35,2.69 l 0.9,2.25"),
    // Krupobitie (KDE, LibreWXR)
    Hail("M 7.92,13.11 a 3.57,3.57 0 0,1 0,-7.13 a 5.35,5.35 0 0,1 10.08,1.34 A 3.12,3.12 0 0,1 17.28,13.11 M 7.56,17.57 a 1.25,1.25 0 1,0 2.5,0 a 1.25,1.25 0 0,0 -2.5,0 M 11.58,19.8 a 1.25,1.25 0 1,0 2.5,0 a 1.25,1.25 0 1,0 -2.5,0 M 15.14,17.13 a 1.25,1.25 0 1,0 2.5,0 a 1.25,1.25 0 1,0 -2.5,0"),
    // Prach, piesok (KDE, LibreWXR)
    Dust("M 3.88,7.85 l 9.6,0 M 3.88,11.35 l 13.1,0 M 3.88,14.84 l 7.86,0 M 16.45,7.85 a 0.52,0.52 0 1,0 1.05,0 a 0.52,0.52 0 1,0 -1.05,0 M 19.07,11.35 a 0.52,0.52 0 1,0 1.05,0 a 0.52,0.52 0 1,0 -1.05,0 M 14.27,14.84 a 0.52,0.52 0 0,0 1.05,0 a 0.52,0.52 0 1,0 -1.05,0 M 17.76,16.15 a 0.52,0.52 0 1,0 1.05,0 a 0.52,0.52 0 1,0 -1.05,0"),
    // Tropická cyklóna (KDE, LibreWXR)
    Cyclone("M 9.6,12 a 2.4,2.4 0 0,0 4.8,0 a 2.4,2.4 0 0,0 -4.8,0 M 12,9.6 C 12,6.8 14,4.8 17.6,4.8 M 12,14.4 C 12,17.2 10,19.2 6.4,19.2"),
    // Tornádo (KDE, LibreWXR)
    Tornado("M 5.18,5.94 l 13.63,0 M 7.08,8.97 l 9.85,0 M 8.97,12 l 6.06,0 M 10.49,15.03 l 3.41,0 M 11.62,18.06 l 1.51,0"),
    // Zemetrasenie (KDE, LibreWXR)
    Earthquake("M 2.98,13.35 l 3.16,0 l 1.8,-5.41 l 2.7,10.82 l 2.7,-13.52 l 2.7,9.92 l 1.8,-1.8 L 21.02,13.35"),
    // Cunami (KDE, LibreWXR)
    Tsunami("M 4.7,17.47 c 0,-6.57 4.38,-10.95 9.49,-10.95 c 2.19,0 4.01,0.73 5.11,1.82 c -2.55,-0.73 -5.47,0.36 -5.47,3.28 c 0,2.19 2.19,3.28 5.47,3.28 M 4.7,17.47 l 14.6,0"),
    // Zosuvy, prúdy bahna (KDE, LibreWXR)
    Landslide("M 4.99,6.16 l 0,11.68 l 14.01,0 M 4.99,6.16 c 2.34,1.56 5.45,6.23 7.4,11.68 M 13.48,11.22 a 1.25,1.25 0 0,0 2.49,0 a 1.25,1.25 0 1,0 -2.49,0 M 16.44,13.95 a 1.01,1.01 0 0,0 2.02,0 a 1.01,1.01 0 0,0 -2.02,0 M 14.72,15.89 a 0.78,0.78 0 0,0 1.56,0 a 0.78,0.78 0 0,0 -1.56,0"),
    // Sopka (KDE, LibreWXR)
    Volcano("M 5.55,18.45 l 3.94,-7.16 l 5.01,0 L 18.45,18.45 Z M 10.57,11.28 l 1.43,-1.79 l 1.43,1.79 M 12,7.34 L 12,5.55 M 9.49,7.34 L 8.63,6.27 M 14.51,7.34 L 15.37,6.27"),
    // Kvalita ovzdušia, dym (KDE, LibreWXR)
    Air("M 5.64,18.54 l 0,-5.88 l 3.67,2.2 l 0,-2.2 l 3.67,2.2 L 12.99,9.72 l 2.94,0 l 0,8.82 Z M 13.73,7.15 a 1.1,1.1 0 0,0 2.2,0 a 1.1,1.1 0 0,0 -2.2,0 M 16.89,6.2 a 0.73,0.73 0 0,0 1.47,0 a 0.73,0.73 0 0,0 -1.47,0"),
    // Iná výstraha (ostatné)
    Generic("M 12,6.09 L 18.95,17.91 L 5.05,17.91 Z M 12,10.96 l 0,2.78 M 12,15.82 l 0,0.01"),
    ;
    val path:Path by lazy { PathParser().parsePathString(data).toNodes().toPath() }
}

internal const val AlertLine=1.75f

private val AwarenessTypes=mapOf(
    1 to AlertHazard.Wind,2 to AlertHazard.Snow,3 to AlertHazard.Storm,4 to AlertHazard.Fog,5 to AlertHazard.Heat,
    6 to AlertHazard.Cold,7 to AlertHazard.Coastal,8 to AlertHazard.Fire,9 to AlertHazard.Avalanche,10 to AlertHazard.Rain,
    12 to AlertHazard.Flood,13 to AlertHazard.RainFlood,14 to AlertHazard.Marine,15 to AlertHazard.Drought,
)
private val AwarenessInText=Regex("""awareness_type\s*=\s*(\d+)""")

// Words of the warning names, without diacritics and in lower case. A word that starts with "=" must match a whole
// word; a phrase with a space or a hyphen is looked for in the text; any other word matches the start of a word.
private val HazardWords:List<Pair<AlertHazard,List<String>>> = listOf(
    AlertHazard.Tsunami to listOf("tsunami","цунами"),
    AlertHazard.Earthquake to listOf("earthquake","seism","zemetras","zemetres","erdbeben","terremoto","sismo","gempa","землетряс","trzesien"),
    AlertHazard.Volcano to listOf("volcan","vulkan","вулкан","wulkan","=ash","ashfall"),
    AlertHazard.Cyclone to listOf("hurrican","typhoon","cyclon","tropical storm","hurikan","tajfun","тайфун","циклон","huracan","furacao"),
    AlertHazard.Tornado to listOf("tornado","waterspout","smrst","торнадо","смерч"),
    AlertHazard.Avalanche to listOf("avalanch","lavin","lawin","лавин","valanga"),
    AlertHazard.Landslide to listOf("landslid","mudflow","mudslide","debris flow","derrumbe","desliza","erdrutsch","bergsturz","felssturz","zosuv","sesuv","osuwisk","оползн","=сель","longsor","=frana"),
    AlertHazard.Fire to listOf("=fire","bushfire","wildfire","grassfire","fire weather","fire danger","poziar","pozar","waldbrand","=brand","incendi","пожар"),
    AlertHazard.Drought to listOf("drought","=sucho","durre","sequia","secheresse","засух","susza","kekeringan","siccita"),
    AlertHazard.Coastal to listOf("coastal","storm surge","=surge","high surf","=surf","high tide","sturmflut","submersion","coster","pobrez","wave height","vagues","=waves","rip current","прибрежн"),
    AlertHazard.RainFlood to listOf("rain-flood","rain flood","flash flood","prival","sturzflut","inundacion urbana","crue soudaine"),
    AlertHazard.Flood to listOf("flood","povod","zaplav","hochwasser","uberschwemm","inond","inundac","alluvion","паводок","наводнен","половод","powodz","banjir","=crue","alluvione"),
    AlertHazard.Marine to listOf("=gale","small craft","marine","=seas","high seas","freezing spray","морск"),
    AlertHazard.Hail to listOf("=hail","krupobit","kroupy","hagel","grele","granizo","=град","=grad"),
    AlertHazard.Storm to listOf("thunder","burk","bourk","gewitter","orage","tormenta","tempestade","гроз","=burza","=burze","petir","convect","konvek","lightning","blesk"),
    AlertHazard.Dust to listOf("=dust","raised dust","duststorm","=sand","sandstorm","prach","staub","poussiere","polvo","пыл","pył","calima"),
    AlertHazard.Ice to listOf("icing","=icy","freezing rain","freezing drizzle","=glaze","poladov","namraz","black ice","glatteis","verglas","hielo","oblodzen","гололед","изморо"),
    AlertHazard.Snow to listOf("=snow","snowfall","snowstorm","blizzard","sneh","snez","=snih","schnee","neige","nieve","nevad","nevic","снег","snieg","=sneg","vanic","метел"),
    AlertHazard.Ice to listOf("=ice"),
    AlertHazard.Cold to listOf("=cold","frost","=freeze","chlad","mraz","kalte","=froid","=gel","gelee","helada","мороз","холод","zimno","=mroz","low temp","nizke tepl","wind chill"),
    AlertHazard.Heat to listOf("=heat","heatwave","horuc","hitze","canicul","chaleur","=calor","жар","upal","high temp","vysoke tepl","=hot"),
    AlertHazard.Fog to listOf("=fog","=hmla","=mlha","nebel","brouillard","niebla","туман","mgła","mgla","=mist"),
    AlertHazard.Rain to listOf("=rain","rainfall","rainstorm","=rains","dazd","=dest","destov","zrazk","srazk","regen","pluie","lluvia","chuva","дожд","ливн","deszcz","=opady","hujan","precip","aguacero","pioggia","monsoon"),
    AlertHazard.Wind to listOf("wind","gust","vietor","vetr","vitr","naraz","sturm","orkan","=vent","=vents","rafale","viento","vento","ветер","ветр","wiatr","angin","brisk","=storm"),
    AlertHazard.Air to listOf("air quality","smog","ozone","ovzdus","luftqual","pollution","smoke","=dym","=haze","aqi"),
)

private fun plain(text:String):String =
    Normalizer.normalize(text,Normalizer.Form.NFD).filter { Character.getType(it)!=Character.NON_SPACING_MARK.toInt() }.lowercase(Locale.ROOT)

private fun matches(t:String,words:List<String>,term:String):Boolean = when {
    term.startsWith("=") -> term.substring(1) in words
    ' ' in term || '-' in term -> term in t
    else -> words.any { it.startsWith(term) }
}

/** The kind of a warning: the MeteoAlarm awareness_type when the issuer sends it, otherwise the words of its name. */
internal fun alertHazard(text:String,awareness:String="",headline:String=""):AlertHazard {
    val t=plain(text)
    val words=t.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
    fun has(kind:AlertHazard)=HazardWords.any { (k,terms)->k==kind && terms.any { matches(t,words,it) } }
    val code=awareness.substringBefore(';').trim().toIntOrNull() ?: AwarenessInText.find(t)?.groupValues?.get(1)?.toIntOrNull()
    val byCode=code?.let { AwarenessTypes[it] }
    fun snowOrIce(value:String):AlertHazard? {
        val normalized=plain(value)
        val tokens=normalized.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        fun hasKind(kind:AlertHazard)=HazardWords.any { (k,terms)->k==kind && terms.any { matches(normalized,tokens,it) } }
        val ice=hasKind(AlertHazard.Ice)
        val snow=hasKind(AlertHazard.Snow)
        return when { snow->AlertHazard.Snow; ice->AlertHazard.Ice; else->null }
    }
    if(byCode==AlertHazard.Snow) return snowOrIce(text) ?: snowOrIce(headline) ?: AlertHazard.Snow
    if(byCode!=null) return byCode
    return HazardWords.firstOrNull { (_,terms)->terms.any { matches(t,words,it) } }?.first
        ?: if(headline.isNotBlank()) alertHazard(headline) else AlertHazard.Generic
}

internal fun DrawScope.drawAlertHazard(kind:AlertHazard,color:Color) {
    val k=size.width/24f
    scale(k,k,Offset.Zero) { drawPath(kind.path,color,style=Stroke(AlertLine,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
}

@Composable internal fun AlertGlyph(kind:AlertHazard,color:Color,dimension:Dp=18.dp) {
    Canvas(Modifier.size(dimension)) { drawAlertHazard(kind,color) }
}

/** The same drawing as a picture for the widgets and the notifications. */
internal fun alertHazardBitmap(kind:AlertHazard,color:Int,size:Int):Bitmap {
    val px=size.coerceAtLeast(1)
    val image=ImageBitmap(px,px)
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(image),Size(px.toFloat(),px.toFloat())) { drawAlertHazard(kind,Color(color)) }
    return image.asAndroidBitmap()
}
