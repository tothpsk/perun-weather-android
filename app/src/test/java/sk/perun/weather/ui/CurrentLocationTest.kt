package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationTest {
    private val card = Location("Petržalka", "Slovakia", 48.123, 17.110, countryCode = "SK", current = true)

    @Test fun theKeysOfTheCurrentLocationDoNotDependOnThePosition() {
        val moved = card.copy(latitude = 45.514, longitude = 13.590)
        assertEquals(CurrentLocation.Key, BackgroundRefresh.locationKey(card))
        assertEquals(BackgroundRefresh.locationKey(card), BackgroundRefresh.locationKey(moved))
        assertEquals(locationSourceKey(card), locationSourceKey(moved))
        // Caches of the position itself still change with it.
        assertNotEquals(BackgroundRefresh.positionKey(card), BackgroundRefresh.positionKey(moved))
        assertNotEquals(positionSourceKey(card), positionSourceKey(moved))
        // A fixed place keeps its key from the coordinates.
        val fixed = card.copy(current = false)
        assertEquals("48.1230,17.1100", BackgroundRefresh.locationKey(fixed))
        assertEquals(positionSourceKey(fixed), locationSourceKey(fixed))
    }

    @Test fun distanceAndRounding() {
        assertEquals(55.6, distanceKm(48.0, 17.0, 48.5, 17.0), 0.2)
        assertEquals(48.123, roundPosition(48.12349), 1e-9)
    }

    @Test fun smallMovesKeepThePositionAndTheName() {
        val step = currentMove(card, 48.123, 17.110, 48.124, 17.111)
        assertFalse(step.move)
        assertFalse(step.rename)
    }

    @Test fun theNameIsLookedUpAfterMoreThanTwoKilometres() {
        // About 1 km: the position moves, the name stays.
        val near = currentMove(card, 48.123, 17.110, 48.132, 17.110)
        assertTrue(near.move)
        assertFalse(near.rename)
        // About 2.7 km from the place of the last name.
        val far = currentMove(card, 48.123, 17.110, 48.147, 17.110)
        assertTrue(far.rename)
        // No country yet, or no place of the last name: the name is looked up.
        assertTrue(currentMove(card.copy(countryCode = ""), 48.123, 17.110, 48.123, 17.110).rename)
        assertTrue(currentMove(card, null, null, 48.123, 17.110).rename)
    }

    @Test fun theCurrentLocationComesFirstInPickersAndIsMarkedInNotifications() {
        val a = Location("Wien", "Austria", 48.2, 16.37)
        val b = Location("Bratislava", "Slovakia", 48.15, 17.11)
        val gps = card.copy(name = "Bratislava")
        assertEquals(listOf(gps, a, b), pickerLocations(listOf(a, b, gps)))
        assertEquals("Bratislava \u2316", notifyPlace(gps))
        assertEquals("Bratislava", notifyPlace(b))
    }
}
