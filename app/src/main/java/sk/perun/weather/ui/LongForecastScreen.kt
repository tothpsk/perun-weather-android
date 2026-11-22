package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val LongText=Color(0xFFE8EAF0)
private val LongEdge=Color(0xFF4B586C)
private val LongPanel=Color(0xFF202833)
internal enum class LongMetric(val label:String,val key:String,val axis:String) {
    Temp(uiText(R.string.ui_teplota_4b720579),"temperature_2m","temp"),Feels(uiText(R.string.ui_pocitova_acd4f8bb),"apparent_temperature","temp"),
    Rain(uiText(R.string.ui_zrazky_66d17319),"precipitation","rain"),Prob(uiText(R.string.ui_pravdepodobnost_bc2683dc),"precipitation_probability","percent"),
    Wind(uiText(R.string.ui_vietor_8a3ebf26),"wind_speed_10m","wind"),Gust(uiText(R.string.ui_narazy_b151d6fb),"wind_gusts_10m","wind"),
    Cloud(uiText(R.string.ui_oblacnost_b5a6e70c),"cloud_cover","percent"),Uv(uiText(R.string.ui_uv_027fcbbc),"uv_index","uv"),
    Humidity(uiText(R.string.ui_vlhkost_e06c751e),"relative_humidity_2m","percent"),Dew(uiText(R.string.ui_rosny_bod_6caea4c6),"dew_point_2m","temp"),
    Visibility(uiText(R.string.ui_viditelnost_7f90db41),"visibility","visibility"),Pressure(uiText(R.string.ui_tlak_vzduchu_cc4c1846),"pressure_msl","pressure")
}
private val LongModes=listOf(LongMetric.Temp,LongMetric.Rain,LongMetric.Wind,LongMetric.Cloud,LongMetric.Uv,LongMetric.Humidity,LongMetric.Visibility,LongMetric.Pressure)
// Any variable the selected model leaves empty is completed from best_match; the chart title then says "· Best match".
private val LongFallbackKeys=LongMetric.entries.map {it.key}
private fun longSeries(m:LongMetric)=when(m) {
    LongMetric.Temp->listOf(m,LongMetric.Feels);LongMetric.Rain->listOf(m,LongMetric.Prob);LongMetric.Wind->listOf(m,LongMetric.Gust)
    LongMetric.Humidity->listOf(m,LongMetric.Dew);else->listOf(m)
}
private fun longUnit(m:LongMetric,u:WeatherUnits)=when(m.axis) { "temp"->u.temperature.symbol;"rain"->u.precipitation.liquidSymbol;"wind"->u.wind.symbol;"percent"->"%";"visibility"->"km";"pressure"->u.pressure.symbol;else->uiText(R.string.ui_uv_027fcbbc) }
private fun longValue(m:LongMetric,v:Double,u:WeatherUnits)=when(m.axis) { "temp"->u.temperature.fromCelsius(v);"rain"->u.precipitation.fromMillimetres(v);"wind"->u.wind.fromKmh(v);"visibility"->v/1000;"pressure"->u.pressure.fromHpa(v);else->v }
private fun longColor(m:LongMetric,v:Double=0.0)=when(m) {
    LongMetric.Temp->Color(0xFFEF5350);LongMetric.Feels->Color(0xFFFF8A80)
    LongMetric.Rain,LongMetric.Humidity->RainBlue;LongMetric.Prob->Color(0xFF9EA7B5)
    LongMetric.Wind->Color(0xFF66D17A);LongMetric.Gust->Color(0xFF268341)
    LongMetric.Dew->Color(0xFFB8D957);LongMetric.Uv->uvColor(v)
    LongMetric.Visibility->Color(0xFFFF9800);LongMetric.Pressure->Color(0xFFBA9EDB);else->Color(0xFF8294AD)
}
private fun longTip(m:LongMetric)=when(m) {
    LongMetric.Temp->uiText(R.string.ui_teplota_vzduchu_vo_vyske_2_m_0bd0b14b)
    LongMetric.Feels->uiText(R.string.ui_pocitova_teplota_vietor_vlhkost_ziarenie_0cf6ca0d)
    LongMetric.Rain->uiText(R.string.ui_uhrn_zrazok_za_hodinu_vo_vodnom_ekvivalente_2eed557b)
    LongMetric.Prob->uiText(R.string.ui_pravdepodobnost_zrazok_dc4a8c6f)
    LongMetric.Wind->uiText(R.string.ui_vietor_vo_vyske_10_m_ab4342ae)
    LongMetric.Gust->uiText(R.string.ui_narazy_vetra_31d92aae)
    LongMetric.Cloud->uiText(R.string.ui_celkova_oblacnost_v_e6d4c200)
    LongMetric.Uv->uiText(R.string.ui_uv_index_podla_stupnice_who_20ec7a75)
    LongMetric.Humidity->uiText(R.string.ui_relativna_vlhkost_vzduchu_7dfed857)
    LongMetric.Dew->uiText(R.string.ui_rosny_bod_teplota_pri_ktorej_sa_vzduch_nasyti_b115885f)
    LongMetric.Visibility->uiText(R.string.ui_horizontalna_viditelnost_v_km_37878c66)
    LongMetric.Pressure->uiText(R.string.ui_tlak_vzduchu_prepocitany_na_hladinu_mora_80fcc497)
}
private val LongTipPosition=object:PopupPositionProvider {
    override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
        val x=(anchorBounds.right-popupContentSize.width).coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0))
        val y=(anchorBounds.top-popupContentSize.height-6).coerceAtLeast(0)
        return IntOffset(x,y)
    }
}
@Composable private fun LongMetricHelp(m:LongMetric) {
    when(m) {
        LongMetric.Temp->HelpText(uiText(R.string.ui_teplota_vzduchu_vo_vyske_2_m_z_hodinovej_predpovede_modelu_77ed797a))
        LongMetric.Feels->HelpText(uiText(R.string.ui_pocitova_teplota_zohladnuje_vietor_vlhkost_a_slnecne_ziare_1f74486b))
        LongMetric.Rain->{
            HelpText(uiText(R.string.ui_stlpceky_ukazuju_uhrn_zrazok_za_predchadzajucu_hodinu_vo_v_f54831bd))
            HelpRow(RainBlue,uiText(R.string.ui_dazd_a_prehanky_9e6eff58),uiText(R.string.ui_kvapalne_zrazky_0c457a52))
            HelpRow(Color.White,uiText(R.string.ui_sneh_27400f08),uiText(R.string.ui_uhrn_je_vo_vodnom_ekvivalente_nie_vyska_snehu_f9add673))
            HelpRow(Color(0xFFBA68C8),uiText(R.string.ui_dazd_so_snehom_586507c0),uiText(R.string.ui_zmiesane_zrazky_0c420ad6))
        }
        LongMetric.Prob->HelpText(uiText(R.string.ui_prerusovana_ciara_ukazuje_pravdepodobnost_zrazok_v_danej_h_5ac15e46))
        LongMetric.Wind->HelpText(uiText(R.string.ui_vietor_vo_vyske_10_m_sipky_nad_grafom_ukazuju_kam_vietor_f_c7fac450))
        LongMetric.Gust->HelpText(uiText(R.string.ui_narazy_su_kratkodobe_maxima_rychlosti_vetra_pri_silnom_vet_fd7f47bd))
        LongMetric.Cloud->HelpText(uiText(R.string.ui_celkova_modelova_oblacnost_v_percentach_0_je_jasno_100_zam_fe810d8f))
        LongMetric.Uv->{
            HelpText(uiText(R.string.ui_modelovy_uv_index_podla_stupnice_who_v_noci_je_nulovy_ff39b5cb))
            HelpRow(uvColor(1.0),uiText(R.string.ui_nizka_02_93a5db81),uiText(R.string.ui_ochrana_zvycajne_nie_je_potrebna_87975225))
            HelpRow(uvColor(4.0),uiText(R.string.ui_mierna_35_62495930),uiText(R.string.ui_v_poludnie_hladaj_tien_0209161d))
            HelpRow(uvColor(6.0),uiText(R.string.ui_vysoka_67_61c69e71),uiText(R.string.ui_pouzi_spf_30_a_viac_32d74f96))
            HelpRow(uvColor(8.0),uiText(R.string.ui_velmi_vysoka_810_4fcf7a45),uiText(R.string.ui_v_poludnie_sa_slnku_vyhybaj_19ae3bee))
            HelpRow(uvColor(11.0),uiText(R.string.ui_extremna_11_a_viac_bed08146),uiText(R.string.ui_dlhy_pobyt_vonku_sa_neodporuca_4c84d339))
        }
        LongMetric.Humidity->HelpText(uiText(R.string.ui_relativna_vlhkost_vzduchu_v_pod_30_je_vzduch_suchy_525c9825))
        LongMetric.Dew->HelpText(uiText(R.string.ui_rosny_bod_je_teplota_pri_ktorej_by_sa_vzduch_nasytil_vodno_ad206731))
        LongMetric.Visibility->HelpText(uiText(R.string.ui_modelova_horizontalna_viditelnost_v_km_d7a2ed32))
        LongMetric.Pressure->HelpText(uiText(R.string.ui_tlak_vzduchu_prepocitany_na_hladinu_mora_rychly_pokles_tla_9b07cb62))
    }
}
private data class LongData(val times:List<Long>,val values:Map<String,List<Double>>,val zone:ZoneId,val sources:Map<String,String> = emptyMap()) {
    fun value(key:String,i:Int)=values[key]?.getOrNull(i) ?: Double.NaN
}
private fun longBase(l:Location)="latitude=${l.latitude}&longitude=${l.longitude}&timezone="+URLEncoder.encode(l.timezone.ifBlank {"auto"},"UTF-8")+"&timeformat=unixtime&forecast_days=16&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"+(l.elevation?.let {"&elevation=$it"} ?: "")
private suspend fun longLoad(l:Location,model:String):LongData=withContext(Dispatchers.IO) {
    val keys=LongMetric.entries.map {it.key}+listOf("rain","showers","snowfall","wind_direction_10m","is_day")
    val base=longBase(l)
    suspend fun request(modelId:String,vars:List<String>)=JSONObject(String(fetchBytes("https://api.open-meteo.com/v1/forecast?$base"+(if(modelId=="best_match") "" else "&models="+URLEncoder.encode(modelId,"UTF-8"))+"&hourly="+vars.joinToString(",")),Charsets.UTF_8))
    val root=request(model,keys)
    val h=root.getJSONObject("hourly");val t=h.getJSONArray("time");val times=List(t.length()) {t.getLong(it)}
    require(times.isNotEmpty() && times.zipWithNext().all {(a,b)->b>a})
    val values=keys.associateWith {key->val a=h.optJSONArray(key);MutableList(times.size) {i->if(a==null || a.isNull(i)) Double.NaN else a.optDouble(i,Double.NaN)}}
    val last=values.getValue("temperature_2m").indexOfLast {it.isFinite()};require(last>=0) {uiText(R.string.ui_model_nevratil_predpoved_pre_lokalitu_07d7763e)}
    val sources=mutableMapOf<String,String>()
    val gaps=if(model=="best_match") emptyList() else LongFallbackKeys.filter {key->val column=values.getValue(key);(0..last).any {!column[it].isFinite()}}
    if(gaps.isNotEmpty()) try {
        val fh=request("best_match",gaps).getJSONObject("hourly");val ft=fh.getJSONArray("time")
        val index=HashMap<Long,Int>();for(i in 0 until ft.length()) index[ft.getLong(i)]=i
        for(key in gaps) {
            val fa=fh.optJSONArray(key) ?: continue;val column=values.getValue(key);var filled=0
            for(i in 0..last) if(!column[i].isFinite()) {val j=index[times[i]] ?: continue;if(!fa.isNull(j)) {val v=fa.optDouble(j,Double.NaN);if(v.isFinite()) {column[i]=v;filled++}}}
            if(filled>0) sources[key]="Best match"
        }
    } catch(e:CancellationException) {throw e} catch(e:Exception) {}
    LongData(times.take(last+1),values.mapValues {it.value.take(last+1)},ZoneId.of(root.getString("timezone")),sources)
}
@Composable private fun LongModeIcon(m:LongMetric,color:Color) {
    when(m) {
        LongMetric.Temp->SummaryGlyph(SummaryIcon.Temp,color,22.dp)
        LongMetric.Rain->SummaryGlyph(SummaryIcon.Rain,color,22.dp)
        LongMetric.Wind->SummaryGlyph(SummaryIcon.Wind,color,22.dp)
        LongMetric.Uv->SummaryGlyph(SummaryIcon.Uv,color,22.dp)
        LongMetric.Humidity->HumidityGlyph(color,22.dp)
        LongMetric.Cloud->Canvas(Modifier.size(24.dp)) {val k=size.width/24f;scale(k,k,Offset.Zero) {val p=Path().apply {moveTo(5f,19f);cubicTo(0f,19f,0f,11f,5f,11f);cubicTo(5f,3f,16f,3f,17f,10f);cubicTo(24f,9f,25f,19f,19f,19f);close()};drawPath(p,color,style=Stroke(1.8f,join=StrokeJoin.Round))}}
        LongMetric.Visibility,LongMetric.Pressure->Canvas(Modifier.size(22.dp)) {val k=size.width/24f;scale(k,k,Offset.Zero) {val s=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            if(m==LongMetric.Visibility) {val p=Path().apply {moveTo(2f,12f);cubicTo(7f,2f,17f,2f,22f,12f);cubicTo(17f,22f,7f,22f,2f,12f);close()};drawPath(p,color,style=s);drawCircle(color,3.5f,Offset(12f,12f))}
            else {drawCircle(color,10f,Offset(12f,12f),style=s);drawLine(color,Offset(6f,12f),Offset(18f,12f),1.8f,StrokeCap.Round);drawLine(color,Offset(14f,8f),Offset(18f,12f),1.8f,StrokeCap.Round);drawLine(color,Offset(14f,16f),Offset(18f,12f),1.8f,StrokeCap.Round)}
        }}
        else->Unit
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun LongForecastScreen(location:Location) {
    val prefs=rememberForecastPreferences();val envelope=rememberLongEnvelopePreferences();val customGraphs=rememberLongCustomGraphs()
    val context=LocalContext.current;val store=remember(context) {context.getSharedPreferences("perun_long_forecast",Context.MODE_PRIVATE)}
    var enabled by remember(store) {mutableStateOf((store.getStringSet("enabledModes",null) ?: setOf("Temp","Rain","Wind")).mapNotNull {name->LongModes.firstOrNull {it.name==name}}.toSet())}
    var showBand by remember(store) {mutableStateOf(store.getBoolean("showEnvelope",true))}
    var data by remember(location,prefs.model) {mutableStateOf<LongData?>(null)}
    var ens by remember(location,prefs.model,prefs.ensembleModel,envelope,showBand) {mutableStateOf(LocationEnsembleData(note=if(showBand) uiText(R.string.ui_ensemble_sa_nacitava_abfb276f) else uiText(R.string.ui_obalka_je_vypnuta_4765cc9d)))}
    var error by remember(location,prefs.model) {mutableStateOf<String?>(null)};var loading by remember(location,prefs.model) {mutableStateOf(true)};var reload by remember {mutableIntStateOf(0)}
    var shift by remember(location,prefs.model) {mutableDoubleStateOf(0.0)};var cursor by remember(location,prefs.model) {mutableStateOf<Long?>(null)}
    var now by remember {mutableLongStateOf(Instant.now().epochSecond)}
    LaunchedEffect(Unit) {while(true) {now=Instant.now().epochSecond;delay(30_000)}}
    LaunchedEffect(location,prefs.model,reload) {
        loading=true;error=null
        try {data=longLoad(location,prefs.model)}
        catch(e:CancellationException) {throw e} catch(e:Exception) {error=e.message ?: uiText(R.string.ui_chyba_nacitania_62fc4ef6)} finally {loading=false}
    }
    LaunchedEffect(location,prefs.model,prefs.ensembleModel,reload,envelope,showBand) {
        ens=LocationEnsembleData(note=if(showBand) uiText(R.string.ui_ensemble_sa_nacitava_abfb276f) else uiText(R.string.ui_obalka_je_vypnuta_4765cc9d))
        if(showBand) {
            try {ens=loadLongEnsemble(location,prefs.model,prefs.ensembleModel,envelope,refreshKey=reload)}
            catch(e:CancellationException) {throw e} catch(e:Exception) {ens=LocationEnsembleData(note=uiText(R.string.ui_ensemble_sa_nepodarilo_nacitat_value_hlavna_predpoved_zost_091af7e1, e.message ?: uiText(R.string.ui_chyba_b9f17311)))}
        }
    }
    PullToRefreshBox(isRefreshing=loading,onRefresh={if(!loading) {NetCache.invalidate();reload++}},modifier=Modifier.fillMaxSize().background(Color(0xFF323E4F))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                LongModes.forEach {m->val active=m in enabled;val shape=RoundedCornerShape(50)
                    Box(Modifier.size(48.dp).clip(shape).background(if(active) RainBlue else LongPanel).border(1.dp,if(active) RainBlue else LongEdge,shape).toggleable(value=active,role=Role.Checkbox,onValueChange={checked->enabled=if(checked) enabled+m else enabled-m;store.edit().putStringSet("enabledModes",enabled.map {it.name}.toSet()).apply()}).semantics {contentDescription=m.label},contentAlignment=Alignment.Center) {LongModeIcon(m,if(active) Color(0xFF101F39) else LongText)}
                }
                LongCustomControls(customGraphs)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(uiText(R.string.ui_obalka_3db174bd),Modifier.weight(1f),color=LongText,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                PerunSegments(listOf(uiText(R.string.ui_vypnute_6eaf04b7),uiText(R.string.ui_zapnute_4b796e4a)),if(showBand) 1 else 0,{showBand=it==1;store.edit().putBoolean("showEnvelope",showBand).apply()},compact=true)
                ChartNowButton(enabled=shift>0.0) {shift=0.0;cursor=null}
            }
            error?.let {Text(it+uiText(R.string.ui_potiahni_nadol_pre_obnovenie_971de0eb),color=Color(0xFFFFB74D))}
            data?.let {d->val span=5L*86400;val start=d.times.first()+shift.toLong()
                val move:(Double)->Unit={seconds->shift=(shift+seconds).coerceIn(0.0,(d.times.last()+3600-d.times.first()-span).coerceAtLeast(0).toDouble())}
                LongModes.filter {it in enabled}.forEach {m->key(m) {LongChart(d,longSeries(m),m.label,prefs.units,start,span,now,cursor,{cursor=it},move,ens,showBand)}}
                customGraphs.filter {it.visible}.forEach {g->key(g.id) {LongChart(d,g.metrics,g.title,prefs.units,start,span,now,cursor,{cursor=it},move,ens,showBand)}}
            }
            if(enabled.isEmpty() && customGraphs.none {it.visible}) Text(uiText(R.string.ui_vyber_aspon_jeden_graf_b3265ee1),color=LongText,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            AttributionFooter()
        }
    }
}
@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable private fun LongChart(d:LongData,metrics:List<LongMetric>,title:String,u:WeatherUnits,start:Long,span:Long,now:Long,cursor:Long?,select:(Long?)->Unit,shift:(Double)->Unit,ens:LocationEnsembleData,showBand:Boolean) {
    val tm=rememberTextMeasurer()
    val sourceSuffix=metrics.filter {it.key in d.sources}.joinToString(", ") {(if(metrics.size==1) "" else it.label+": ")+d.sources.getValue(it.key)}
    val fullTitle=if(sourceSuffix.isEmpty()) title else "$title · $sourceSuffix"
    var hidden by remember {mutableStateOf(setOf<LongMetric>())}
    val shown=metrics.filter {it !in hidden}.ifEmpty {metrics}
    val axes=shown.distinctBy {it.axis};val leftMargin=if(axes.size>=3) 76.dp else 38.dp;val rightMargin=if(axes.size>=4) 76.dp else 38.dp
    var canvasWidth by remember {mutableFloatStateOf(800f)};val density=LocalDensity.current
    val margins=with(density) {(leftMargin+rightMargin).toPx()};val plotPixels=(canvasWidth-margins).coerceAtLeast(1f)
    val move by rememberUpdatedState(shift);val currentStart by rememberUpdatedState(start)
    val scroll=rememberScrollableState {delta->move(-delta.toDouble()*span/plotPixels);delta}
    var tip by remember {mutableStateOf(false)};var help by remember {mutableStateOf(false)}
    val indices=d.times.indices.filter {d.times[it] in start..start+span}
    // With the envelope on, the line of the temperature is the median of the ensemble (P50); hours that the ensemble does not cover keep the value of the model.
    val median=remember(ens,showBand) {if(showBand) ens.bands[LongMetric.Temp.key+LongMedianSuffix].orEmpty().associate {it.first to it.second.lower} else emptyMap()}
    fun lineRaw(m:LongMetric,i:Int):Double = (if(m==LongMetric.Temp) median[d.times[i]] else null) ?: d.value(m.key,i)
    val selected=cursor?.let {t->d.times.indices.minByOrNull {abs(d.times[it]-t)}}
    val scales=axes.associate {axis->val ms=shown.filter {it.axis==axis.axis}
        val values=ms.flatMap {m->indices.map {longValue(m,d.value(m.key,it),u)}+if(showBand) ens.bands[m.key].orEmpty().filter {it.first in start..start+span}.flatMap {(_,b)->listOf(longValue(m,b.lower,u),longValue(m,b.upper,u))} else emptyList()}.filter {it.isFinite()}
        var lo=if(axis.axis in listOf("percent","rain","wind","uv","visibility")) 0.0 else values.minOrNull() ?: 0.0
        var hi=if(axis.axis=="percent") 100.0 else values.maxOrNull() ?: lo+1
        if(hi<=lo) hi=lo+1
        if(axis.axis !in listOf("percent","rain","wind","uv","visibility")) {val p=(hi-lo)*.1;lo-=p;hi+=p}
        axis.axis to (lo to hi)
    }
    Surface(color=LongPanel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,LongEdge)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=28.dp).scrollable(scroll,Orientation.Horizontal),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(fullTitle,Modifier.weight(1f),color=LongText,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(metrics.size>1) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    metrics.forEach {m->val on=m in shown
                        Row(Modifier.heightIn(min=28.dp).alpha(if(on) 1f else .4f).clickable(role=Role.Checkbox,onClickLabel=uiText(R.string.ui_prepnut_8ce4129e)+m.label,onClick={hidden=if(m in hidden) hidden-m else if(shown.size>1) hidden+m else hidden}),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Canvas(Modifier.size(12.dp,8.dp)) {drawLine(longColor(m,3.0),Offset(0f,size.height/2),Offset(size.width,size.height/2),2.dp.toPx(),pathEffect=if(m==LongMetric.Prob) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx())) else null)}
                            Text(m.label,fontSize=11.sp,color=longColor(m,3.0),maxLines=1,softWrap=false)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(234.dp).onSizeChanged {canvasWidth=it.width.toFloat()}.semantics {contentDescription=fullTitle+uiText(R.string.ui_posun_cez_okraj_dlazdice_odcitanie_dotykom_v_grafe_1e76db34)}.pointerInput(d,span,leftMargin,rightMargin) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false);val left=leftMargin.toPx();val right=(size.width-rightMargin.toPx()).coerceAtLeast(left+1);val top=48.dp.toPx();val bottom=size.height-18.dp.toPx();val w=right-left
                    if(down.position.x in left..right && down.position.y in top..bottom) {
                        fun choose(px:Float) {select(currentStart+(((px-left)/w).coerceIn(0f,1f)*span).toLong())}
                        down.consume();choose(down.position.x)
                        try {do {val event=awaitPointerEvent();val c=event.changes.firstOrNull {it.id==down.id} ?: break;if(!c.pressed) break;c.consume();choose(c.position.x)} while(true)} finally {select(null)}
                    } else {
                        var previous=down.position.x
                        val drag=awaitHorizontalTouchSlopOrCancellation(down.id) {c,over->move(-over.toDouble()*span/w);previous=c.position.x;c.consume()}
                        if(drag!=null) horizontalDrag(drag.id) {c->move(-(c.position.x-previous).toDouble()*span/w);previous=c.position.x;c.consume()}
                    }
                }
            }) {
                val left=leftMargin.toPx();val right=(size.width-rightMargin.toPx()).coerceAtLeast(left+1);val width=right-left;val top=48.dp.toPx();val bottom=size.height-18.dp.toPx()
                fun x(t:Long)=left+((t-start).toDouble()/span*width).toFloat()
                fun y(m:LongMetric,v:Double):Float {val (lo,hi)=scales.getValue(m.axis);return bottom-((v-lo)/(hi-lo)*(bottom-top)).toFloat()}
                fun text(s:String,xx:Float,yy:Float,c:Color=ForecastMuted) {val r=tm.measure(s,TextStyle(color=c,fontSize=9.sp));drawText(r,topLeft=Offset(xx.coerceIn(0f,(size.width-r.size.width).coerceAtLeast(0f)),yy))}
                clipRect(left,top,right,bottom) {
                    indices.forEach {i->if(d.value("is_day",i)==1.0) {val a=x(d.times[i]);val b=x(d.times[i]+3600);drawRect(Color(0x0CFFFFFF),Offset(a,top),Size((b-a).coerceAtLeast(0f),bottom-top))}}
                    for(k in 0..4) {val yy=top+(bottom-top)*k/4;drawLine(LongEdge.copy(alpha=.5f),Offset(left,yy),Offset(right,yy),.5.dp.toPx())}
                    if(showBand) shown.forEach {m->val points=ens.bands[m.key].orEmpty()
                        points.zipWithNext().forEach {(a,b)->if(b.first>=start && a.first<=start+span && b.first-a.first in 1..3600) {
                            val p=Path().apply {moveTo(x(a.first),y(m,longValue(m,a.second.lower,u)));lineTo(x(b.first),y(m,longValue(m,b.second.lower,u)));lineTo(x(b.first),y(m,longValue(m,b.second.upper,u)));lineTo(x(a.first),y(m,longValue(m,a.second.upper,u)));close()}
                            drawPath(p,longColor(m,3.0).copy(alpha=.23f))
                        }}
                    }
                    shown.forEach {m->
                        if(m==LongMetric.Rain || m==LongMetric.Uv) indices.forEach {i->val raw=d.value(m.key,i);if(raw.isFinite() && raw>0) {
                            val a=x(d.times[i]-3600);val b=x(d.times[i]);val yy=y(m,longValue(m,raw,u));val c=if(m==LongMetric.Uv) uvColor(raw) else {
                                val snow=d.value("snowfall",i)>0;val rain=d.value("rain",i)>0 || d.value("showers",i)>0
                                if(snow && rain) precipitationType(1.0,0.0,1.0) ?: Color(0xFFBA68C8) else if(snow) Color.White else RainBlue
                            }
                            drawRect(c,Offset(a+.25.dp.toPx(),yy),Size((b-a-.5.dp.toPx()).coerceAtLeast(.5.dp.toPx()),(bottom-yy).coerceAtLeast(0f)))
                        }} else {
                            val path=Path();val seg=ArrayList<Offset>();var lastTime=0L
                            fun flush() {
                                if(seg.size>=2) {
                                    val ys=FloatArray(seg.size) {seg[it].y}
                                    repeat(2) {val c=ys.copyOf();for(k in 1 until ys.size-1) ys[k]=.25f*c[k-1]+.5f*c[k]+.25f*c[k+1]}
                                    path.moveTo(seg[0].x,ys[0])
                                    for(k in 0 until seg.size-1) {
                                        val x1=seg[k].x;val x2=seg[k+1].x;val y1=ys[k];val y2=ys[k+1];val y0=ys[max(k-1,0)];val y3=ys[min(k+2,seg.size-1)]
                                        val lowY=min(y1,y2);val highY=max(y1,y2)
                                        path.cubicTo(x1+(x2-x1)/3f,(y1+(y2-y0)/6f).coerceIn(lowY,highY),x2-(x2-x1)/3f,(y2-(y3-y1)/6f).coerceIn(lowY,highY),x2,y2)
                                    }
                                }
                                seg.clear()
                            }
                            d.times.indices.forEach {i->val value=longValue(m,lineRaw(m,i),u)
                                if(!value.isFinite()) flush() else {
                                    if(seg.isNotEmpty() && d.times[i]-lastTime!=3600L) flush()
                                    seg.add(Offset(x(d.times[i]),y(m,value)));lastTime=d.times[i]
                                }
                            }
                            flush()
                            drawPath(path,longColor(m),style=Stroke(1.8.dp.toPx(),pathEffect=if(m==LongMetric.Prob) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),3.dp.toPx())) else null))
                        }
                    }
                }
                axes.forEachIndexed {a,m->val xx=if(a%2==0) left-38.dp.toPx()*(a/2+1) else right+3.dp.toPx()+38.dp.toPx()*(a/2);val c=longColor(m,3.0);text(longUnit(m,u),xx,top-20.dp.toPx(),c)
                    val ax=if(a%2==0) left-38.dp.toPx()*(a/2) else right+38.dp.toPx()*(a/2);drawLine(c.copy(alpha=.7f),Offset(ax,top),Offset(ax,bottom),.7.dp.toPx())
                    for(k in 0..4) {val (lo,hi)=scales.getValue(m.axis);text(weatherUnitNumber(hi-(hi-lo)*k/4,if(m.axis=="rain" || m.axis=="pressure") 1 else 0),xx,top+(bottom-top)*k/4-5.dp.toPx(),c)}
                }
                var date=Instant.ofEpochSecond(start).atZone(d.zone).toLocalDate();val today=Instant.ofEpochSecond(now).atZone(d.zone).toLocalDate()
                repeat(7) {val a=date.atStartOfDay(d.zone).toEpochSecond();val b=date.plusDays(1).atStartOfDay(d.zone).toEpochSecond()
                    if(a<start+span && b>start) {val x0=x(a).coerceIn(left,right);val x1=x(b).coerceIn(left,right);drawLine(LongEdge.copy(alpha=.5f),Offset(x0,top),Offset(x0,bottom),.7.dp.toPx())
                        val name=when(date) {today->uiText(R.string.ui_dnes_ce1bed34);today.plusDays(1)->uiText(R.string.ui_zajtra_c3e86eb2);else->date.format(DateTimeFormatter.ofPattern("EEE",appLocale()))}
                        val full=name+" · "+date.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()));val s=if(tm.measure(full,TextStyle(fontSize=9.sp)).size.width<=x1-x0-2.dp.toPx()) full else date.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()))
                        val r=tm.measure(s,TextStyle(fontSize=9.sp,color=ForecastMuted));if(r.size.width<=x1-x0) drawText(r,topLeft=Offset((x0+x1-r.size.width)/2,bottom+5.dp.toPx()))
                    };date=date.plusDays(1)
                }
                if(shown.any {it.axis=="wind"}) indices.filter {Instant.ofEpochSecond(d.times[it]).atZone(d.zone).hour%12==0}.forEach {i->val dir=d.value("wind_direction_10m",i);if(dir.isFinite()) {val angle=(dir+180)*PI/180;val v=Offset(sin(angle).toFloat(),-cos(angle).toFloat());val center=Offset(x(d.times[i]),top-7.dp.toPx());val tip=center+v*5.dp.toPx();val side=Offset(-v.y,v.x)*2.dp.toPx();drawLine(longColor(LongMetric.Wind),center-v*5.dp.toPx(),tip,1.dp.toPx());drawLine(longColor(LongMetric.Wind),tip,tip-v*3.dp.toPx()+side,1.dp.toPx());drawLine(longColor(LongMetric.Wind),tip,tip-v*3.dp.toPx()-side,1.dp.toPx())}}
                fun marker(time:Long,c:Color,s:String,badgeY:Float) {if(time in start..start+span) {val xx=x(time);val r=tm.measure(s,TextStyle(fontSize=9.sp,color=c));val pad=3.dp.toPx();val bx=(xx-r.size.width/2).coerceIn(left,(right-r.size.width).coerceAtLeast(left));drawLine(c,Offset(xx,top),Offset(xx,bottom),1.dp.toPx());drawRoundRect(Color(0xFF111A24),Offset(bx-pad,badgeY),Size(r.size.width+2*pad,r.size.height+2*pad),CornerRadius(3.dp.toPx()));drawText(r,topLeft=Offset(bx,badgeY+pad));drawLine(c,Offset(xx,badgeY+r.size.height+2*pad),Offset(xx,top),.7.dp.toPx())}}
                marker(now,LongText,uiText(R.string.ui_teraz_e6989b57)+Instant.ofEpochSecond(now).atZone(d.zone).format(DateTimeFormatter.ofPattern("HH:mm")),0f)
                selected?.let {i->marker(d.times[i],RainBlue,Instant.ofEpochSecond(d.times[i]).atZone(d.zone).format(DateTimeFormatter.ofPattern(appPattern("d. M. HH:mm"),appLocale())),20.dp.toPx())
                    val xx=x(d.times[i]);val pad=3.dp.toPx();val gap=2.dp.toPx();var prevBottom=top-1000f
                    shown.mapNotNull {m->val raw=lineRaw(m,i);if(raw.isFinite()) m to y(m,longValue(m,raw,u)).coerceIn(top,bottom) else null}.sortedBy {it.second}.forEach {(m,yy)->
                        val raw=lineRaw(m,i);val c=longColor(m,raw)
                        val r=tm.measure(weatherUnitNumber(longValue(m,raw,u),1)+" "+longUnit(m,u),TextStyle(fontSize=9.sp,color=c))
                        val w=r.size.width+2*pad;val h=r.size.height+2*pad
                        val cy=maxOf((yy-h/2).coerceIn(top,(bottom-h).coerceAtLeast(top)),prevBottom+gap);prevBottom=cy+h
                        val cx=if(xx+6.dp.toPx()+w<=right) xx+6.dp.toPx() else xx-6.dp.toPx()-w
                        drawCircle(c,3.dp.toPx(),Offset(xx,yy))
                        drawRoundRect(Color(0xFF111A24),Offset(cx,cy),Size(w,h),CornerRadius(3.dp.toPx()))
                        drawRoundRect(c,Offset(cx,cy),Size(w,h),CornerRadius(3.dp.toPx()),style=Stroke(1.dp.toPx()))
                        drawText(r,topLeft=Offset(cx+pad,cy+pad))
                    }
                }
                if(metrics.all {m->d.values[m.key].orEmpty().none {it.isFinite()}}) text(uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61),left,top+30.dp.toPx())
            }
            Box(Modifier.align(Alignment.BottomEnd).offset(y=6.dp).size(28.dp).combinedClickable(role=Role.Button,onClickLabel=uiText(R.string.ui_strucny_popis_e8d7e5f7),onLongClickLabel=uiText(R.string.ui_pomocnik_c68b7b84),onLongClick={help=true},onClick={tip=true}).semantics {contentDescription=uiText(R.string.ui_informacie_o_grafe_37b2a71e)+fullTitle},contentAlignment=Alignment.Center) {
                Text("ⓘ",color=ForecastMuted,fontSize=16.sp)
                if(tip) {
                    LaunchedEffect(tip) {delay(2600);tip=false}
                    Popup(popupPositionProvider=LongTipPosition,onDismissRequest={tip=false},properties=PopupProperties(focusable=true)) {
                        Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF10131A),contentColor=LongText,border=BorderStroke(1.dp,RainBlue),shadowElevation=4.dp) {
                            Text(metrics.joinToString(" ") {longTip(it)},Modifier.widthIn(max=240.dp).padding(horizontal=10.dp,vertical=6.dp),fontSize=13.sp)
                        }
                    }
                }
            }
            }
        }
    }
    if(help) ModalBottomSheet(onDismissRequest={help=false},containerColor=LongPanel,contentColor=LongText) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(fullTitle,style=MaterialTheme.typography.titleLarge)
            CompositionLocalProvider(LocalContentColor provides LongText) {
                metrics.forEach {LongMetricHelp(it)}
                HelpText(uiText(R.string.ui_ovladanie_v_grafe_podrz_a_posuvaj_prst_pre_odcitanie_hodno_73b85b53))
            }
        }
    }
}

