package sk.perun.weather

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import sk.perun.weather.ui.PerunApp
import sk.perun.weather.ui.publishAppShortcuts
import sk.perun.weather.ui.theme.PerunTheme

class MainActivity : ComponentActivity() {
    private var notificationIntent by mutableStateOf<Intent?>(null)
    private var notificationToken by mutableLongStateOf(0L)
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationIntent=intent
        notificationToken++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationIntent=intent
        publishAppShortcuts(this)
        enableEdgeToEdge()
        setContent {
            PerunTheme { PerunApp(notificationIntent?.getStringExtra("perun_route"),notificationIntent?.getStringExtra("perun_location"),notificationToken,notificationIntent?.getStringExtra("perun_alert")) }
        }
    }
}
