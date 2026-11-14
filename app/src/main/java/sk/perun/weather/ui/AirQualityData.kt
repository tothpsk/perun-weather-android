package sk.perun.weather.ui

import sk.perun.weather.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.ZoneId

internal data class AirLevel(val label:String,val band:Int)
internal fun airQualityLevel(value:Double,scale:AirQualityScale):AirLevel? {
    if(!value.isFinite() || value<0) return null
    val eu=scale==AirQualityScale.European
    val limits=if(eu) listOf(20.0,40.0,60.0,80.0,100.0) else listOf(50.0,100.0,150.0,200.0,300.0)
    val band=limits.indexOfFirst { value<=it }.let { if(it<0) 5 else it }
    val labels=if(eu) listOf(uiText(R.string.ui_dobra_19daeeb7),uiText(R.string.ui_prijatelna_47496cf3),uiText(R.string.ui_mierna_5f589b5b),uiText(R.string.ui_zla_258323d1),uiText(R.string.ui_velmi_zla_ec30c594),uiText(R.string.ui_extremne_zla_9c6c07ef)) else listOf(uiText(R.string.ui_dobra_19daeeb7),uiText(R.string.ui_mierna_5f589b5b),uiText(R.string.ui_nezdrava_pre_citlive_skupiny_7c64c682),uiText(R.string.ui_nezdrava_4f9ce3d5),uiText(R.string.ui_velmi_nezdrava_06e7390c),uiText(R.string.ui_nebezpecna_f88656e8))
    return AirLevel(labels[band],band)
}
internal data class AirPollutant(val key:String,val symbol:String,val name:String,val european:Boolean=true)
internal val AirPollutants=listOf(AirPollutant("pm2_5",uiText(R.string.ui_pm2_5_7d02b735),uiText(R.string.ui_jemne_castice_5b95fd95)),AirPollutant("pm10",uiText(R.string.ui_pm10_dca32eb0),uiText(R.string.ui_prachove_castice_a8cc6445)),AirPollutant("nitrogen_dioxide",uiText(R.string.ui_no2_13442efb),uiText(R.string.ui_oxid_dusicity_06483361)),AirPollutant("ozone",uiText(R.string.ui_o3_1903ec79),uiText(R.string.ui_ozon_ae213c8d)),AirPollutant("sulphur_dioxide",uiText(R.string.ui_so2_220bf934),uiText(R.string.ui_oxid_siricity_245bd3ab)),AirPollutant("carbon_monoxide",uiText(R.string.ui_co_fc8842c6),uiText(R.string.ui_oxid_uholnaty_e8f2de2d),false))
internal data class AirPollenType(val key:String,val name:String,val elevated:Double,val high:Double)
internal val AirPollenTypes=listOf(AirPollenType("alder_pollen",uiText(R.string.ui_jelsa_50b3fc2b),50.0,200.0),AirPollenType("birch_pollen",uiText(R.string.ui_breza_274e3ab2),50.0,400.0),AirPollenType("grass_pollen",uiText(R.string.ui_trava_30b5b0b5),20.0,50.0),AirPollenType("mugwort_pollen",uiText(R.string.ui_palina_6c25bcd3),20.0,50.0),AirPollenType("olive_pollen",uiText(R.string.ui_oliva_3462b6a4),50.0,200.0),AirPollenType("ragweed_pollen",uiText(R.string.ui_ambrozia_5cdaf78e),10.0,50.0))
internal val AirPollenLevelNames=listOf(uiText(R.string.ui_nizka_992049fb),uiText(R.string.ui_zvysena_2944aa11),uiText(R.string.ui_vysoka_35517062))
internal fun airPollenLevel(type:AirPollenType,value:Double):Int = if(!value.isFinite()) -1 else if(value>=type.high) 2 else if(value>=type.elevated) 1 else 0
internal data class AirQualityData(val times:List<Long>,val zone:ZoneId,val values:Map<String,List<Double>>,val units:Map<String,String>) {
    fun at(key:String,index:Int)=values[key]?.getOrNull(index) ?: Double.NaN
}
internal suspend fun loadAirQuality(location:Location):AirQualityData=withContext(Dispatchers.IO) {
    require(location.latitude.isFinite() && location.longitude.isFinite() && location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0)
    val variables=buildList {
        addAll(AirPollutants.map { it.key });add("european_aqi");add("us_aqi")
        AirPollutants.forEach { p -> if(p.european) add("european_aqi_${p.key}");add("us_aqi_${p.key}") }
        addAll(AirPollenTypes.map { it.key })
    }
    val tz=URLEncoder.encode(location.timezone.ifBlank { "auto" },"UTF-8")
    val url="https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${location.latitude}&longitude=${location.longitude}&timezone=$tz&timeformat=unixtime&forecast_days=5&hourly="+variables.joinToString(",")
    val root=JSONObject(String(fetchBytes(url),Charsets.UTF_8));val h=root.getJSONObject("hourly");val t=h.getJSONArray("time")
    require(t.length()>0) { uiText(R.string.ui_chybaju_hodinove_udaje_33c2aefa) }
    val times=List(t.length()) { t.getLong(it) }
    require(times.zipWithNext().all { (a,b)->b>a }) { uiText(R.string.ui_neplatne_casove_poradie_efcd08b4) }
    val values=variables.associateWith { key ->
        val a=h.optJSONArray(key)
        List(times.size) { i -> val v=if(a==null || a.isNull(i)) Double.NaN else a.optDouble(i,Double.NaN);if(v.isFinite() && v>=0) v else Double.NaN }
    }
    val units=root.optJSONObject("hourly_units")
    AirQualityData(times,ZoneId.of(root.getString("timezone")),values,AirPollutants.associate { it.key to (units?.optString(it.key,"µg/m³") ?: "µg/m³") }+AirPollenTypes.associate { it.key to uiText(R.string.ui_zrn_m3_2736b71a) })
}
