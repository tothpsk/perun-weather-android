package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockWidgetTest {
    @Test fun cellsFollowTheWidth() {
        assertEquals(2,ClockSize(150,60).cells)
        assertEquals(3,ClockSize(200,60).cells)
        assertEquals(4,ClockSize(290,60).cells)
        assertEquals(5,ClockSize(340,60).cells)
    }

    @Test fun oneRowBelowAHundredDp() {
        assertTrue(ClockSize(300,80).row)
        assertFalse(ClockSize(300,170).row)
    }

    @Test fun stylesHaveDefaults() {
        assertEquals("light",PerunWidgets.ClockStyles.first())
        assertTrue("long" in PerunWidgets.DateStyles && "none" in PerunWidgets.DateStyles)
    }
}
