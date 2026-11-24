package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val MarkLevels = listOf("neutral", "yellow", "orange", "red")
private val MarkGrey = Color(0xFF5B6577)
private fun markRank(level: String) = AlertRank[level] ?: 0

// Official alerts of each place from the sources chosen for that place, without the local estimates, so that the list
// agrees with the Now and Alerts screens. Responses come through the same cache (5 minutes), two places at a time.
// A source that fails gives no alerts for that place, as on the Now screen; the result of each place is reported as it arrives.
internal suspend fun loadLocationAlerts(context: Context, places: List<Location>, onResult: (String, List<AlertItem>) -> Unit) = coroutineScope {
    val store = LocationSourceStore(context)
    val gate = Semaphore(2)
    places.forEach { place ->
        launch {
            val ids = alertSourceSet(store.read(place).alertSource) - "local"
            val items = if (ids.isEmpty()) emptyList() else gate.withPermit {
                try { withContext(Dispatchers.IO) { fetchOfficialAlerts(place, alertSourceValue(ids)) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { emptyList() }
            }
            onResult(BackgroundRefresh.locationKey(place), items)
        }
    }
}

// The same chip as on the Now screen without the text: the icon of the kind and the colour of the level of the first group,
// and "+N" for the other groups, in the colour of the highest level among them.
@Composable internal fun LocationAlertMark(items: List<AlertItem>) {
    if (items.isEmpty()) return
    val groups = remember(items) { groupAlertPresentation(items.sortedByDescending { markRank(it.level) }) }
    val first = groups.first()
    val level = first.items.maxByOrNull { markRank(it.level) }?.level ?: first.items.first().level
    val color = AlertColors[level] ?: MarkGrey
    val rest = groups.drop(1)
    val restRank = if (rest.isEmpty()) 0 else rest.maxOf { g -> g.items.maxOf { markRank(it.level) } }
    val moreColor = AlertColors[MarkLevels[restRank.coerceIn(0, 3)]] ?: MarkGrey
    val description = uiText(R.string.ui_vystraha_a0ff184b) + if (rest.isEmpty()) "" else " +" + rest.size
    Row(
        Modifier.padding(end = 4.dp).semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AlertGlyph(alertHazard(first.items.first().event + " " + first.label, first.items.first().awareness), color, 22.dp)
        if (rest.isNotEmpty()) Box(Modifier.border(1.dp, moreColor, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("+" + rest.size, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = moreColor)
        }
    }
}
