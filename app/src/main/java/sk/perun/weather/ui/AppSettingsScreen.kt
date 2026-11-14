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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
                                    SettingsPage.Overview->stringResource(R.string.tiles_summary_v2,tiles.size,OverviewTileId.entries.size)
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
                            }
                            SettingsCategory(stringResource(R.string.settings_air_pollen)) {AirQualitySettings()}
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
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(26.dp)) {
        scale(size.width/24f,size.height/24f,Offset.Zero) {
            val stroke=Stroke(1.6f,cap=StrokeCap.Round)
            fun line(a:Float,b:Float,c:Float,d:Float)=drawLine(color,Offset(a,b),Offset(c,d),1.6f,StrokeCap.Round)
            when(page) {
                SettingsPage.Background->{drawCircle(color,9f,Offset(12f,12f),style=stroke);line(12f,6f,12f,12f);line(12f,12f,17f,15f)}
                SettingsPage.Language->{drawCircle(color,9f,Offset(12f,12f),style=stroke);line(3f,12f,21f,12f);line(12f,3f,12f,21f);drawOval(color,topLeft=Offset(7.5f,3f),size=Size(9f,18f),style=stroke)}
                SettingsPage.Units->{listOf(6f,12f,18f).forEachIndexed {i,y->line(3f,y,21f,y);drawCircle(color,2f,Offset(if(i==1) 16f else 8f,y))}}
                SettingsPage.Overview->{for(x in listOf(3f,13f)) for(y in listOf(3f,13f)) drawRoundRect(color,Offset(x,y),Size(8f,8f),CornerRadius(2f),style=stroke)}
                SettingsPage.Forecast->{val a=Path().apply {moveTo(3f,12f);cubicTo(9f,11f,14f,8f,21f,4f)};val b=Path().apply {moveTo(3f,12f);cubicTo(9f,13f,14f,16f,21f,20f)};drawPath(a,color,style=stroke);drawPath(b,color,style=stroke);line(3f,12f,21f,12f);drawCircle(color,1.6f,Offset(3f,12f))}
                SettingsPage.Notifications->{val p=Path().apply {moveTo(4f,18f);lineTo(7f,14f);lineTo(7f,9f);cubicTo(7f,2f,17f,2f,17f,9f);lineTo(17f,14f);lineTo(20f,18f);close()};drawPath(p,color,style=stroke);drawCircle(color,1.5f,Offset(12f,21f))}
                SettingsPage.Widgets->{drawRoundRect(color,Offset(3f,3f),Size(18f,7f),CornerRadius(2f),style=stroke);drawRoundRect(color,Offset(3f,13f),Size(8f,8f),CornerRadius(2f),style=stroke);drawRoundRect(color,Offset(13f,13f),Size(8f,8f),CornerRadius(2f),style=stroke)}
                SettingsPage.About->{drawCircle(color,9f,Offset(12f,12f),style=stroke);line(12f,11f,12f,17f);drawCircle(color,1f,Offset(12f,7.5f))}
                SettingsPage.Backup->{line(4f,15f,4f,21f);line(4f,21f,20f,21f);line(20f,21f,20f,15f);line(12f,3f,12f,16f);line(7f,11f,12f,16f);line(12f,16f,17f,11f)}
            }
        }
    }
}

@Composable private fun SettingsNotice(title:String,description:String) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Small (i) button: the explanation opens in a dialog instead of occupying space under the control.
@Composable internal fun SettingsInfo(text:String) {
    var open by remember {mutableStateOf(false)}
    val color=MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick={open=true},modifier=Modifier.size(36.dp)) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(color,style=Stroke(1.4.dp.toPx()))
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
// Backup covers every "perun_*" preference store; keys that look like credentials are never exported.
private const val BackupVersion=1
private val BackupSkip=listOf("apikey","api_key","token","secret","password")
private fun backupStoreNames(context:Context):List<String> =
    (File(context.applicationInfo.dataDir,"shared_prefs").listFiles() ?: emptyArray()).map {it.nameWithoutExtension}.filter {it.startsWith("perun_")}.sorted()
