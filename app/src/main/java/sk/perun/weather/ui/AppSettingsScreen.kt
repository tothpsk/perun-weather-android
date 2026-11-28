package sk.perun.weather.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import sk.perun.weather.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate

private enum class SettingsPage(val titleRes:Int) {
    Background(R.string.settings_background),Language(R.string.settings_language),Units(R.string.settings_units_air),
    Overview(R.string.settings_overview),Forecast(R.string.settings_forecast),Notifications(R.string.settings_notifications),
    Widgets(R.string.settings_widgets),Backup(R.string.settings_backup),About(R.string.settings_about)
}

@Composable internal fun AppSettingsScreen(onClose:()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context) {context.getSharedPreferences("perun_forecast",Context.MODE_PRIVATE)}
    val s=rememberForecastPreferences()
    val air=rememberAirPreferences()
    val envelope=rememberLongEnvelopePreferences()
    val tiles=rememberOverviewTiles()
    val bgRefresh=rememberBackgroundRefresh()
    var pageId by rememberSaveable {mutableStateOf<String?>(null)}
    val page=SettingsPage.entries.firstOrNull {it.name==pageId}
    BackHandler {if(page!=null) pageId=null else onClose()}
    val scheme=darkColorScheme(primary=Color(0xFF4FC3F7),onPrimary=Color(0xFF10131A),background=Color(0xFF323E4F),onBackground=Color(0xFFE8EAF0),surface=Color(0xFF202833),onSurface=Color(0xFFE8EAF0),surfaceVariant=Color(0xFF323E4F),onSurfaceVariant=Color(0xFFBBC4D2),secondaryContainer=Color(0xFF254459),onSecondaryContainer=Color(0xFFBCECFF),outline=Color(0xFF4B586C))
    MaterialTheme(colorScheme=scheme,typography=MaterialTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            key(pageId) {
                Column(Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(page?.titleRes ?: R.string.settings_title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    if(page==null) {
                        OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,scheme.outline),colors=CardDefaults.outlinedCardColors(containerColor=scheme.surface,contentColor=scheme.onSurface)) {
                            SettingsPage.entries.forEachIndexed {index,item->
                                val summary=when(item) {
                                    SettingsPage.Background->backgroundSummary(bgRefresh)
                                    SettingsPage.Language->languageSummary()
                                    SettingsPage.Units->stringResource(R.string.settings_units_summary,s.units.temperature.symbol,s.units.wind.symbol,s.units.pressure.symbol,air.scale.label)
                                    SettingsPage.Overview->stringResource(R.string.tiles_summary_v2,tiles.count {it!=OverviewTileId.Alerts},OverviewTileId.entries.size-1)
                                    SettingsPage.Forecast->stringResource(R.string.settings_envelope_summary,envelope.lower,envelope.upper)
                                    SettingsPage.Notifications->notificationSummary(rememberNotificationSettings())
                                    SettingsPage.Widgets->widgetSummary()
                                    SettingsPage.Backup->stringResource(R.string.settings_backup_summary)
                                    SettingsPage.About->stringResource(R.string.settings_about_summary)
                                }
                                SettingsHubRow(item,summary) {pageId=item.name}
                                if(index<SettingsPage.entries.lastIndex) HorizontalDivider(Modifier.padding(horizontal=16.dp),color=scheme.outline.copy(alpha=.5f))
                            }
                        }
                    } else when(page) {
                        SettingsPage.Units->{
                            SettingsCategory(stringResource(R.string.settings_units)) {
                                SettingsChoices(stringResource(R.string.settings_temperature),TemperatureUnit.entries.map {it.symbol},s.units.temperature.ordinal) {prefs.edit().putString("temperatureUnit",TemperatureUnit.entries[it].name).apply()}
                                SettingsChoices(stringResource(R.string.settings_wind),WindSpeedUnit.entries.map {it.symbol},s.units.wind.ordinal) {prefs.edit().putString("windUnit",WindSpeedUnit.entries[it].name).apply()}
                                SettingsChoices(stringResource(R.string.settings_pressure),PressureUnit.entries.map {it.symbol},s.units.pressure.ordinal) {prefs.edit().putString("pressureUnit",PressureUnit.entries[it].name).apply()}
                                SettingsChoices(stringResource(R.string.settings_precipitation),listOf(stringResource(R.string.settings_precipitation_metric),stringResource(R.string.settings_precipitation_imperial)),s.units.precipitation.ordinal) {prefs.edit().putString("precipitationUnit",PrecipitationUnit.entries[it].name).apply()}
                                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
                                Text(stringResource(R.string.settings_air_pollen),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                                AirQualitySettings()
                            }
                        }
                        SettingsPage.Overview->SettingsCategory("") {OverviewTileSettings()}
                        SettingsPage.Forecast->SettingsCategory(stringResource(R.string.settings_envelope_percentiles)) {LongEnvelopeSettings()}
                        SettingsPage.Background->SettingsCategory(stringResource(R.string.settings_background)) {BackgroundRefreshSettings()}
                        SettingsPage.Language->SettingsCategory(stringResource(R.string.settings_language)) {LanguageSettings()}
                        SettingsPage.Notifications->SettingsCategory(stringResource(R.string.settings_notifications)) {NotificationSettingsPane()}
                        SettingsPage.Widgets->SettingsCategory(stringResource(R.string.settings_widgets)) {WidgetSettingsPane()}
                        SettingsPage.Backup->BackupSettings()
                        SettingsPage.About->SettingsCategory(stringResource(R.string.settings_about)) {AboutPane()}
                    }
                }
            }
        }
    }
}

