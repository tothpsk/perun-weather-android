package sk.perun.weather.ui

import org.junit.Assert.*
import org.junit.Test
import sk.perun.weather.R

class AppStringsTest : ResourceStringsTestBase() {
    @Test fun defaultResourceIsSlovak() {
        assertEquals("Nastavenia", uiText(R.string.settings_title))
    }
    @Test fun indexedArgumentsKeepTheirOrder() {
        assertEquals("Zrážky · mm (vodný ekvivalent). Sneh pri kurzore · cm.",
            uiText(R.string.ui_zrazky_value_vodny_ekvivalent_sneh_pri_kurzore_value_c076cdec, "mm", "cm"))
    }
    @Test fun literalPercentSignsAreNotFormattedWithoutArguments() {
        assertEquals("Celková modelová oblačnosť v percentách: 0 % je jasno, 100 % zamračené.",
            uiText(R.string.ui_celkova_modelova_oblacnost_v_percentach_0_je_jasno_100_zam_fe810d8f))
    }
    @Test fun nullArgumentsKeepKotlinTemplateSemantics() {
        assertEquals("Zrážky · null (vodný ekvivalent). Sneh pri kurzore · cm.",
            uiText(R.string.ui_zrazky_value_vodny_ekvivalent_sneh_pri_kurzore_value_c076cdec, null, "cm"))
    }
    @Test fun slovakModelCountsUseOneFewOther() {
        listOf(0 to "0 modelov", 1 to "1 model", 2 to "2 modely", 4 to "4 modely", 5 to "5 modelov", 21 to "21 modelov").forEach { (n, prefix) ->
            assertTrue(uiQuantityText(R.plurals.model_verification_failed, n, n).startsWith(prefix + " sa"))
        }
    }
    @Test fun alertCountsKeepTheRegionArgument() {
        listOf(1 to "1 výstrahu", 2 to "2 výstrahy", 5 to "5 výstrah").forEach { (n, text) ->
            assertEquals("Kanál obsahuje $text, žiadna nezodpovedá oblasti „Bratislava“.",
                uiQuantityText(R.plurals.unmatched_alert_count, n, n, "Bratislava"))
        }
    }
}
