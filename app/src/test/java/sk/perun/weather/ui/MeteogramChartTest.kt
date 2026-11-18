package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MeteogramChartTest {
    private fun check(lo:Double,step:Double,axis:Pair<Double,Double>) {
        assertEquals(lo,axis.first,1e-9)
        assertEquals(step,axis.second,1e-9)
    }

    @Test fun niceStepIsOneTwoOrFive() {
        assertEquals(1.0,meteoNiceStep(0.85),1e-9)
        assertEquals(2.0,meteoNiceStep(2.0),1e-9)
        assertEquals(10.0,meteoNiceStep(9.25),1e-9)
        assertEquals(0.05,meteoNiceStep(0.03),1e-9)
        assertEquals(1.0,meteoNiceStep(0.0),1e-9)
    }

    @Test fun temperatureAxisUsesWholeSteps() {
        check(3.0,3.0,meteoAxis(3.2,11.7,false))
        check(-5.0,1.0,meteoAxis(-4.6,-1.2,false))
        check(7.0,1.0,meteoAxis(7.0,7.0,false))
    }

    @Test fun zeroBasedAxisReachesTheLeastValue() {
        check(0.0,1.0,meteoAxis(0.0,3.4,true,4,2.0))
        check(0.0,0.5,meteoAxis(0.0,0.0,true,4,2.0))
        check(0.0,10.0,meteoAxis(0.0,37.0,true,4,20.0))
    }
}
