package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StormWidgetTest {
    @Test fun shapeFollowsTheSize() {
        assertEquals(StormShape(1,1),StormShape.of(70,70))
        assertEquals(StormShape(3,1),StormShape.of(215,70))
        assertEquals(StormShape(5,2),StormShape.of(360,170))
    }

    @Test fun ratesTakeTheColoursOfTheNowcast() {
        assertEquals(0,stormRateColor(0.0))
        assertEquals(0xFFA5D6A7.toInt(),stormRateColor(0.2))
        assertEquals(0xFFFFCA28.toInt(),stormRateColor(3.0))
        assertEquals(0xFFEF5350.toInt(),stormRateColor(9.0))
    }
}
