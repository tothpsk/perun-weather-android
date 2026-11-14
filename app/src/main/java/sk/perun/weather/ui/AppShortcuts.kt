package sk.perun.weather.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import sk.perun.weather.MainActivity
import sk.perun.weather.R

// Long press on the launcher icon offers the four main screens.
// Published on every start of the app, so the labels follow the app language.
internal fun publishAppShortcuts(context: Context) {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return
    val items = listOf(
        Tab.Overview to R.drawable.ic_shortcut_now,
        Tab.Forecast to R.drawable.ic_shortcut_forecast,
        Tab.Maps to R.drawable.ic_shortcut_maps,
        Tab.Alerts to R.drawable.ic_shortcut_alerts,
    )
    val shortcuts = items.mapIndexed { rank, (tab, icon) ->
        ShortcutInfo.Builder(context, "tab_" + tab.route)
            .setShortLabel(tab.label)
            .setRank(rank)
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("perun_route", tab.route)
            )
            .build()
    }
    runCatching { manager.dynamicShortcuts = shortcuts }
}
