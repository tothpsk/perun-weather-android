package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.abs

private const val MarineTilesKey="marineOverviewTiles"
private val SeaRise=Color(0xFF4FC3F7)
private val SeaFall=Color(0xFFFFA45B)
private val SeaScaleGrey=Color(0xFF5B6577)
private data class SeaBand(val color:Color,val label:String,val range:String)
private fun seaTemperatureBands()=listOf(
    SeaBand(Color(0xFF4FC3F7),uiText(R.string.sea_temp_cold),uiText(R.string.sea_temp_cold_range)),
    SeaBand(Color(0xFF4D8D7C),uiText(R.string.sea_temp_cool),uiText(R.string.sea_temp_cool_range)),
    SeaBand(Color(0xFF66D17A),uiText(R.string.sea_temp_pleasant),uiText(R.string.sea_temp_pleasant_range)),
    SeaBand(Color(0xFFFFB74D),uiText(R.string.sea_temp_warm),uiText(R.string.sea_temp_warm_range)),
    SeaBand(Color(0xFFFF6B6B),uiText(R.string.sea_temp_hot),uiText(R.string.sea_temp_hot_range)),
)
private fun waveHeightBands()=listOf(
    SeaBand(Color(0xFF66D17A),uiText(R.string.wave_calm),uiText(R.string.wave_calm_range)),
    SeaBand(Color(0xFFB5D46A),uiText(R.string.wave_slight),uiText(R.string.wave_slight_range)),
    SeaBand(Color(0xFFFFD166),uiText(R.string.wave_moderate),uiText(R.string.wave_moderate_range)),
    SeaBand(Color(0xFFFB8C00),uiText(R.string.wave_rough),uiText(R.string.wave_rough_range)),
    SeaBand(Color(0xFFE53935),uiText(R.string.wave_high),uiText(R.string.wave_high_range)),
)
private fun wavePeriodBands()=listOf(
    SeaBand(Color(0xFF8FD3F4),uiText(R.string.period_short),uiText(R.string.period_short_range)),
    SeaBand(Color(0xFF4FC3F7),uiText(R.string.period_medium),uiText(R.string.period_medium_range)),
    SeaBand(Color(0xFF7C6FCD),uiText(R.string.period_long),uiText(R.string.period_long_range)),
)
private fun seaLevelBands()=listOf(
    SeaBand(SeaRise,uiText(R.string.ui_stupa_c05574b4),uiText(R.string.sea_level_rise_range)),
    SeaBand(Color(0xFFBA9EDB),uiText(R.string.ui_stabilna_be3f811f),uiText(R.string.sea_level_stable_range)),
    SeaBand(SeaFall,uiText(R.string.ui_klesa_54707dc3),uiText(R.string.sea_level_fall_range)),
)
private fun seaBandFor(tile:MarineTile,raw:Double,next:Double):SeaBand? = if(!raw.isFinite()) null else when(tile) {
    MarineTile.Temperature->seaTemperatureBands()[when {raw<10->0;raw<18->1;raw<24->2;raw<28->3;else->4}]
    MarineTile.Waves->waveHeightBands()[when {raw<0.5->0;raw<1.25->1;raw<2.5->2;raw<4->3;else->4}]
    MarineTile.Period->wavePeriodBands()[when {raw<6->0;raw<10->1;else->2}]
    MarineTile.Level->if(!next.isFinite()) null else seaLevelBands()[when {next-raw>.01->0;raw-next>.01->2;else->1}]
}
private fun seaTemperatureColor(c:Double)=when {!c.isFinite()->SeaScaleGrey;c<10->Color(0xFF4FC3F7);c<18->Color(0xFF4D8D7C);c<24->Color(0xFF66D17A);c<28->Color(0xFFFFB74D);else->Color(0xFFFF6B6B)}
private fun waveHeightColor(m:Double)=when {!m.isFinite()->SeaScaleGrey;m<0.5->Color(0xFF66D17A);m<1.25->Color(0xFFB5D46A);m<2.5->Color(0xFFFFD166);m<4->Color(0xFFFB8C00);else->Color(0xFFE53935)}
private fun wavePeriodColor(s:Double)=when {!s.isFinite()->SeaScaleGrey;s<6->Color(0xFF8FD3F4);s<10->Color(0xFF4FC3F7);else->Color(0xFF7C6FCD)}
internal class OverviewCell(val key:String,val content:@Composable (Modifier)->Unit)
@Composable internal fun TileRows(cells:List<OverviewCell>) {
    val order=rememberOverviewTileOrder()
    cells.sortedBy {overviewTileRank(order,it.key)}.chunked(2).forEach {pair->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            pair.forEach {cell->key(cell.key) {cell.content(Modifier.weight(1f))}}
            if(pair.size==1) Spacer(Modifier.weight(1f))
        }
    }
}
private enum class MarineTile(val key:String,val label:String,val color:Color) {
    Temperature("sea_surface_temperature",uiText(R.string.ui_teplota_mora_0e2349bf),Color(0xFFFFB74D)),
    Level("sea_level_height_msl",uiText(R.string.ui_hladina_mora_a4623230),Color(0xFFBA9EDB)),
    Waves("wave_height",uiText(R.string.ui_vyska_vln_4b75e096),Color(0xFF4FC3F7)),
    Period("wave_period",uiText(R.string.ui_perioda_vln_47347236),Color(0xFF66D17A))
}
private data class MarineTilePreferences(val enabled:Boolean,val selected:Set<String>)
private fun marineTileSelection(p:SharedPreferences)=p.getStringSet(MarineTilesKey,null)?.toSet() ?: MarineTile.entries.map {it.key}.toSet()
@Composable private fun rememberMarineTilePreferences():MarineTilePreferences {
    val context=LocalContext.current
    val marine=remember(context) {context.getSharedPreferences("perun_marine",Context.MODE_PRIVATE)}
    val forecast=remember(context) {context.getSharedPreferences("perun_forecast",Context.MODE_PRIVATE)}
    fun read()=MarineTilePreferences(marine.getBoolean("enabled",false),marineTileSelection(forecast))
    var state by remember(marine,forecast) {mutableStateOf(read())}
    DisposableEffect(marine,forecast) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->state=read()}
        marine.registerOnSharedPreferenceChangeListener(listener);forecast.registerOnSharedPreferenceChangeListener(listener);state=read()
        onDispose {marine.unregisterOnSharedPreferenceChangeListener(listener);forecast.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return state
}
/** Sea data for the Now screen; pending is true until the first load for the location has finished. */
internal class MarineOverviewState(val data:MarineData?,val pending:Boolean)
@Composable internal fun rememberMarineOverviewState(location:Location,refreshKey:Int):MarineOverviewState {
    val prefs=rememberMarineTilePreferences()
    val wanted=prefs.enabled && MarineTile.entries.any {it.key in prefs.selected}
    var data by remember(location) {mutableStateOf<MarineData?>(null)}
    var pending by remember(location) {mutableStateOf(true)}
    LaunchedEffect(location,wanted,refreshKey) {
        // A refresh keeps the tiles that are shown until the new data arrive.
        if(!wanted) {data=null;pending=false;return@LaunchedEffect}
        while(true) {
            data=try {loadMarine(location,2).takeIf {it.relevant}} catch(e:CancellationException) {throw e} catch(e:Exception) {null}
            pending=false
            delay(600_000)
        }
    }
    return MarineOverviewState(if(wanted) data else null,wanted && pending)
}
@Composable internal fun rememberMarineOverview(location:Location,refreshKey:Int):MarineData? = rememberMarineOverviewState(location,refreshKey).data
@Composable internal fun rememberMarineCells(data:MarineData?,u:WeatherUnits,onHelp:(HelpSpec)->Unit):List<OverviewCell> {
    val prefs=rememberMarineTilePreferences()
    val d=data ?: return emptyList()
    if(!prefs.enabled || !d.relevant) return emptyList()
    var now by remember {mutableLongStateOf(Instant.now().epochSecond)}
    LaunchedEffect(Unit) {while(true) {now=Instant.now().epochSecond;delay(30_000)}}
    val i=d.times.indexOfLast {it<=now}
    if(i<0 || now-d.times[i]>=3600) return emptyList()
    val nav=LocalOverviewNavigation.current
    return MarineTile.entries.filter {it.key in prefs.selected}.map {tile->
        run {
            val raw=d.at(tile.key,i)
            val value=if(tile==MarineTile.Temperature) u.temperature.fromCelsius(raw) else raw
            val unit=when(tile) {MarineTile.Temperature->u.temperature.symbol;MarineTile.Period->"s";else->"m"}
            val next=if(d.times.getOrNull(i+1)?.minus(d.times[i])==3600L) d.at(tile.key,i+1) else Double.NaN
            val trend=if(!raw.isFinite() || !next.isFinite()) uiText(R.string.ui_trend_nedostupny_87cb2581) else when {next-raw>.01->uiText(R.string.ui_stupa_c05574b4);raw-next>.01->uiText(R.string.ui_klesa_54707dc3);else->uiText(R.string.ui_stabilna_be3f811f)}
            val levelTone=when(tile) {
                MarineTile.Level->if(raw.isFinite() && next.isFinite()) when {next-raw>.01->SeaRise;raw-next>.01->SeaFall;else->tile.color} else tile.color
                MarineTile.Temperature->seaTemperatureColor(raw)
                MarineTile.Waves->waveHeightColor(raw)
                MarineTile.Period->wavePeriodColor(raw)
            }
            val bearing=marineTravelBearing(d.at("wave_direction",i))
            val date=Instant.ofEpochSecond(now).atZone(d.zone).toLocalDate()
            val dayValues=if(tile==MarineTile.Temperature) d.times.indices.filter {Instant.ofEpochSecond(d.times[it]).atZone(d.zone).toLocalDate()==date}.map {u.temperature.fromCelsius(d.at(tile.key,it))}.filter {it.isFinite()} else emptyList()
            val explain=when(tile) {
                MarineTile.Temperature->uiText(R.string.ui_modelova_teplota_povrchovej_vrstvy_mora_7d6ec8aa)
                MarineTile.Level->uiText(R.string.ui_modelova_hladina_voci_strednej_hladine_mora_msl_21f4a62d)
                MarineTile.Waves->uiText(R.string.ui_vyznamna_vyska_vln_sipka_ukazuje_smer_sirenia_sever_je_hor_07286d2b)
                MarineTile.Period->uiText(R.string.ui_perioda_vln_interval_medzi_vlnami_v_sekundach_5a86050e)
            }
            OverviewCell("marine:${tile.key}") {modifier->TkTile(tile.label,if(raw.isFinite()) levelTone else Color(0xFF5B6577),false,seaBandFor(tile,raw,next)?.label ?: uiText(R.string.ui_udaj_nedostupny_8bbc9c94),modifier,{
                onHelp(HelpSpec(tile.label) {
                    HelpText(explain)
                    val bands=when(tile) {MarineTile.Temperature->seaTemperatureBands();MarineTile.Level->seaLevelBands();MarineTile.Waves->waveHeightBands();MarineTile.Period->wavePeriodBands()}
                    bands.forEach {HelpRow(it.color,it.label,it.range)}
                })
            },{nav.openMarine(tile.name)}) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)) {Text(weatherUnitNumber(value,1),color=Color(0xFFE8EAF0),fontSize=24.sp,fontWeight=FontWeight.SemiBold,maxLines=1);Text(unit,color=ForecastMuted,fontSize=13.sp,maxLines=1)}
                        val sub=if(!raw.isFinite()) uiText(R.string.ui_udaj_nedostupny_8bbc9c94) else when(tile) {
                            MarineTile.Temperature->if(dayValues.isEmpty()) "" else weatherUnitNumber(dayValues.minOrNull()!!,1)+"–"+weatherUnitNumber(dayValues.maxOrNull()!!,1)+uiText(R.string.ui_dnes_b6dcb499)
                            MarineTile.Level->uiText(R.string.ui_msl_be8d7660)+trend
                            MarineTile.Waves->if(bearing.isFinite()) uiText(R.string.ui_smer_sirenia_e0efaa7c)+cardinal(bearing) else uiText(R.string.ui_smer_nedostupny_f96aaf53)
                            MarineTile.Period->uiText(R.string.ui_modelova_hodina_97a95bf3)
                        }
                        Text(sub,color=ForecastMuted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Box(Modifier.padding(bottom=3.dp)) {
                        when(tile) {
                            MarineTile.Temperature->Column(horizontalAlignment=Alignment.CenterHorizontally) {SummaryGlyph(SummaryIcon.Temp,levelTone,22.dp);GlyphIcon(Glyph.Wave,levelTone,22.dp)}
                            MarineTile.Level->Column(horizontalAlignment=Alignment.CenterHorizontally) {if(next.isFinite() && raw.isFinite() && abs(next-raw)>.01) TkRiseArrow(next>raw,levelTone);GlyphIcon(Glyph.Wave,levelTone,26.dp)}
                            MarineTile.Waves->if(bearing.isFinite()) {val box=rememberTileTextBox();Box(Modifier.padding(bottom=(box.descent-3.dp).coerceAtLeast(0.dp))) {TkDirectionArrow(d.at("wave_direction",i),levelTone,box.height,uiText(R.string.ui_smer_sirenia_e0efaa7c)+cardinal(bearing))}} else GlyphIcon(Glyph.Wave,levelTone,40.dp)
                            MarineTile.Period->GlyphIcon(Glyph.SeaPeriod,levelTone,40.dp)
                        }
                    }
                }
            }}
        }
    }
}
