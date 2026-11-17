package sk.perun.weather.ui

import sk.perun.weather.R

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Locale

/** Language of the app: the first language of the system or per-app list that the app supports, Slovak otherwise. */
internal object AppLocale {
    val Supported=listOf("sk","en")
    @Volatile private var current="sk"
    @Volatile private var wanted=listOf("sk-SK")
    fun install(resources:Resources) {
        val list=resources.configuration.locales
        current=(0 until list.size()).map { list.get(it).language }.firstOrNull { it in Supported } ?: "sk"
        wanted=(0 until list.size()).map { list.get(it).toLanguageTag() }.ifEmpty { listOf("sk-SK") }
    }
    fun language():String=current
    /** Languages of the system or per-app list in their order, also those the app has no texts for. The texts of alerts follow them. */
    fun alertLanguages():List<String> = wanted
    fun locale():Locale=if(current=="en") Locale.forLanguageTag("en-GB") else Locale.forLanguageTag("sk-SK")
    /** Empty string means follow the system. */
    fun chosen(context:Context):String {
        val list=context.getSystemService(LocaleManager::class.java).getApplicationLocales()
        return if(list.isEmpty()) "" else list.get(0).language
    }
    /** Stores the per-app language and restarts the process, because some lists are built once at start. */
    fun choose(context:Context,tag:String) {
        context.getSystemService(LocaleManager::class.java).setApplicationLocales(if(tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag))
        val app=context.applicationContext
        val intent=app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        Handler(Looper.getMainLooper()).postDelayed({ if(intent!=null) app.startActivity(intent);Runtime.getRuntime().exit(0) },300)
    }
}

internal fun appLocale():Locale=AppLocale.locale()

private val EnPatterns=mapOf(
    "d. M." to "d MMM","d.M." to "d MMM","d. M. HH:mm" to "d MMM HH:mm","d. M. yyyy HH:mm" to "d MMM yyyy HH:mm",
    "EEEE d. MMMM · HH:mm" to "EEEE d MMMM · HH:mm","EEE d." to "EEE d","d. MMMM · HH:mm" to "d MMMM · HH:mm","EEEE d. M. yyyy" to "EEEE d MMM yyyy",
)
/** Date patterns are written in the Slovak form; English uses the day-month-name form. */
internal fun appPattern(slovak:String):String = if(AppLocale.language()=="en") EnPatterns[slovak] ?: slovak else slovak

@Composable internal fun LanguageSettings() {
    val context=LocalContext.current
    val tags=listOf("","sk","en")
    val labels=listOf(stringResource(R.string.lang_system),stringResource(R.string.lang_sk),stringResource(R.string.lang_en))
    var current by remember { mutableStateOf(AppLocale.chosen(context)) }
    Text(stringResource(R.string.lang_choose),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    PerunChips(labels,tags.indexOf(current).coerceAtLeast(0),{ i -> if(tags[i]!=current) { current=tags[i];AppLocale.choose(context,tags[i]) } })
    Text(stringResource(R.string.lang_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable internal fun languageSummary():String {
    val context=LocalContext.current
    return when(AppLocale.chosen(context)) {
        "sk"->stringResource(R.string.lang_sk)
        "en"->stringResource(R.string.lang_en)
        else->stringResource(R.string.lang_system)
    }
}
