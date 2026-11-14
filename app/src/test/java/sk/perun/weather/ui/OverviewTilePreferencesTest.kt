package sk.perun.weather.ui

import org.junit.Assert.*
import org.junit.Test

class OverviewTilePreferencesTest : ResourceStringsTestBase() {
    @Test fun defaultsShowAll() { assertEquals(OverviewTileId.entries.toSet(),overviewTileSelection(null)) }
    @Test fun emptySelectionStaysEmpty() { assertTrue(overviewTileSelection(emptySet()).isEmpty()) }
    @Test fun explicitKeysAreNotTranslatedTitles() { assertEquals(setOf(OverviewTileId.Wind,OverviewTileId.Sun),overviewTileSelection(setOf("wind","sun"))) }
    @Test fun unknownKeysDoNotEnableTiles() { assertEquals(setOf(OverviewTileId.Uv),overviewTileSelection(setOf("uv","unknown"))) }
    @Test fun identifiersAreUnique() { assertEquals(OverviewTileId.entries.size,OverviewTileId.entries.map { it.key }.toSet().size) }
}
