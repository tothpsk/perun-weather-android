package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AirWidgetTest {
    @Test fun shapeFollowsTheSize() {
        assertEquals(AirWidgetShape.Tiny,airWidgetShape(70,70))
        assertEquals(AirWidgetShape.Tiny,airWidgetShape(160,60))
        assertEquals(AirWidgetShape.Square,airWidgetShape(160,160))
        assertEquals(AirWidgetShape.Strip,airWidgetShape(330,60))
        assertEquals(AirWidgetShape.Full,airWidgetShape(330,130))
    }
}