@Composable private fun SettingsHubRow(page:SettingsPage,summary:String,onOpen:()->Unit) {
    val title=stringResource(page.titleRes)
    Row(Modifier.fillMaxWidth().heightIn(min=88.dp).clickable(role=Role.Button,onClickLabel=stringResource(R.string.settings_open_section,title),onClick=onOpen).padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        SettingsGlyph(page)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun SettingsGlyph(page:SettingsPage) {
    GlyphIcon(when(page) {
        SettingsPage.Background->Glyph.SetBackground;SettingsPage.Language->Glyph.SetLanguage;SettingsPage.Units->Glyph.SetUnits
        SettingsPage.Overview->Glyph.SetOverview;SettingsPage.Forecast->Glyph.SetForecast;SettingsPage.Notifications->Glyph.SetNotifications
        SettingsPage.Widgets->Glyph.SetWidgets;SettingsPage.About->Glyph.SetAbout;SettingsPage.Backup->Glyph.SetBackup
    },MaterialTheme.colorScheme.primary,24.dp)
}

// Small (i) button: the explanation opens in a dialog instead of occupying space under the control.
@Composable internal fun SettingsInfo(text:String) {
    var open by remember {mutableStateOf(false)}
    val color=MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick={open=true},modifier=Modifier.size(36.dp)) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(color,radius=size.minDimension/2-.65.dp.toPx(),style=Stroke(1.3.dp.toPx()))
            drawLine(color,Offset(size.width/2,size.height*.42f),Offset(size.width/2,size.height*.74f),1.6.dp.toPx(),StrokeCap.Round)
            drawCircle(color,1.1.dp.toPx(),Offset(size.width/2,size.height*.28f))
        }
    }
    if(open) AlertDialog(containerColor=Color(0xFF202833),titleContentColor=Color(0xFFE8EAF0),textContentColor=Color(0xFFE8EAF0),onDismissRequest={open=false},text={Text(text,style=MaterialTheme.typography.bodyMedium)},confirmButton={TextButton(onClick={open=false}) {Text(stringResource(R.string.ui_zavriet_7c32fd0b))}})
}
/** Re-evaluates a system state (permissions, battery exemption) every time the screen returns to the foreground. */
@Composable internal fun <T> rememberOnResume(compute:()->T):T {
    val owner=LocalLifecycleOwner.current
    var value by remember {mutableStateOf(compute())}
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME) value=compute()}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    return value
}
@Composable private fun SettingsCategory(title:String,content:@Composable ColumnScope.()->Unit) {
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {if(title.isNotEmpty()) Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);content()}
    }
}
// Backup covers every "perun_*" preference store; keys that look like credentials are never exported, nor are the caches
// and the state of the app (downloaded warnings, model availability, the last fix of the Current location, the times of the
// background refresh, the shown day of a widget). The widgets are also kept by kind, see WidgetBackup.
private const val BackupVersion=1
private val BackupSkip=listOf("apikey","api_key","token","secret","password")
private val BackupSkipStores=setOf("perun_widget_alerts","perun_model_availability","perun_current_location",WidgetBackup.PendingStore)
private val BackupSkipKeys=mapOf("perun_background" to setOf("lastAttempt","lastSuccess","lastSkipReason"),"perun_notifications" to setOf("sentAlerts"))
internal fun backupKeeps(store:String,key:String):Boolean =
    store.startsWith("perun_") && store !in BackupSkipStores && key !in BackupSkipKeys[store].orEmpty() && !WidgetBackup.isState(store,key) && BackupSkip.none {key.lowercase().contains(it)}
