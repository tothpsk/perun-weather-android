package sk.perun.weather

import android.app.Application
import android.content.Context
import sk.perun.weather.ui.AppLocale
import sk.perun.weather.ui.AppStrings
import sk.perun.weather.ui.BackgroundRefresh
import sk.perun.weather.ui.NetCache
import sk.perun.weather.ui.Notifier

class PerunApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        AppStrings.install(resources)
        AppLocale.install(resources)
    }

    override fun onCreate() {
        super.onCreate()
        NetCache.install(cacheDir)
        Notifier.ensureChannels(this)
        // Re-applies the stored interval and constraints; "never" cancels the periodic work.
        BackgroundRefresh.schedule(this)
        Notifier.scheduleSummaries(this)
    }
}
