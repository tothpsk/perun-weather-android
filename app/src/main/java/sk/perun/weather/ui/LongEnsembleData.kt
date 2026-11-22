package sk.perun.weather.ui

import sk.perun.weather.R

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal data class LocationEnsembleData(
    val bands:Map<String,List<Pair<Long,EnsembleBand>>> = emptyMap(),
    val note:String,
    val sources:Map<String,String> = emptyMap(),
)

/** The median of the members of a quantity is kept beside its band, under the key of the quantity with this ending. */
internal const val LongMedianSuffix="#median"

internal fun resolveLongEnsemble(weatherModel:String,selection:String):String {
    require(selection in LocationModelCatalogue.ensembleIds) {uiText(R.string.ui_neznamy_ensemble_model_value_6c24183b, selection)}
    if(selection!=LocationModelCatalogue.AutomaticEnsemble) return selection
    return when {
        weatherModel.startsWith("ncep_") || weatherModel=="gfs_seamless" -> "ncep_gefs_seamless"
        weatherModel.startsWith("dwd_icon_d2") -> "dwd_icon_d2_eps"
        weatherModel.startsWith("dwd_icon_eu") -> "dwd_icon_eu_eps"
        weatherModel.startsWith("dwd_") || weatherModel=="icon_seamless" -> "dwd_icon_seamless_eps"
        weatherModel.startsWith("ecmwf_aifs") -> "ecmwf_aifs025_ensemble"
        weatherModel.startsWith("cmc_") -> "gem_global_ensemble"
        weatherModel.startsWith("ukmo_uk_") -> "ukmo_uk_ensemble_2km"
        weatherModel.startsWith("ukmo_") -> "ukmo_global_ensemble_20km"
        weatherModel=="meteoswiss_icon_ch1" -> "meteoswiss_icon_ch1_ensemble"
        weatherModel.startsWith("meteoswiss_") -> "meteoswiss_icon_ch2_ensemble"
        else -> "ecmwf_ifs025_ensemble"
    }.also {require(it in LocationModelCatalogue.ensembleIds)}
}

internal fun longEnsembleMembers(hourly:JSONObject,key:String):List<JSONArray> {
    val pattern=Regex(Regex.escape(key)+"_member([0-9]+)")
    val members=sortedMapOf<Int,JSONArray>()
    hourly.keys().asSequence().toList().sorted().forEach {name ->
        val match=pattern.matchEntire(name) ?: return@forEach
        val id=match.groupValues[1].toIntOrNull() ?: return@forEach
        val values=hourly.optJSONArray(name) ?: return@forEach
        members.putIfAbsent(id,values)
    }
    if(0 !in members) hourly.optJSONArray(key)?.let {members[0]=it}
    return members.values.toList()
}

internal fun parseLongEnsembleVariable(root:JSONObject,key:String,p:LongEnvelopePreferences):Map<String,List<Pair<Long,EnsembleBand>>> {
    val hourly=root.getJSONObject("hourly")
    val rawTimes=hourly.getJSONArray("time")
    val times=List(rawTimes.length()) {rawTimes.getLong(it)}
    require(times.isNotEmpty() && times.zipWithNext().all {(a,b)->b>a}) {uiText(R.string.ui_neplatne_casy_ensemble_d69337d8)}
    val members=longEnsembleMembers(hourly,key)
    fun values(i:Int)=members.map {a->if(a.isNull(i)) Double.NaN else a.optDouble(i,Double.NaN)}
    val band=times.indices.mapNotNull {i->ensembleBand(values(i),p.lower,p.upper)?.let {times[i] to it}}
    val result=mutableMapOf<String,List<Pair<Long,EnsembleBand>>>()
    if(band.isNotEmpty()) result[key]=band
    // The 50th percentile of the members; only its lower value is used.
    val median=times.indices.mapNotNull {i->ensembleBand(values(i),50,51)?.let {times[i] to it}}
    if(median.isNotEmpty()) result[key+LongMedianSuffix]=median
    if(key=="precipitation") {
        val probability=times.indices.mapNotNull {i->
            val finite=values(i).filter {it.isFinite() && it>=0.0}
            if(finite.size<2) null else times[i] to EnsembleBand(
                100.0*finite.count {it>=1.0}/finite.size,
                100.0*finite.count {it>=0.1}/finite.size,
                0.0,100.0,finite.size,
            )
        }
        if(probability.isNotEmpty()) result["precipitation_probability"]=probability
    }
    return result
}

