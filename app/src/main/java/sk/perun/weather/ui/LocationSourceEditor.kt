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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EditorPanel=Color(0xFF202833)
private val EditorText=Color(0xFFE8EAF0)
private val EditorMuted=Color(0xFFBBC4D2)
private val EditorAccent=Color(0xFF4FC3F7)
private val EditorEdge=Color(0xFF4B586C)

private enum class SourceSection { Weather,Ensemble,Nowcast,Alerts }
private val NowcastChoices=listOf(LocationModelChoice("libre","LibreWXR (radar)"),LocationModelChoice("openmeteo","Open-Meteo (model)"))
private fun alertChoices()=listOf(LocationModelChoice("local",uiText(R.string.alert_source_local)),LocationModelChoice("meteo",uiText(R.string.alert_source_meteo)),LocationModelChoice("libre",uiText(R.string.alert_source_libre)),LocationModelChoice("kde",uiText(R.string.alert_source_kde)))
private fun alertSourceShort(id:String)=when(id) {"local"->uiText(R.string.alert_source_local_short);"meteo"->"MeteoAlarm";"libre"->"LibreWXR";else->"FOSS"}

/**
 * Per-location sources, structured like Settings: a hub with one row per section, each opening a plain list.
 * Tapping a model selects it at once (as Breezy Weather does); availability is verified in the background and an
 * unavailable model is reverted with a one-line note.
 */
