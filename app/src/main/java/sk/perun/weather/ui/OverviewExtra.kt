package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

internal data class OverviewExtra(val humidity:Double,val dewPoint:Double,val visibility:Double,val pressure:Double,val pressureChange:Double,val fallback:List<String> = emptyList())
internal data class ExtraBand(val color:Color,val name:String)

internal val PressureRiseFast=Color(0xFF1E6FD9)
internal val PressureRise=Color(0xFF4FC3F7)

internal val BeaufortLimits=listOf(0.0,1.1,5.8,12.2,19.8,28.8,38.9,50.0,61.9,74.9,88.2,102.6,117.7)
internal val BeaufortNames=listOf(uiText(R.string.ui_bezvetrie_cd38ecb2),uiText(R.string.ui_vanok_565e5344),uiText(R.string.ui_slaby_vietor_96b40e12),uiText(R.string.ui_mierny_vietor_93ea48e7),uiText(R.string.ui_dost_cerstvy_vietor_aac5a22e),uiText(R.string.ui_cerstvy_vietor_a5ad3c92),uiText(R.string.ui_silny_vietor_2766694f),uiText(R.string.ui_prudky_vietor_03f07e8a),uiText(R.string.ui_burlivy_vietor_12ab2d33),uiText(R.string.ui_vichrica_c95b9839),uiText(R.string.ui_silna_vichrica_2d7e107a),uiText(R.string.ui_mohutna_vichrica_453ef247),uiText(R.string.ui_orkan_8f9a5a59))
internal fun beaufortIndex(kmh:Double):Int=if(!kmh.isFinite() || kmh<0) -1 else BeaufortLimits.indexOfLast { kmh>=it }

internal fun humidityBand(humidity:Double,dewPoint:Double):ExtraBand? = when {
    !humidity.isFinite() -> null
    humidity<30 -> ExtraBand(Color(0xFFE6C27A),uiText(R.string.ui_suchy_vzduch_b65d36c7))
    dewPoint.isFinite() && dewPoint>=21 -> ExtraBand(Color(0xFFFF6B6B),uiText(R.string.ui_dusno_ab731e80))
    dewPoint.isFinite() && dewPoint>=16 -> ExtraBand(Color(0xFFFFB74D),uiText(R.string.ui_vlhka_8c3aea38))
    else -> ExtraBand(Color(0xFF66D17A),uiText(R.string.ui_prijemna_26c506d8))
}

internal fun visibilityBand(meters:Double):ExtraBand? {
    if(!meters.isFinite() || meters<0) return null
    val km=meters/1000.0
    return when {
        km>=10 -> ExtraBand(Color(0xFF66D17A),uiText(R.string.ui_dobra_19daeeb7))
        km>=4 -> ExtraBand(Color(0xFFFFD166),uiText(R.string.ui_znizena_7bb856e1))
        km>=1 -> ExtraBand(Color(0xFFFB8C00),uiText(R.string.ui_zla_258323d1))
        else -> ExtraBand(Color(0xFFE53935),uiText(R.string.ui_hmla_386a47cf))
    }
}

internal fun pressureBand(change:Double):ExtraBand = when {
    !change.isFinite() -> ExtraBand(Color(0xFF8C9BB2),uiText(R.string.ui_trend_nie_je_dostupny_9e207c82))
    change>=3 -> ExtraBand(PressureRiseFast,uiText(R.string.ui_rychlo_stupa_aea55e49))
    change>=1 -> ExtraBand(PressureRise,uiText(R.string.ui_stupa_c05574b4))
    change<=-3 -> ExtraBand(Color(0xFFFF6B6B),uiText(R.string.ui_rychlo_klesa_1bac87f5))
    change<=-1 -> ExtraBand(Color(0xFFFFB74D),uiText(R.string.ui_klesa_54707dc3))
    else -> ExtraBand(Color(0xFF66D17A),uiText(R.string.ui_stabilny_17f01156))
}

internal suspend fun loadOverviewExtra(l:Location,model:String="best_match"):OverviewExtra? = withContext(Dispatchers.IO) {
    require(model in LocationModelCatalogue.weatherIds) {uiText(R.string.ui_neznamy_predpovedny_model_value_28933271, model)}
    try {
        val tz=URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8")
        val elevation=l.elevation?.let { "&elevation=$it" } ?: ""
        val modelQuery=if(model=="best_match") "" else "&models="+URLEncoder.encode(model,"UTF-8")
        val query="latitude=${l.latitude}&longitude=${l.longitude}&timezone=$tz$elevation&current=relative_humidity_2m,dew_point_2m,visibility,pressure_msl&hourly=pressure_msl&past_hours=3&forecast_hours=1"
        val root=JSONObject(String(fetchBytes("https://api.open-meteo.com/v1/forecast?$query$modelQuery"),Charsets.UTF_8))
        val c=root.getJSONObject("current")
        val h0=root.optJSONObject("hourly")?.optJSONArray("pressure_msl")
        var h=h0
        // Values the selected model does not provide are taken from best_match; the footer lists them.
        val filled=ArrayList<String>()
        val missing=listOf("relative_humidity_2m","dew_point_2m","visibility","pressure_msl").filter { !c.optDouble(it,Double.NaN).isFinite() }
        val hourlyMissing=h0==null || (0 until h0.length()).all { h0.isNull(it) }
        if(model!="best_match" && (missing.isNotEmpty() || hourlyMissing)) try {
            val fb=JSONObject(String(fetchBytes("https://api.open-meteo.com/v1/forecast?$query"),Charsets.UTF_8))
            val fc=fb.getJSONObject("current")
            for(key in missing) { val v=fc.optDouble(key,Double.NaN); if(v.isFinite()) { c.put(key,v); filled+=key } }
            if(hourlyMissing) {
                val fh=fb.optJSONObject("hourly")?.optJSONArray("pressure_msl")
                if(fh!=null && (0 until fh.length()).any { !fh.isNull(it) }) { h=fh; if("pressure_msl" !in filled) filled+="pressure_msl" }
            }
        } catch(e:CancellationException) { throw e } catch(e:Exception) { }
        val series=h
        val first=series?.optDouble(0,Double.NaN) ?: Double.NaN
        val last=if(series!=null && series.length()>0) series.optDouble(series.length()-1,Double.NaN) else Double.NaN
        OverviewExtra(
            humidity=c.optDouble("relative_humidity_2m",Double.NaN),
            dewPoint=c.optDouble("dew_point_2m",Double.NaN),
            visibility=c.optDouble("visibility",Double.NaN),
            pressure=c.optDouble("pressure_msl",Double.NaN),
            pressureChange=if(series!=null && series.length()>=2 && first.isFinite() && last.isFinite()) last-first else Double.NaN,
            fallback=filled,
        )
    } catch(e:CancellationException) { throw e } catch(e:Exception) { null }
}
