package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun MoreScreen(onAir:()->Unit,onSettings:()->Unit,onSunMoon:()->Unit,onMarine:()->Unit,location:Location) {
    var longOpen by rememberSaveable {mutableStateOf(false)}
    BackHandler(enabled=longOpen) {longOpen=false}
    if(longOpen) {key(location) {LongForecastScreen(location)};return}
    Column(Modifier.fillMaxSize().background(Color(0xFF323E4F)).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        MoreEntry(uiText(R.string.ui_podrobna_predpoved_6372d922),null,{longOpen=true},PerunIcons.Detail)
        MoreEntry(uiText(R.string.ui_kvalita_ovzdusia_a_pel_4aa97320),null,onAir,PerunIcons.Air)
        MoreEntry(uiText(R.string.ui_slnko_a_mesiac_10659686),null,onSunMoon,PerunIcons.SunMoon)
        MoreEntry(uiText(R.string.ui_more_d47d7cb0),null,onMarine,PerunIcons.Wave)
        MoreEntry(uiText(R.string.ui_nastavenia_4465d758),null,onSettings,PerunIcons.Settings)
    }
}
@Composable private fun MoreEntry(title:String,description:String?,onClick:()->Unit,icon:ImageVector?=null) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFF4B586C)),colors=CardDefaults.outlinedCardColors(containerColor=Color(0xFF202833),contentColor=Color(0xFFE8EAF0))) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(icon!=null) Icon(icon,contentDescription=null,tint=Color(0xFF4FC3F7),modifier=Modifier.size(28.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(title,style=MaterialTheme.typography.titleMedium,color=Color(0xFFE8EAF0));if(description!=null) Text(description,color=Color(0xFFBBC4D2),fontSize=13.sp)}
            Text("›",color=Color(0xFF4FC3F7),fontSize=30.sp)
        }
    }
}
