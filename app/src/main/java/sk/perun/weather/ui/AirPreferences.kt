package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal enum class AirGasUnit(val label:String) { Micrograms("µg/m³"),Ppb(uiText(R.string.ui_ppb_co_v_ppm_8c3193c0)) }
internal data class AirPreferences(val scale:AirQualityScale,val gas:AirGasUnit)

private const val AirPrefsName="perun_air_detail"
private fun readAirPreferences(p:SharedPreferences)=AirPreferences(
    AirQualityScale.entries.firstOrNull { it.name==p.getString("scale","") } ?: AirQualityScale.European,
    AirGasUnit.entries.firstOrNull { it.name==p.getString("gasUnit","") } ?: AirGasUnit.Micrograms,
)

@Composable internal fun rememberAirPreferences():AirPreferences {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences(AirPrefsName,Context.MODE_PRIVATE) }
    var value by remember(prefs) { mutableStateOf(readAirPreferences(prefs)) }
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,key->if(key=="scale" || key=="gasUnit") value=readAirPreferences(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        value=readAirPreferences(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

internal fun airConvert(key:String,value:Double,gas:AirGasUnit):Double {
    if(gas==AirGasUnit.Micrograms || !value.isFinite()) return value
    val factor=when(key) { "nitrogen_dioxide"->0.5314;"ozone"->0.5094;"sulphur_dioxide"->0.3816;"carbon_monoxide"->0.000873;else->1.0 }
    return value*factor
}

internal fun airUnitLabel(key:String,gas:AirGasUnit):String = if(gas==AirGasUnit.Micrograms) "µg/m³" else when(key) { "nitrogen_dioxide","ozone","sulphur_dioxide"->"ppb";"carbon_monoxide"->"ppm";else->"µg/m³" }

@Composable internal fun AirQualitySettings() {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences(AirPrefsName,Context.MODE_PRIVATE) }
    val current=rememberAirPreferences()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(uiText(R.string.ui_index_kvality_ovzdusia_d9bab98d),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            PerunChips(AirQualityScale.entries.map { it.label },current.scale.ordinal,{ prefs.edit().putString("scale",AirQualityScale.entries[it].name).apply() })
        }
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(uiText(R.string.ui_koncentracie_plynov_055a3e2f),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsInfo(uiText(R.string.settings_gas_info))
            }
            PerunChips(AirGasUnit.entries.map { it.label },current.gas.ordinal,{ prefs.edit().putString("gasUnit",AirGasUnit.entries[it].name).apply() })
        }
    }
}
