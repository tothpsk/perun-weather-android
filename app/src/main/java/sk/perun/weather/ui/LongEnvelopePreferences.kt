package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class LongEnvelopePreferences(val lower:Int=10,val upper:Int=90)
private fun readLongEnvelopePreferences(p:SharedPreferences):LongEnvelopePreferences {
    val lower=p.getInt("envelopeLower",10)
    val upper=p.getInt("envelopeUpper",90)
    return if(lower in 0..100 && upper in 0..100 && lower<upper) LongEnvelopePreferences(lower,upper) else LongEnvelopePreferences()
}
@Composable internal fun rememberLongEnvelopePreferences():LongEnvelopePreferences {
    val context=LocalContext.current
    val prefs=remember(context) {context.getSharedPreferences("perun_long_forecast",Context.MODE_PRIVATE)}
    var value by remember(prefs) {mutableStateOf(readLongEnvelopePreferences(prefs))}
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,key->if(key=="envelopeLower" || key=="envelopeUpper") value=readLongEnvelopePreferences(prefs)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        value=readLongEnvelopePreferences(prefs)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return value
}
@Composable internal fun LongEnvelopeSettings() {
    val context=LocalContext.current
    val prefs=remember(context) {context.getSharedPreferences("perun_long_forecast",Context.MODE_PRIVATE)}
    val saved=rememberLongEnvelopePreferences()
    var lower by remember(saved) {mutableStateOf(saved.lower.toString())}
    var upper by remember(saved) {mutableStateOf(saved.upper.toString())}
    val lo=lower.toIntOrNull();val hi=upper.toIntOrNull()
    val valid=lo!=null && hi!=null && lo in 0..100 && hi in 0..100 && lo<hi
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(uiText(R.string.ui_percentily_ensemble_obalky_a825c14f),style=MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=lower,onValueChange={if(it.length<=3 && it.all(Char::isDigit)) lower=it},modifier=Modifier.weight(1f),label={Text(uiText(R.string.ui_dolny_percentil_4cf4ff75))},singleLine=true,isError=!valid,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(value=upper,onValueChange={if(it.length<=3 && it.all(Char::isDigit)) upper=it},modifier=Modifier.weight(1f),label={Text(uiText(R.string.ui_horny_percentil_8555fb69))},singleLine=true,isError=!valid,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        }
        Text(uiText(R.string.ui_rozsah_0100_dolny_percentil_musi_byt_mensi_nez_horny_p10p9_bb4d7ae0),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!valid) Text(uiText(R.string.ui_zadaj_dve_cisla_od_0_do_100_v_rastucom_poradi_79646f92),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(enabled=valid && (lo!=saved.lower || hi!=saved.upper),onClick={if(valid) prefs.edit().putInt("envelopeLower",lo!!).putInt("envelopeUpper",hi!!).apply()}) {Text(uiText(R.string.ui_ulozit_percentily_02eece4c))}
            TextButton(onClick={lower="10";upper="90";prefs.edit().putInt("envelopeLower",10).putInt("envelopeUpper",90).apply()}) {Text(uiText(R.string.ui_obnovit_p10p90_7936736e))}
        }
        Text(uiText(R.string.ui_ulozene_pasmo_pvaluepvalue_61b19643, saved.lower, saved.upper),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
