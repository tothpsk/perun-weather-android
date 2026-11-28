package sk.perun.weather.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScopeTest {
    @Test fun settingsAreKept() {
        assertTrue(backupKeeps("perun_locations","locations"))
        assertTrue(backupKeeps("perun_widgets","bg_17"))
        assertTrue(backupKeeps("perun_widget_day","rows_17"))
        assertTrue(backupKeeps("perun_notifications","rules"))
        assertTrue(backupKeeps("perun_background","intervalMinutes"))
    }

    @Test fun cachesAndStateAreLeftOut() {
        assertFalse(backupKeeps("perun_widget_alerts","gps"))
        assertFalse(backupKeeps("perun_model_availability","x"))
        assertFalse(backupKeeps("perun_current_location","fixTime"))
        assertFalse(backupKeeps(WidgetBackup.PendingStore,"pending"))
        assertFalse(backupKeeps("perun_background","lastSuccess"))
        assertFalse(backupKeeps("perun_notifications","sentAlerts"))
        assertFalse(backupKeeps("perun_widget_day","date_17"))
        assertFalse(backupKeeps("perun_widget_day","at_17"))
        assertFalse(backupKeeps("perun_forecast","owm_api_key"))
        assertFalse(backupKeeps("other","x"))
    }

    @Test fun widgetKindsHaveTheirStores() {
        assertTrue(WidgetBackup.storeOf("CompactWidget")=="perun_widgets")
        assertTrue(WidgetBackup.storeOf("DayHoursWidget")=="perun_widget_day")
        assertTrue(WidgetBackup.storeOf("SkyWidget")=="perun_widget_sky")
    }
}
