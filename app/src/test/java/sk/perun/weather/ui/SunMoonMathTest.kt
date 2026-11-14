package sk.perun.weather.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SunMoonMathTest : ResourceStringsTestBase() {
    @Test fun matchesLinuxFullMoonReference() {
        val p=SunMoonMath.position(Instant.parse("2026-10-26T12:00:00Z"),48.1482,17.1067)
        assertEquals(26.475636802935302,p.sun,0.000001)
        assertEquals(-21.306592164008727,p.moon,0.000001)
        assertEquals(99.99822757652312,p.illumination,0.000001)
        assertEquals(4,p.phase)
    }
    @Test fun matchesLinuxNewMoonReference() {
        val p=SunMoonMath.position(Instant.parse("2026-03-20T12:00:00Z"),48.1482,17.1067)
        assertEquals(40.0315969545086,p.sun,0.000001)
        assertEquals(51.460937080781285,p.moon,0.000001)
        assertEquals(2.622616301503422,p.illumination,0.000001)
        assertEquals(0,p.phase)
    }
    @Test fun respectsCivilDayLength() {
        val zone=ZoneId.of("Europe/Bratislava")
        assertEquals(23,SunMoonMath.times(LocalDate.of(2026,3,29),zone,1,1).size)
        assertEquals(25,SunMoonMath.times(LocalDate.of(2026,10,25),zone,1,1).size)
    }
}