@Composable internal fun LocationSourceEditor(location:Location,controller:ModelAvailabilityController,onDismiss:()->Unit) {
    val context=LocalContext.current
    val store=remember(context) {LocationSourceStore(context)}
    val saved=rememberLocationSourcePreferences(location)
    val key=locationSourceKey(location)
    var weather by remember(key,saved) {mutableStateOf(saved.weatherModel)}
    var ensemble by remember(key,saved) {mutableStateOf(saved.ensembleModel)}
    var alerts by remember(key,saved) {mutableStateOf(saved.alertSource)}
    var nowcast by remember(key,saved) {mutableStateOf(saved.nowcastSource)}
    var section by remember(key) {mutableStateOf<SourceSection?>(null)}
    var note by remember(key) {mutableStateOf<String?>(null)}
    val weatherStatus=controller.statusOf(weather)
    val ensembleStatus=controller.statusOf(ensemble)
    LaunchedEffect(weather,weatherStatus) {
        if(weatherStatus==ModelStatus.NoData || weatherStatus==ModelStatus.Failed) {
            note=uiText(if(weatherStatus==ModelStatus.NoData) R.string.model_unavailable_note else R.string.model_check_failed_note,LocationModelCatalogue.weather.firstOrNull {it.id==weather}?.label ?: weather)
            weather=saved.weatherModel
        }
    }
    LaunchedEffect(ensemble,ensembleStatus) {
        if(ensembleStatus==ModelStatus.NoData || ensembleStatus==ModelStatus.Failed) {
            note=uiText(if(ensembleStatus==ModelStatus.NoData) R.string.model_unavailable_note else R.string.model_check_failed_note,LocationModelCatalogue.ensemble.firstOrNull {it.id==ensemble}?.label ?: ensemble)
            ensemble=saved.ensembleModel
        }
    }
    val valid=weatherStatus==ModelStatus.Available && ensembleStatus==ModelStatus.Available
    fun weatherLabel()=LocationModelCatalogue.weather.firstOrNull {it.id==weather}?.label ?: weather
    fun ensembleLabel()=LocationModelCatalogue.ensemble.firstOrNull {it.id==ensemble}?.label ?: ensemble
    fun alertsLabel():String {val ids=alertSourceSet(alerts);return if(ids.isEmpty()) uiText(R.string.alert_sources_off) else AlertSourceIds.filter {it in ids}.joinToString(", ") {alertSourceShort(it)}}
    fun nowcastLabel()=NowcastChoices.firstOrNull {it.id==nowcast}?.label ?: nowcast
    val title=when(section) {
        null->uiText(R.string.ui_zdroje_35dc0f31)+location.name
        SourceSection.Weather->uiText(R.string.ui_predpovedny_model_666f632c)
        SourceSection.Ensemble->uiText(R.string.ui_ensemble_model_b09a8627)
        SourceSection.Nowcast->uiText(R.string.ui_nowcast_zrazok_753057c8)
        SourceSection.Alerts->uiText(R.string.ui_vystrahy_d88f15ec)
    }
    AlertDialog(onDismissRequest=onDismiss,containerColor=EditorPanel,titleContentColor=EditorText,textContentColor=EditorText,title={
        Row(verticalAlignment=Alignment.CenterVertically) {
            if(section!=null) IconButton(onClick={section=null}) {Icon(PerunIcons.Back,uiText(R.string.ui_spat_000d5419),tint=EditorAccent)}
            Text(title,Modifier.weight(1f),maxLines=2);if(section==SourceSection.Alerts) SettingsInfo(uiText(R.string.alert_sources_note))
        }
    },text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())) {
            when(section) {
                null->{
                    if(location.current) CurrentLocationStatus()
                    SourceHubRow(uiText(R.string.ui_predpovedny_model_666f632c),weatherLabel(),weatherStatus==ModelStatus.Checking) {section=SourceSection.Weather}
                    HorizontalDivider(color=EditorEdge.copy(alpha=.6f))
                    SourceHubRow(uiText(R.string.ui_ensemble_model_b09a8627),ensembleLabel(),ensembleStatus==ModelStatus.Checking) {section=SourceSection.Ensemble}
                    HorizontalDivider(color=EditorEdge.copy(alpha=.6f))
                    SourceHubRow(uiText(R.string.ui_nowcast_zrazok_753057c8),nowcastLabel(),false) {section=SourceSection.Nowcast}
                    HorizontalDivider(color=EditorEdge.copy(alpha=.6f))
                    SourceHubRow(uiText(R.string.ui_vystrahy_d88f15ec),alertsLabel(),false) {section=SourceSection.Alerts}
                    note?.let {Text(it,Modifier.padding(top=10.dp),fontSize=12.sp,color=Color(0xFFFFB74D))}
                }
                SourceSection.Weather->LocationModelCatalogue.weather.forEach {m->
                    SourceChoiceRow(m.label,weather==m.id,weather==m.id && weatherStatus==ModelStatus.Checking) {note=null;weather=m.id;controller.check(m.id,false)}
                }
                SourceSection.Ensemble->LocationModelCatalogue.ensemble.forEach {m->
                    SourceChoiceRow(m.label,ensemble==m.id,ensemble==m.id && ensembleStatus==ModelStatus.Checking) {note=null;ensemble=m.id;controller.check(m.id,true)}
                }
                SourceSection.Nowcast->NowcastChoices.forEach {c->SourceChoiceRow(c.label,nowcast==c.id,false) {nowcast=c.id}}
                SourceSection.Alerts->{
                    val ids=alertSourceSet(alerts)
                    alertChoices().forEach {c->SourceCheckRow(c.label,c.id in ids,when(c.id) {"libre"->uiText(R.string.alert_source_libre_info);"kde"->uiText(R.string.alert_source_kde_info);else->null}) {alerts=alertSourceValue(if(c.id in ids) ids-c.id else ids+c.id)}}
                }
            }
        }
    },confirmButton={TextButton(enabled=valid,onClick={store.save(location,LocationSourcePreferences(weather,ensemble,alerts,nowcast));onDismiss()}) {Text(uiText(R.string.ui_ulozit_bf7cd927),color=if(valid) EditorAccent else EditorMuted)}},
    dismissButton={TextButton(onClick=onDismiss) {Text(uiText(R.string.ui_zrusit_520e257a),color=EditorMuted)}})
}
@Composable private fun SourceHubRow(title:String,value:String,busy:Boolean,onOpen:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).clickable(role=Role.Button,onClick=onOpen).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(title,color=EditorText,fontWeight=FontWeight.SemiBold)
            Text(value,color=EditorMuted,fontSize=12.sp,maxLines=1)
        }
        if(busy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=EditorAccent)
        Text("›",style=MaterialTheme.typography.headlineSmall,color=EditorAccent)
    }
}
@Composable private fun SourceCheckRow(label:String,checked:Boolean,info:String?,onToggle:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable(role=Role.Checkbox,onClick=onToggle),verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked=checked,onCheckedChange=null,colors=CheckboxDefaults.colors(checkedColor=EditorAccent,uncheckedColor=EditorMuted,checkmarkColor=EditorPanel))
        Text(label,Modifier.weight(1f).padding(start=8.dp),color=EditorText)
        if(info!=null) SettingsInfo(info)
    }
}
@Composable private fun SourceChoiceRow(label:String,selected:Boolean,busy:Boolean,onSelect:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.RadioButton,onClick=onSelect),verticalAlignment=Alignment.CenterVertically) {
        RadioButton(selected=selected,onClick=null,colors=RadioButtonDefaults.colors(selectedColor=EditorAccent,unselectedColor=EditorMuted))
        Text(label,Modifier.weight(1f).padding(start=8.dp),color=EditorText)
        if(busy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=EditorAccent)
    }
}

/** For the Current location: whether it may use the position in the background and whether it is precise, with the ways to change it. */
@Composable private fun CurrentLocationStatus() {
    val context=LocalContext.current
    val owner=androidx.compose.ui.platform.LocalLifecycleOwner.current
    var tick by remember {mutableIntStateOf(0)}
    DisposableEffect(owner) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    val background=remember(tick) {CurrentLocation.background(context)}
    val precise=remember(tick) {CurrentLocation.precise(context)}
    val ask=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {tick++}
    Column(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(uiText(if(background) R.string.loc_bg_on else R.string.loc_bg_off),Modifier.weight(1f),fontSize=13.sp,color=if(background) EditorText else Color(0xFFFFB74D))
            if(!background) TextButton(onClick={ask.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)}) {Text(uiText(R.string.loc_bg_allow),color=EditorAccent)}
        }
        if(!precise) Row(verticalAlignment=Alignment.CenterVertically) {
            Text(uiText(R.string.loc_approximate),Modifier.weight(1f),fontSize=13.sp,color=EditorMuted)
            TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",context.packageName,null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))}) {Text(uiText(R.string.loc_permissions),color=EditorAccent)}
        }
        HorizontalDivider(color=EditorEdge.copy(alpha=.6f))
    }
}
