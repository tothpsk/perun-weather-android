package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertHazardTest {
    private fun kind(text:String,awareness:String="")=alertHazard(text,awareness)

    @Test fun awarenessTypeWins() {
        assertEquals(AlertHazard.Wind,kind("Výstraha","1; Wind"))
        assertEquals(AlertHazard.RainFlood,kind("Povodeň","13; rain-flood"))
        assertEquals(AlertHazard.Marine,kind("x","14; Marine-Hazard"))
        assertEquals(AlertHazard.Drought,kind("x","15; Drought"))
        assertEquals(AlertHazard.Wind,kind("awareness_type=1, awareness_level=2"))
    }

    @Test fun snowIceSplitsIntoSnowAndIce() {
        assertEquals(AlertHazard.Ice,kind("Výstraha pred poľadovicou","2; snow-ice"))
        assertEquals(AlertHazard.Snow,kind("Yellow Snow-ice Warning","2; snow-ice"))
        assertEquals(AlertHazard.Ice,kind("Freezing rain, icing"))
        assertEquals(AlertHazard.Ice,kind("icy surfaces"))
        assertEquals(AlertHazard.Snow,kind("Road ice and snow"))
    }

    @Test fun wordsOfTheServers() {
        assertEquals(AlertHazard.Wind,kind("High Wind Watch issued November 24"))
        assertEquals(AlertHazard.Marine,kind("Small Craft Advisory"))
        assertEquals(AlertHazard.Marine,kind("gale"))
        assertEquals(AlertHazard.Fog,kind("Dense fog"))
        assertEquals(AlertHazard.Fog,kind("Туман"))
        assertEquals(AlertHazard.Rain,kind("Дождь"))
        assertEquals(AlertHazard.Wind,kind("Ветер"))
        assertEquals(AlertHazard.Snow,kind("Снег"))
        assertEquals(AlertHazard.Coastal,kind("Coastal Flood Warning"))
        assertEquals(AlertHazard.Flood,kind("Flood Warning"))
        assertEquals(AlertHazard.RainFlood,kind("Inundación urbana"))
        assertEquals(AlertHazard.Fire,kind("Bushfire"))
        assertEquals(AlertHazard.Fire,kind("Grass Fire"))
        assertEquals(AlertHazard.Earthquake,kind("Gempabumi"))
        assertEquals(AlertHazard.Earthquake,kind("Earthquake"))
        assertEquals(AlertHazard.Landslide,kind("DESLIZAMENTOS"))
        assertEquals(AlertHazard.Landslide,kind("Fels- und Bergsturz, Erdrutsch"))
        assertEquals(AlertHazard.Storm,kind("TEMPESTADE LOCAL/CONVECTIVA - CHUVAS INTENSAS"))
        assertEquals(AlertHazard.Storm,kind("Hujan Lebat dan Petir"))
        assertEquals(AlertHazard.Dust,kind("Raised dust"))
        assertEquals(AlertHazard.Dust,kind("Poussiere"))
        assertEquals(AlertHazard.Air,kind("Air Quality Alert"))
        assertEquals(AlertHazard.Hail,kind("hail"))
        assertEquals(AlertHazard.Cold,kind("cold wave"))
        assertEquals(AlertHazard.Cold,kind("FROST"))
        assertEquals(AlertHazard.Heat,kind("Extreme Heat"))
        assertEquals(AlertHazard.Avalanche,kind("Лавины"))
        assertEquals(AlertHazard.Ice,kind("Żółty alert na oblodzenie"))
        assertEquals(AlertHazard.Snow,kind("Żółty alert na intensywne opady śniegu"))
        assertEquals(AlertHazard.Storm,kind("Yellow Thunderstorm Warning"))
        assertEquals(AlertHazard.Rain,kind("Yellow Rain Warning"))
        assertEquals(AlertHazard.Wind,kind("Aviso de vientos de nivel amarillo"))
        assertEquals(AlertHazard.Coastal,kind("Aviso de costeros de nivel amarillo"))
        assertEquals(AlertHazard.Coastal,kind("Vigilance jaune vagues-submersion"))
        assertEquals(AlertHazard.Wind,kind("Vigilance jaune vent-violent"))
        assertEquals(AlertHazard.Storm,kind("Silné búrky"))
        assertEquals(AlertHazard.Wind,kind("Vietor"))
        assertEquals(AlertHazard.Heat,kind("Vysoké teploty"))
        assertEquals(AlertHazard.Cold,kind("Nízke teploty"))
        assertEquals(AlertHazard.Generic,kind("Прочие опасности"))
        assertEquals(AlertHazard.Generic,kind("Other dangers"))
    }
}