private fun backupStoreNames(context:Context):List<String> =
    (File(context.applicationInfo.dataDir,"shared_prefs").listFiles() ?: emptyArray()).map {it.nameWithoutExtension}.filter {it.startsWith("perun_") && it !in BackupSkipStores}.sorted()
private fun backupTyped(v:Any?):JSONObject? {
    val e=JSONObject()
    when(v) {
        is Boolean->e.put("t","b").put("v",v)
        is Int->e.put("t","i").put("v",v)
        is Long->e.put("t","l").put("v",v)
        is Float->e.put("t","f").put("v",v.toDouble())
        is String->e.put("t","s").put("v",v)
        is Set<*>->e.put("t","ss").put("v",JSONArray(v.map {it.toString()}))
        else->return null
    }
    return e
}
private fun backupValue(e:JSONObject):Any? = when(e.optString("t")) {
    "b"->e.getBoolean("v");"i"->e.getInt("v");"l"->e.getLong("v");"f"->e.getDouble("v").toFloat();"s"->e.getString("v")
    "ss"->e.getJSONArray("v").let {a->(0 until a.length()).map {a.getString(it)}.toSet()}
    else->null
}
internal fun exportConfiguration(context:Context):String {
    val stores=JSONObject()
    backupStoreNames(context).forEach {name->
        val o=JSONObject()
        context.getSharedPreferences(name,Context.MODE_PRIVATE).all.forEach {(k,v)->
            if(!backupKeeps(name,k)) return@forEach
            backupTyped(v)?.let {o.put(k,it)}
        }
        if(o.length()>0) stores.put(name,o)
    }
    return JSONObject().put("app","perun-weather").put("version",BackupVersion).put("exported",Instant.now().toString()).put("stores",stores)
        .put("widgets",WidgetBackup.export(context,::backupTyped)).toString(2)
}
internal data class BackupPreview(val exported:String,val stores:Map<String,Int>,val widgets:Int=0)
internal fun previewConfiguration(raw:String):BackupPreview {
    val root=JSONObject(raw)
    require(root.optString("app")=="perun-weather" && root.optInt("version") in 1..BackupVersion)
    val stores=root.getJSONObject("stores")
    val counts=LinkedHashMap<String,Int>()
    stores.keys().forEach {name->if(name.startsWith("perun_") && name !in BackupSkipStores) counts[name]=stores.getJSONObject(name).length()}
    return BackupPreview(root.optString("exported"),counts,root.optJSONArray("widgets")?.length() ?: 0)
}
internal fun importConfiguration(context:Context,raw:String,replace:Boolean) {
    val root=JSONObject(raw)
    val stores=root.getJSONObject("stores")
    stores.keys().forEach {name->
        // Older backups may still hold the caches; they are not restored.
        if(!name.startsWith("perun_") || name in BackupSkipStores) return@forEach
        val o=stores.getJSONObject(name);val ed=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit()
        if(replace) ed.clear()
        o.keys().forEach {k->
            if(!backupKeeps(name,k)) return@forEach
            when(val v=backupValue(o.getJSONObject(k))) {
                is Boolean->ed.putBoolean(k,v)
                is Int->ed.putInt(k,v)
                is Long->ed.putLong(k,v)
                is Float->ed.putFloat(k,v)
                is String->ed.putString(k,v)
                is Set<*>->ed.putStringSet(k,v.map {it.toString()}.toSet())
            }
        }
        ed.commit()
    }
    WidgetBackup.keep(context,root.optJSONArray("widgets"))
}
internal fun backupReadValue(e:JSONObject):Any? = backupValue(e)
private fun restartApp(context:Context) {
    val intent=context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)}
    if(intent!=null) context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