internal fun exportConfiguration(context:Context):String {
    val stores=JSONObject()
    backupStoreNames(context).forEach {name->
        val o=JSONObject()
        context.getSharedPreferences(name,Context.MODE_PRIVATE).all.forEach {(k,v)->
            if(BackupSkip.any {k.lowercase().contains(it)}) return@forEach
            val e=JSONObject()
            when(v) {
                is Boolean->e.put("t","b").put("v",v)
                is Int->e.put("t","i").put("v",v)
                is Long->e.put("t","l").put("v",v)
                is Float->e.put("t","f").put("v",v.toDouble())
                is String->e.put("t","s").put("v",v)
                is Set<*>->e.put("t","ss").put("v",JSONArray(v.map {it.toString()}))
                else->return@forEach
            }
            o.put(k,e)
        }
        stores.put(name,o)
    }
    return JSONObject().put("app","perun-weather").put("version",BackupVersion).put("exported",Instant.now().toString()).put("stores",stores).toString(2)
}
internal data class BackupPreview(val exported:String,val stores:Map<String,Int>)
internal fun previewConfiguration(raw:String):BackupPreview {
    val root=JSONObject(raw)
    require(root.optString("app")=="perun-weather" && root.optInt("version") in 1..BackupVersion)
    val stores=root.getJSONObject("stores")
    val counts=LinkedHashMap<String,Int>()
    stores.keys().forEach {name->counts[name]=stores.getJSONObject(name).length()}
    return BackupPreview(root.optString("exported"),counts)
}
internal fun importConfiguration(context:Context,raw:String,replace:Boolean) {
    val stores=JSONObject(raw).getJSONObject("stores")
    stores.keys().forEach {name->
        if(!name.startsWith("perun_")) return@forEach
        val o=stores.getJSONObject(name);val ed=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit()
        if(replace) ed.clear()
        o.keys().forEach {k->
            val e=o.getJSONObject(k)
            when(e.getString("t")) {
                "b"->ed.putBoolean(k,e.getBoolean("v"))
                "i"->ed.putInt(k,e.getInt("v"))
                "l"->ed.putLong(k,e.getLong("v"))
                "f"->ed.putFloat(k,e.getDouble("v").toFloat())
                "s"->ed.putString(k,e.getString("v"))
                "ss"->{val a=e.getJSONArray("v");ed.putStringSet(k,(0 until a.length()).map {a.getString(it)}.toSet())}
            }
        }
        ed.commit()
    }
}
private fun restartApp(context:Context) {
    val intent=context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)}
    if(intent!=null) context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
@Composable private fun backupStoreLabel(name:String):String = when(name) {
    "perun_locations"->stringResource(R.string.backup_store_locations)
    "perun_forecast"->stringResource(R.string.backup_store_forecast)
    "perun_marine"->stringResource(R.string.backup_store_marine)
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
                preview.stores.forEach {(name,count)->Text(backupStoreLabel(name)+" · "+stringResource(R.string.backup_items,count),style=MaterialTheme.typography.bodySmall)}
                Text(stringResource(R.string.backup_replace_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },confirmButton={TextButton(onClick={importConfiguration(context,raw,true);pending=null;restartApp(context)}) {Text(stringResource(R.string.backup_replace))}},
        dismissButton={Row {TextButton(onClick={importConfiguration(context,raw,false);pending=null;restartApp(context)}) {Text(stringResource(R.string.backup_merge))};TextButton(onClick={pending=null}) {Text(stringResource(R.string.ui_zrusit_520e257a))}}})
    }
}
@Composable private fun SettingsChoices(title:String,labels:List<String>,selected:Int,enabled:Boolean=true,pick:(Int)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);PerunChips(labels,selected,pick,enabled=enabled)}
}
