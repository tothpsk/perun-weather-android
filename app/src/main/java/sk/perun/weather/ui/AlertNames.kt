package sk.perun.weather.ui

import sk.perun.weather.R

import java.time.Instant

private val HazardNames=mapOf(
    AlertHazard.Wind to R.string.alert_hazard_wind,AlertHazard.Snow to R.string.alert_hazard_snow,AlertHazard.Ice to R.string.alert_hazard_ice,
    AlertHazard.Storm to R.string.alert_hazard_storm,AlertHazard.Fog to R.string.alert_hazard_fog,AlertHazard.Heat to R.string.alert_hazard_heat,
    AlertHazard.Cold to R.string.alert_hazard_cold,AlertHazard.Coastal to R.string.alert_hazard_coastal,AlertHazard.Fire to R.string.alert_hazard_fire,
    AlertHazard.Avalanche to R.string.alert_hazard_avalanche,AlertHazard.Rain to R.string.alert_hazard_rain,AlertHazard.Flood to R.string.alert_hazard_flood,
    AlertHazard.RainFlood to R.string.alert_hazard_rain_flood,AlertHazard.Marine to R.string.alert_hazard_marine,AlertHazard.Drought to R.string.alert_hazard_drought,
    AlertHazard.Hail to R.string.alert_hazard_hail,AlertHazard.Dust to R.string.alert_hazard_dust,AlertHazard.Cyclone to R.string.alert_hazard_cyclone,
    AlertHazard.Tornado to R.string.alert_hazard_tornado,AlertHazard.Earthquake to R.string.alert_hazard_earthquake,AlertHazard.Tsunami to R.string.alert_hazard_tsunami,
    AlertHazard.Landslide to R.string.alert_hazard_landslide,AlertHazard.Volcano to R.string.alert_hazard_volcano,AlertHazard.Air to R.string.alert_hazard_air,
    AlertHazard.Generic to R.string.alert_hazard_generic,
)
private val LocalNames=mapOf("Thunderstorm risk" to R.string.ui_riziko_burok_8fa6514a,"Strong wind gusts" to R.string.ui_silne_narazy_vetra_7b7b006f,"Heavy precipitation" to R.string.ui_silne_zrazky_2630cc03,"Extreme heat" to R.string.ui_extremne_teplo_cdcd5041,"Extreme cold" to R.string.ui_extremny_chlad_d4aeb237)

internal fun hazardName(h: AlertHazard)=uiText(HazardNames[h] ?: R.string.alert_hazard_generic)
/** The text of the warning in the app language, or English, or the original. */
internal fun alertText(a: UnifiedAlert)=pickText(a.texts,appLocale().toLanguageTag())
/** The short general name of the kinds ("Sneženie", "Poľadovica · Sneženie"); a warning of no known kind keeps the name the issuer gave it. */
internal fun alertName(a: UnifiedAlert): String {
    if (a.hazards.all { it==AlertHazard.Generic }) alertText(a)?.event?.takeIf { it.isNotBlank() }?.let { return it }
    return a.hazards.ifEmpty { listOf(AlertHazard.Generic) }.joinToString(" · ") { hazardName(it) }
}
/** The kind whose icon stands for the warning. */
internal fun alertIconKind(a: UnifiedAlert)=a.hazards.firstOrNull() ?: AlertHazard.Generic
/** A local estimate of the forecast as a warning of the unified model; its text is the name of the rule in the app language. */
internal fun localAlert(hit: LocalAlertHit,now: Instant): UnifiedAlert {
    val name=LocalNames[hit.event]?.let { uiText(it) } ?: hit.event
    return UnifiedAlert(
        key=null,supersedes=emptySet(),msgType="Alert",hazards=listOf(alertHazard(hit.event)),hazardBasis=Basis.EventCode,
        level=AlertLevel.entries.firstOrNull { it.key==hit.level } ?: AlertLevel.Neutral,levelBasis=Basis.EventCode,
        start=hit.start,end=hit.end,expires=hit.end,sent=now,areas=emptySet(),issuer=uiText(R.string.ui_lokalny_odhad_72c32ba1),web="",
        texts=listOf(AlertText(appLocale().toLanguageTag(),name,"","","","")),sources=emptyList(),local=true,
    )
}
/**
 * Stable references of a warning, the same in the app and in the background: every CAP key it was joined from.
 * A warning without a CAP message is referred to by its name, level and source entry.
 */
internal fun alertRefs(a: UnifiedAlert): Set<String> {
    val keys=a.capKeys()
    if (keys.isNotEmpty()) return keys.map { it.sender+"|"+it.identifier }.toSet()
    return setOf("~"+(alertText(a)?.event.orEmpty())+"|"+a.level.key+(if (a.local) "" else "|"+a.sources.firstOrNull()?.sourceId.orEmpty()))
}
/** The main reference: the CAP key of the warning, else the first one. */
internal fun alertRef(a: UnifiedAlert): String = a.key?.let { it.sender+"|"+it.identifier } ?: alertRefs(a).first()