private data class LongEnsembleCacheKey(val query:String,val model:String,val lower:Int,val upper:Int,val refresh:Int,val variable:String)
private data class LongEnsembleCacheEntry(val time:Long,val bands:Map<String,List<Pair<Long,EnsembleBand>>>)
private data class LongEnsembleVariableResult(val key:String,val bands:Map<String,List<Pair<Long,EnsembleBand>>>,val failed:Boolean)
private val LongEnsembleRequests=Semaphore(3)
private val LongEnsembleCache=LinkedHashMap<LongEnsembleCacheKey,LongEnsembleCacheEntry>()
private const val LongEnsembleCacheMillis=600_000L

internal suspend fun loadLongEnsemble(
    location:Location,
    weatherModel:String,
    selection:String,
    percentiles:LongEnvelopePreferences,
    refreshKey:Int=0,
):LocationEnsembleData=withContext(Dispatchers.IO) {
    val model=resolveLongEnsemble(weatherModel,selection)
    val name=LocationModelCatalogue.ensemble.first {it.id==model}.label
    val source="$name ($model)"
    val mode=if(selection==LocationModelCatalogue.AutomaticEnsemble) uiText(R.string.ui_automaticky_vyber_podla_rodiny_predpovedneho_modelu_5b0286bd) else uiText(R.string.ui_vyber_lokality_b65cfb4d)
    val prefix=uiText(R.string.ui_ensemble_value_value_pvaluepvalue_1f97e0c6, source, mode, percentiles.lower, percentiles.upper)
    val timezone=URLEncoder.encode(location.timezone.ifBlank {"auto"},"UTF-8")
    val query="latitude=${location.latitude}&longitude=${location.longitude}&timezone=$timezone&timeformat=unixtime&forecast_days=16&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"+(location.elevation?.let {"&elevation=$it"} ?: "")
    val metrics=LongMetric.entries.filter {it!=LongMetric.Prob}
    val results=coroutineScope {
        metrics.map {metric->async {
            LongEnsembleRequests.withPermit {
                val cacheKey=LongEnsembleCacheKey(query,model,percentiles.lower,percentiles.upper,refreshKey,metric.key)
                val cached=synchronized(LongEnsembleCache) {
                    val now=System.currentTimeMillis()
                    LongEnsembleCache.entries.removeAll {now-it.value.time !in 0L..LongEnsembleCacheMillis}
                    LongEnsembleCache[cacheKey]
                }
                if(cached!=null) LongEnsembleVariableResult(metric.key,cached.bands,false) else try {
                    val url="https://ensemble-api.open-meteo.com/v1/ensemble?$query&models="+URLEncoder.encode(model,"UTF-8")+"&hourly="+URLEncoder.encode(metric.key,"UTF-8")
                    val root=JSONObject(String(fetchBytes(url),Charsets.UTF_8))
                    val bands=parseLongEnsembleVariable(root,metric.key,percentiles)
                    synchronized(LongEnsembleCache) {
                        LongEnsembleCache[cacheKey]=LongEnsembleCacheEntry(System.currentTimeMillis(),bands)
                        while(LongEnsembleCache.size>192) LongEnsembleCache.remove(LongEnsembleCache.keys.first())
                    }
                    LongEnsembleVariableResult(metric.key,bands,false)
                } catch(e:CancellationException) {throw e} catch(e:Exception) {
                    LongEnsembleVariableResult(metric.key,emptyMap(),true)
                }
            }
        }}.awaitAll()
    }
    val bands=results.flatMap {it.bands.entries}.associate {it.key to it.value}
    val errors=results.filter {it.failed}.map {it.key}.toSet()
    val missing=metrics.filter {it.key !in bands && it.key !in errors}.joinToString(", ") {it.label}
    val failed=metrics.filter {it.key in errors}.joinToString(", ") {it.label}
    val note=buildString {
        append(prefix)
        append(uiText(R.string.ui_obalka_sa_pocita_samostatne_pre_kazdu_velicinu_a_hodinu_as_b5f39ef8))
        append(uiText(R.string.ui_chybajuce_udaje_sa_nedoplnaju_z_ineho_ensemble_b2b00123))
        if(missing.isNotEmpty()) append(uiText(R.string.ui_bez_dostatocnych_clenov_value_3d74c527, missing))
        if(failed.isNotEmpty()) append(uiText(R.string.ui_poziadavka_zlyhala_alebo_ju_api_odmietlo_value_nie_je_to_p_61af315a, failed))
        if("precipitation_probability" in bands) append(uiText(R.string.ui_pasmo_pravdepodobnosti_zrazok_nie_je_percentilove_hranice_defaa900))
    }
    LocationEnsembleData(bands,note,bands.keys.filter {!it.endsWith(LongMedianSuffix)}.associateWith {source})
}
