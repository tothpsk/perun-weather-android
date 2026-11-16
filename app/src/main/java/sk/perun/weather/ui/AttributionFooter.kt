package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

// meteoAlarm: the screen shows MeteoAlarm alerts for this place, so their source is named next to Open-Meteo.
@Composable internal fun AttributionFooter(note:String?=null,meteoAlarm:Boolean=false) {
    val uri=LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    if(note!=null) Text(note,Modifier.fillMaxWidth().padding(top=8.dp),color=ForecastMuted,fontSize=12.sp,textAlign=TextAlign.Center)
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        Text("Open-Meteo (CC BY 4.0)",Modifier.clickable { uri.openUri("https://open-meteo.com/") }.padding(vertical=8.dp),color=ForecastMuted,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        Text(" · ",color=ForecastMuted,fontSize=12.sp)
        Text(uiText(R.string.ui_viac_66c91b9a),Modifier.clickable { open=true }.padding(vertical=8.dp),color=ForecastMuted,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    }
    if(meteoAlarm) Text("EUMETNET – MeteoAlarm",Modifier.fillMaxWidth().clickable { uri.openUri("https://meteoalarm.org/en/live/") }.padding(bottom=8.dp),color=ForecastMuted,fontSize=12.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
    if(open) AlertDialog(
        onDismissRequest={open=false},
        containerColor=Color(0xFF202833),
        titleContentColor=Color(0xFFE8EAF0),
        textContentColor=ForecastMuted,
        title={Text(uiText(R.string.ui_zdroje_udajov_dbbe1dcb))},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            SourceEntry(uiText(R.string.ui_aktualne_pocasie_e2638e4d),"Open-Meteo (CC BY 4.0)","https://open-meteo.com/")
            SourceEntry(uiText(R.string.ui_predpoved_20fdfd9d),"Open-Meteo (CC BY 4.0)","https://open-meteo.com/")
            SourceEntry(uiText(R.string.ui_kvalita_ovzdusia_08946dda),"Open-Meteo · CAMS/Copernicus","https://open-meteo.com/en/docs/air-quality-api")
            SourceEntry(uiText(R.string.ui_pel_ca4a713f),"Open-Meteo · CAMS/Copernicus","https://open-meteo.com/en/docs/air-quality-api")
            SourceEntry(uiText(R.string.ui_nowcast_zrazok_753057c8),uiText(R.string.ui_librewxr_zaloha_open_meteo_91902c11),"https://librewxr.net/")
            SourceEntry(uiText(R.string.ui_burkove_bunky_a_blesky_cc129d3b),"LibreWXR","https://librewxr.net/")
            SourceEntry(uiText(R.string.ui_vystrahy_d88f15ec),"EUMETNET – MeteoAlarm","https://meteoalarm.org/en/live/")
            Text(uiText(R.string.sources_meteoalarm_delay),fontSize=12.sp)
            SourceEntry(uiText(R.string.sources_alert_areas),uiText(R.string.sources_alert_areas_credit),"https://github.com/NiklasJordan/meteoalarm")
            SourceEntry(uiText(R.string.ui_vystrahy_dalsi_zdroj_foss_predvoleny_96081ceb),"KDE FOSS Public Alert Server","https://alerts.kde.org/")
            SourceEntry(uiText(R.string.ui_vystrahy_dalsi_zdroj_volitelny_9b32d6b9),"LibreWXR","https://librewxr.net/")
            Text(uiText(R.string.ui_vystrahy_doplnaju_aj_lokalne_odhady_z_predpovede_lokalny_o_cfa8a6ab),fontSize=12.sp)
            Text(uiText(R.string.ui_contains_modified_copernicus_atmosphere_monitoring_service_d82a97ef, LocalDate.now().year),fontSize=12.sp)
            Text(uiText(R.string.ui_europska_komisia_ani_ecmwf_nezodpovedaju_za_pouzitie_infor_85a94267),fontSize=12.sp)
            SourceEntry(uiText(R.string.ui_licencia_udajov_open_meteo_5b91d58f),"Creative Commons Attribution 4.0","https://creativecommons.org/licenses/by/4.0/")
        }},
        confirmButton={TextButton(onClick={open=false}) {Text(uiText(R.string.ui_zavriet_7c32fd0b),color=Color(0xFF4FC3F7))}},
    )
}

@Composable private fun SourceEntry(title:String,label:String,url:String) {
    val uri=LocalUriHandler.current
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,color=Color(0xFFE8EAF0),fontSize=14.sp)
        Text(label,Modifier.clickable { uri.openUri(url) },fontSize=12.sp,color=ForecastMuted,textDecoration=TextDecoration.Underline)
    }
}
