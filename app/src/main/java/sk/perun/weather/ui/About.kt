package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val RepoUrl="https://github.com/tothpsk/perun-weather-android"
private const val ProfileUrl="https://github.com/tothpsk"
private val BuildEpoch:Instant=Instant.parse("2026-01-01T00:00:00Z")

@Composable private fun AboutRow(label:String,value:String,url:String?=null) {
    val uri=LocalUriHandler.current
    Column(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalArrangement=Arrangement.Center) {
        Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(url==null) Text(value,style=MaterialTheme.typography.bodyMedium)
        else Text(value,Modifier.clickable { uri.openUri(url) }.padding(vertical=4.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,textDecoration=TextDecoration.Underline)
    }
}

/** Settings page About: version with build number, author, source link, licence and data-source attribution. */
@Composable internal fun AboutPane() {
    val context=LocalContext.current
    val info=remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName,0) }.getOrNull() }
    val name=info?.versionName ?: ""
    val build=info?.longVersionCode ?: 0L
    val built=if(build>100000L) DateTimeFormatter.ofPattern(appPattern("d. M. yyyy HH:mm"),appLocale()).format(BuildEpoch.plusSeconds(build*60).atZone(ZoneId.systemDefault())) else null
    var license by remember { mutableStateOf(false) }
    Text(stringResource(R.string.about_description),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
    AboutRow(stringResource(R.string.about_version),stringResource(R.string.about_version_value,name,build))
    if(built!=null) AboutRow(stringResource(R.string.about_built),built)
    AboutRow(stringResource(R.string.about_author),stringResource(R.string.about_author_name),ProfileUrl)
    AboutRow(stringResource(R.string.about_source),RepoUrl,RepoUrl)
    OutlinedButton(onClick={ license=true },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_license_button)) }
    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
    AttributionFooter()
    if(license) AlertDialog(
        onDismissRequest={ license=false },
        containerColor=Color(0xFF202833),titleContentColor=Color(0xFFE8EAF0),textContentColor=Color(0xFFE8EAF0),
        title={ Text(stringResource(R.string.about_license_title)) },
        text={ Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) { Text(stringResource(R.string.about_license_text),style=MaterialTheme.typography.bodySmall) } },
        confirmButton={ TextButton(onClick={ license=false }) { Text(stringResource(R.string.ui_zavriet_7c32fd0b),color=Color(0xFF4FC3F7)) } },
    )
}
