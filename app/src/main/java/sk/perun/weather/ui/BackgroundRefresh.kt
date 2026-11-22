package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class BackgroundRefreshSettings(
    val intervalMinutes:Int,val scope:String,val wifiOnly:Boolean,val batteryFloor:Int,
    val allowWhileCharging:Boolean,val pauseInPowerSaver:Boolean,val lastSuccess:Long,val lastAttempt:Long,val lastSkipReason:String,
)
internal object BackgroundRefresh {
    const val PrefsName="perun_background"
    val Intervals=listOf(0,15,30,60,120,180,360,720,1440)
    val BatteryFloors=listOf(10,20,30,50)
    private const val PeriodicWork="perun-background-refresh"
    private const val OneTimeWork="perun-background-refresh-now"
    fun prefs(context:Context):SharedPreferences = context.applicationContext.getSharedPreferences(PrefsName,Context.MODE_PRIVATE)
    fun read(p:SharedPreferences)=BackgroundRefreshSettings(
        p.getInt("intervalMinutes",0),p.getString("scope","active") ?: "active",p.getBoolean("wifiOnly",false),p.getInt("batteryFloor",20),
        p.getBoolean("allowWhileCharging",true),p.getBoolean("pauseInPowerSaver",true),p.getLong("lastSuccess",0L),p.getLong("lastAttempt",0L),p.getString("lastSkipReason","") ?: "",
    )
    fun locationKey(l:Location):String = String.format(Locale.US,"%.4f,%.4f",l.latitude,l.longitude)
    /** Re-creates the periodic work from the stored settings; "never" cancels it. */
    fun schedule(context:Context) {
        val s=read(prefs(context));val wm=WorkManager.getInstance(context.applicationContext)
        // Notifications (current conditions, alerts, rules) can only work through background refreshes: with "Never" they fall back to 30 minutes.
        val minutes=if(s.intervalMinutes>0) s.intervalMinutes else if(Notifier.needsBackground(context) || PerunWidgets.any(context)) 30 else 0
        if(minutes<=0) { wm.cancelUniqueWork(PeriodicWork);return }
        val constraints=Constraints.Builder().setRequiredNetworkType(if(s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val request=PeriodicWorkRequestBuilder<RefreshWorker>(minutes.toLong(),TimeUnit.MINUTES).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(PeriodicWork,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    fun refreshNow(context:Context) {
        // "Refresh now" is a deliberate user action: it ignores the battery and power-saver pauses.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(OneTimeWork,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<RefreshWorker>().setInputData(workDataOf("manual" to true)).build())
    }
    fun locations(context:Context):List<Location> {
        val store=LocationStore(context);val all=store.load();val p=prefs(context)
        return when(read(p).scope) {
            "all"->all
            "custom"->{ val keys=p.getStringSet("customLocations",null) ?: emptySet();all.filter { locationKey(it) in keys } }
            else->listOfNotNull(all.getOrNull(store.loadSelected()) ?: all.firstOrNull())
        }
    }
    /** Null when a refresh may run, otherwise the reason shown in Settings. */
    fun skipReason(context:Context):String? {
        val s=read(prefs(context))
        val pm=context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if(s.pauseInPowerSaver && pm.isPowerSaveMode) return uiText(R.string.bg_skip_power_saver)
        val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED),Context.RECEIVER_NOT_EXPORTED) ?: return null
        val level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100)
        val plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0
        val percent=if(level>=0 && scale>0) level*100/scale else 100
        if(percent<s.batteryFloor && !(plugged && s.allowWhileCharging)) return uiText(R.string.bg_skip_battery,percent)
        return null
    }
    fun ignoringBatteryOptimizations(context:Context):Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
}

/** Fills the shared response cache for the selected locations; screens then open instantly from it. */
internal class RefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val context=applicationContext;val p=BackgroundRefresh.prefs(context)
        val now=System.currentTimeMillis()
        if(!inputData.getBoolean("manual",false)) BackgroundRefresh.skipReason(context)?.let { p.edit().putLong("lastAttempt",now).putString("lastSkipReason",it).apply();return Result.success() }
        val sources=LocationSourceStore(context)
        val store=LocationStore(context);val saved=store.load()
        val activeKey=(saved.getOrNull(store.loadSelected()) ?: saved.firstOrNull())?.let { BackgroundRefresh.locationKey(it) }
        val marine=context.getSharedPreferences("perun_marine",Context.MODE_PRIVATE).getBoolean("enabled",false)
        var failures=0
        NetCache.forceNetwork=true
        try {
            for(l in Notifier.workerLocations(context,BackgroundRefresh.locations(context))) {
                val src=sources.read(l)
                try {
                    val overview=loadOverview(l,src.alertSource,src.weatherModel)
                    val extra=loadOverviewExtra(l,src.weatherModel)
                    if(marine) loadMarine(l,2)
                    Notifier.onOverview(context,l,overview,extra,BackgroundRefresh.locationKey(l)==activeKey)
                } catch(e:CancellationException) { throw e } catch(e:Exception) { failures++ }
            }
        } finally { NetCache.forceNetwork=false }
        PerunWidgets.refresh(context)
        val done=System.currentTimeMillis()
        if(failures==0) p.edit().putLong("lastSuccess",done).putLong("lastAttempt",done).putString("lastSkipReason","").apply()
        else p.edit().putLong("lastAttempt",done).putString("lastSkipReason",uiText(R.string.bg_skip_failed,failures)).apply()
        return if(failures==0) Result.success() else if(runAttemptCount<3) Result.retry() else Result.failure()
    }
}

