package sk.perun.weather.ui

import sk.perun.weather.R

internal data class LocationModelChoice(val id:String,val label:String)
internal object LocationModelCatalogue {
    const val AutomaticEnsemble="auto"
    private fun parse(raw:String):List<LocationModelChoice> = raw.trimIndent().lineSequence().filter {it.isNotBlank()}.map {
        val parts=it.split('|',limit=2)
        require(parts.size==2 && parts[0].isNotBlank() && parts[1].isNotBlank())
        LocationModelChoice(parts[0],parts[1])
    }.toList().also {require(it.map {m->m.id}.distinct().size==it.size)}
    // Catalogue supplied by the user; presence here does not establish geographic or API availability.
    val weather=parse("""
        best_match|Best match
        ecmwf_ifs|ECMWF IFS HRES
        ecmwf_ifs025|ECMWF IFS 0.25°
        ecmwf_aifs025_single|ECMWF AIFS Single
        cma_grapes_global|CMA GRAPES Global
        bom_access_global|BOM ACCESS Global
        ncep_gfs_seamless|NCEP GFS Seamless
        ncep_gfs_global|NCEP GFS Global
        ncep_hrrr_conus|NCEP HRRR CONUS
        ncep_nbm_conus|NCEP NBM CONUS
        ncep_aigfs025|NCEP AIGFS
        ncep_nam_conus|NCEP NAM CONUS
        ncep_hgefs025_ensemble_mean|NCEP HGEFS Ensemble Mean
        jma_seamless|JMA Seamless
        jma_msm|JMA MSM
        jma_gsm|JMA GSM
        kma_seamless|KMA Seamless
        kma_ldps|KMA LDPS
        kma_gdps|KMA GDPS
        dwd_icon_seamless|DWD ICON Seamless
        dwd_icon_global|DWD ICON Global
        dwd_icon_global_native|DWD ICON Global Native
        dwd_aicon_global_native|DWD AICON Global Native
        dwd_icon_eu|DWD ICON EU
        dwd_icon_eu_native|DWD ICON EU Native
        dwd_icon_d2|DWD ICON D2
        dwd_icon_d2_native|DWD ICON D2 Native
        dwd_icon_d2_ruc_native|DWD ICON D2 RUC Native
        cmc_gem_seamless|CMC GEM Seamless
        cmc_gem_gdps|CMC GEM GDPS
        cmc_gem_rdps|CMC GEM RDPS
        cmc_gem_hrdps|CMC GEM HRDPS
        cmc_gem_hrdps_west|CMC GEM HRDPS West
        meteofrance_seamless|Météo-France Seamless
        meteofrance_arpege_world|Météo-France ARPEGE World
        meteofrance_arpege_europe|Météo-France ARPEGE Europe
        meteofrance_arome_france|Météo-France AROME France
        meteofrance_arome_france_hd|Météo-France AROME France HD
        italia_meteo_arpae_icon_2i|ItaliaMeteo ARPAE ICON 2I
        metno_seamless|MET Norway Seamless
        metno_nordic|MET Norway Nordic
        knmi_harmonie_arome_europe|KNMI HARMONIE AROME Europe
        knmi_seamless|KNMI Seamless
        knmi_harmonie_arome_netherlands|KNMI HARMONIE AROME Netherlands
        dmi_seamless|DMI Seamless
        dmi_harmonie_arome_europe|DMI HARMONIE AROME Europe
        ukmo_global_deterministic_10km|UK Met Office Global 10 km
        ukmo_uk_deterministic_2km|UK Met Office UK 2 km
        ukmo_seamless|UK Met Office Seamless
        meteoswiss_icon_seamless|MeteoSwiss ICON Seamless
        meteoswiss_icon_ch2|MeteoSwiss ICON CH2
        meteoswiss_icon_ch1|MeteoSwiss ICON CH1
        geosphere_seamless|GeoSphere Seamless
        geosphere_arome_austria|GeoSphere AROME Austria
        chmi_aladin_seamless|ČHMÚ ALADIN Seamless
        chmi_aladin_central_europe_2km|ČHMÚ ALADIN Central Europe 2 km
        chmi_aladin_cz_1km|ČHMÚ ALADIN CZ 1 km
    """)
    val ensemble=parse("""
        auto|${uiText(R.string.ensemble_automatic_label)}
        dwd_icon_seamless_eps|DWD ICON Seamless EPS
        dwd_icon_global_eps|DWD ICON Global EPS
        dwd_icon_global_eps_native|DWD ICON Global EPS Native
        dwd_icon_eu_eps|DWD ICON EU EPS
        dwd_icon_eu_eps_native|DWD ICON EU EPS Native
        dwd_icon_d2_eps|DWD ICON D2 EPS
        dwd_icon_d2_eps_native|DWD ICON D2 EPS Native
        ncep_gefs_seamless|NCEP GEFS Seamless
        ncep_gefs025|NCEP GEFS 0.25°
        ncep_gefs05|NCEP GEFS 0.5°
        ncep_aigefs025|NCEP AIGEFS
        ecmwf_ifs025_ensemble|ECMWF IFS Ensemble
        ecmwf_aifs025_ensemble|ECMWF AIFS Ensemble
        ecmwf_ifs_europe_ensemble|ECMWF IFS Europe Ensemble
        ecmwf_aifs_europe_ensemble|ECMWF AIFS Europe Ensemble
        ukmo_global_ensemble_20km|UK Met Office Global Ensemble 20 km
        ukmo_uk_ensemble_2km|UK Met Office UK Ensemble 2 km
        gem_global_ensemble|GEM Global Ensemble
        meteoswiss_icon_ch1_ensemble|MeteoSwiss ICON CH1 Ensemble
        meteoswiss_icon_ch2_ensemble|MeteoSwiss ICON CH2 Ensemble
        google_weathernext2_ensemble|Google WeatherNext 2 Ensemble
    """)
    val weatherIds=weather.map {it.id}.toSet()
    val ensembleIds=ensemble.map {it.id}.toSet()
    fun weatherChoices(verifiedIds:Set<String>)=weather.filter {it.id=="best_match" || it.id in verifiedIds}
    fun ensembleChoices(verifiedIds:Set<String>)=ensemble.filter {it.id==AutomaticEnsemble || it.id in verifiedIds}
}