@Composable private fun backupStoreLabel(name:String):String = when(name) {
    "perun_locations"->stringResource(R.string.backup_store_locations)
    "perun_forecast"->stringResource(R.string.backup_store_forecast)
    "perun_marine"->stringResource(R.string.backup_store_marine)
    "perun_location_preferences"->stringResource(R.string.backup_store_location_sources)
    "perun_long_forecast"->stringResource(R.string.backup_store_long)
    "perun_air_detail"->stringResource(R.string.backup_store_air)
    "perun_notifications"->stringResource(R.string.backup_store_notifications)
    "perun_background"->stringResource(R.string.backup_store_background)
    "perun_widgets","perun_widget_day","perun_widget_air","perun_widget_sky"->stringResource(R.string.backup_store_widgets)
    else->name.removePrefix("perun_")
}
@Composable private fun BackupSettings() {
    val context=LocalContext.current
    var message by remember {mutableStateOf<String?>(null)}
    var pending by remember {mutableStateOf<Pair<String,BackupPreview>?>(null)}
    val stamp=remember {LocalDate.now().toString()}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->
        if(uri!=null) message=runCatching {
            context.contentResolver.openOutputStream(uri)!!.use {it.write(exportConfiguration(context).toByteArray(Charsets.UTF_8))}
            context.getString(R.string.backup_exported)
        }.getOrElse {context.getString(R.string.backup_failed)}
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) runCatching {
            val raw=context.contentResolver.openInputStream(uri)!!.use {it.readBytes().toString(Charsets.UTF_8)}
            pending=raw to previewConfiguration(raw)
        }.onFailure {message=context.getString(R.string.backup_invalid)}
    }
    SettingsCategory(stringResource(R.string.settings_backup)) {
        Text(stringResource(R.string.backup_description),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={exporter.launch("perun-config-$stamp.json")},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.backup_export))}
        OutlinedButton(onClick={importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.backup_import))}
        message?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
    }
    pending?.let {(raw,preview)->
        AlertDialog(onDismissRequest={pending=null},title={Text(stringResource(R.string.backup_preview_title))},text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(preview.exported.isNotBlank()) Text(stringResource(R.string.backup_preview_exported,preview.exported),style=MaterialTheme.typography.bodySmall)
                // The four stores of the widgets share one line.
                val labelled=preview.stores.map {(name,count)->backupStoreLabel(name) to count}
                labelled.groupBy({it.first},{it.second}).forEach {(label,counts)->Text(label+" · "+stringResource(R.string.backup_items,counts.sum()),style=MaterialTheme.typography.bodySmall)}
                if(preview.widgets>0) Text(stringResource(R.string.backup_widgets_note,preview.widgets),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.backup_replace_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },confirmButton={TextButton(onClick={importConfiguration(context,raw,true);pending=null;restartApp(context)}) {Text(stringResource(R.string.backup_replace))}},
        dismissButton={Row {TextButton(onClick={importConfiguration(context,raw,false);pending=null;restartApp(context)}) {Text(stringResource(R.string.backup_merge))};TextButton(onClick={pending=null}) {Text(stringResource(R.string.ui_zrusit_520e257a))}}})
    }
}
@Composable private fun SettingsChoices(title:String,labels:List<String>,selected:Int,enabled:Boolean=true,pick:(Int)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);PerunChips(labels,selected,pick,enabled=enabled)}
}