internal fun backgroundIntervalLabel(minutes:Int):String = when {
    minutes<=0->uiText(R.string.bg_never)
    minutes<60->uiText(R.string.bg_minutes,minutes)
    else->uiText(R.string.bg_hours,minutes/60)
}
private fun clock(ms:Long):String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(appPattern("d. M. HH:mm"),appLocale()))
@Composable internal fun rememberBackgroundRefresh():BackgroundRefreshSettings {
    val context=LocalContext.current
    val p=remember(context) { BackgroundRefresh.prefs(context) }
    var value by remember(p) { mutableStateOf(BackgroundRefresh.read(p)) }
    DisposableEffect(p) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> value=BackgroundRefresh.read(p) }
        p.registerOnSharedPreferenceChangeListener(listener)
        value=BackgroundRefresh.read(p)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
@Composable internal fun backgroundSummary(s:BackgroundRefreshSettings):String =
    if(s.intervalMinutes<=0) uiText(R.string.bg_summary_off) else uiText(R.string.bg_summary_on,backgroundIntervalLabel(s.intervalMinutes),s.batteryFloor)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BackgroundRefreshSettings() {
    val context=LocalContext.current
    val p=remember(context) { BackgroundRefresh.prefs(context) }
    val s=rememberBackgroundRefresh()
    val all=remember(context) { LocationStore(context).load() }
    val custom=p.getStringSet("customLocations",null) ?: emptySet()
    fun edit(block:SharedPreferences.Editor.()->Unit) { p.edit().apply(block).apply();BackgroundRefresh.schedule(context) }
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(uiText(R.string.bg_interval),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=muted)
        SettingsInfo(uiText(R.string.bg_interval_note))
    }
    PerunChips(BackgroundRefresh.Intervals.map { backgroundIntervalLabel(it) },BackgroundRefresh.Intervals.indexOf(s.intervalMinutes).coerceAtLeast(0),{ edit { putInt("intervalMinutes",BackgroundRefresh.Intervals[it]) } })
    Text(uiText(R.string.bg_locations),style=MaterialTheme.typography.labelLarge,color=muted)
    PerunChips(listOf(uiText(R.string.bg_scope_active),uiText(R.string.bg_scope_all),uiText(R.string.bg_scope_custom)),listOf("active","all","custom").indexOf(s.scope).coerceAtLeast(0),{ edit { putString("scope",listOf("active","all","custom")[it]) } })
    if(s.scope=="custom") Column {
        all.forEach { l ->
            val key=BackgroundRefresh.locationKey(l)
            Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=key in custom,onCheckedChange={ on -> val next=custom.toMutableSet();if(on) next.add(key) else next.remove(key);edit { putStringSet("customLocations",next) } })
                Text(l.name,Modifier.padding(start=8.dp))
            }
        }
    }
    Text(uiText(R.string.bg_network),style=MaterialTheme.typography.labelLarge,color=muted)
    PerunChips(listOf(uiText(R.string.bg_network_any),uiText(R.string.bg_network_wifi)),if(s.wifiOnly) 1 else 0,{ edit { putBoolean("wifiOnly",it==1) } })
    Text(uiText(R.string.bg_battery),style=MaterialTheme.typography.labelLarge,color=muted)
    PerunChips(BackgroundRefresh.BatteryFloors.map { "$it %" },BackgroundRefresh.BatteryFloors.indexOf(s.batteryFloor).coerceAtLeast(0),{ edit { putInt("batteryFloor",BackgroundRefresh.BatteryFloors[it]) } })
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) { Text(uiText(R.string.bg_allow_charging),Modifier.weight(1f));Switch(s.allowWhileCharging,{ edit { putBoolean("allowWhileCharging",it) } }) }
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) { Text(uiText(R.string.bg_pause_saver),Modifier.weight(1f));Switch(s.pauseInPowerSaver,{ edit { putBoolean("pauseInPowerSaver",it) } }) }
    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
    Text(uiText(R.string.bg_status),style=MaterialTheme.typography.labelLarge,color=muted)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(if(s.lastSuccess>0) uiText(R.string.bg_last_success,clock(s.lastSuccess)) else uiText(R.string.bg_no_success),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        SettingsInfo(uiText(R.string.bg_cache_note))
    }
    if(s.lastSkipReason.isNotBlank() && s.lastAttempt>0) Text(uiText(R.string.bg_last_skip,clock(s.lastAttempt),s.lastSkipReason),style=MaterialTheme.typography.bodySmall,color=Color(0xFFFFB74D))
    OutlinedButton(onClick={ BackgroundRefresh.refreshNow(context) },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.bg_refresh_now)) }
    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
    Text(uiText(R.string.bg_permissions),style=MaterialTheme.typography.labelLarge,color=muted)
    val ignoring=rememberOnResume { BackgroundRefresh.ignoringBatteryOptimizations(context) }
    PerunStatusRow(ignoring,if(ignoring) uiText(R.string.bg_optimization_off) else uiText(R.string.bg_optimization_on))
    if(!ignoring) OutlinedButton(onClick={ runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.bg_open_battery_settings)) }
    OutlinedButton(onClick={ runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.bg_open_system_settings)) }
}
